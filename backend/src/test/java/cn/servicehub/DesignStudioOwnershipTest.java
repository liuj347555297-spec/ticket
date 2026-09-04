package cn.servicehub;

import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.catalog.config.FormConfigurationRepository;
import cn.servicehub.catalog.domain.*;
import cn.servicehub.designer.*;
import cn.servicehub.designer.StudioModels.*;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.servicesystem.domain.*;
import cn.servicehub.servicesystem.infrastructure.InMemoryServiceSystemRepository;
import cn.servicehub.ticket.domain.TicketType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesignStudioOwnershipTest {
    private static final String ORG = "org-it";
    private static final String SERVICE_A = "SC-service-a";
    private static final String SERVICE_B = "SC-service-b";
    private final Instant now = Instant.now();
    private CurrentUserProvider users;
    private ServiceCatalogRepository catalogs;
    private FormConfigurationRepository configurations;
    private InMemoryServiceSystemRepository systems;
    private InMemoryStudioDraftRepository repository;
    private StudioDraftService service;

    @BeforeEach void setup() {
        users = mock(CurrentUserProvider.class);
        actor("ROLE_SERVICE_MANAGER", "DATA_SCOPE_ORGANIZATION:" + ORG);
        catalogs = mock(ServiceCatalogRepository.class);
        configurations = mock(FormConfigurationRepository.class);
        systems = new InMemoryServiceSystemRepository();
        repository = new InMemoryStudioDraftRepository();
        service = service(repository);
        system("ERP", ORG);
        system("HRMS", ORG);
        system("FOREIGN", "org-other");
        for (String id : List.of(SERVICE_A, SERVICE_B)) {
            when(catalogs.findById(id)).thenReturn(Optional.of(new ServiceCatalogItem(id, id, "service", CatalogPublicationStatus.PUBLISHED,
                Set.of(TicketType.INCIDENT), List.of())));
        }
        mapping("ERP", null, SERVICE_A, true);
        mapping("ERP", null, SERVICE_B, true);
        mapping("HRMS", null, SERVICE_A, true);
    }

    @Test void draftSystemCanOwnDesignAndSummaryPreservesServiceContext() {
        Input input = input(0, "ERP", SERVICE_A);
        Draft draft = service.create(input, "create-owned-design");
        assertEquals("ERP", draft.systemCode());
        assertEquals(SERVICE_A, draft.serviceCatalogItemId());
        assertEquals("DRAFT_ONLY", draft.executionMode());
        assertEquals(input, draft.input());
        assertEquals(draft, service.create(input, "create-owned-design"));
        assertEquals("ERP", service.list().get(0).systemCode());
        assertEquals(SERVICE_A, service.list().get(0).serviceCatalogItemId());
        assertThrows(StudioConflictException.class, () -> service.create(input(0, "HRMS", SERVICE_A), "create-owned-design"));
    }

    @Test void validatesRealSystemOrganizationAndActorScopeWithoutAdministratorBypass() {
        assertThrows(IllegalArgumentException.class, () -> service.create(input(0, "MISSING", null), "missing-system"));
        assertThrows(AccessDeniedException.class, () -> service.create(input(0, "FOREIGN", null), "foreign-system"));
        actor("ROLE_PLATFORM_ADMIN");
        assertThrows(AccessDeniedException.class, () -> service.create(input(0, "FOREIGN", null), "admin-org-mismatch"));
        actor("ROLE_SERVICE_MANAGER", "DATA_SCOPE_ORGANIZATION:org-other");
        assertThrows(AccessDeniedException.class, () -> service.create(input(0, "ERP", null), "out-of-scope"));
        actor("ROLE_AUDITOR", "DATA_SCOPE_ORGANIZATION:" + ORG);
        assertThrows(AccessDeniedException.class, () -> service.create(input(0, "ERP", null), "auditor-write"));
    }

    @Test void rejectsCatalogWithoutSystemBadCodesNonexistentAndUnmappedCatalogs() {
        assertThrows(IllegalArgumentException.class, () -> service.create(input(0, null, SERVICE_A), "missing-parent"));
        assertThrows(IllegalArgumentException.class, () -> service.create(input(0, "erp", SERVICE_A), "bad-system-code"));
        assertThrows(IllegalArgumentException.class, () -> service.create(input(0, "ERP", ""), "blank-service"));
        mapping("ERP", null, "SC-ghost-service", true);
        assertThrows(IllegalArgumentException.class, () -> service.create(input(0, "ERP", "SC-ghost-service"), "nonexistent-service"));
        assertThrows(IllegalArgumentException.class, () -> service.create(input(0, "HRMS", SERVICE_B), "unmapped-service"));
    }

    @Test void activeModuleMappingIsAcceptedAcrossModulesButDisabledModulesAndMappingsAreNot() {
        module("HRMS", "PAYROLL", true);
        mapping("HRMS", "PAYROLL", SERVICE_B, true);
        assertEquals(SERVICE_B, service.create(input(0, "HRMS", SERVICE_B), "module-binding").serviceCatalogItemId());
        ServiceSystemModule current = systems.findModule("HRMS", "PAYROLL").orElseThrow();
        systems.saveModule(new ServiceSystemModule("HRMS", "PAYROLL", "PAYROLL", null, false, 1, current.version(), "manager", now), current.version());
        assertThrows(IllegalArgumentException.class, () -> service.create(input(0, "HRMS", SERVICE_B), "disabled-module"));
        systems.saveModule(new ServiceSystemModule("HRMS", "PAYROLL", "PAYROLL", null, true, 1, current.version() + 1, "manager", now), current.version() + 1);
        disableMapping("HRMS", "PAYROLL", SERVICE_B);
        assertThrows(IllegalArgumentException.class, () -> service.create(input(0, "HRMS", SERVICE_B), "disabled-mapping"));
    }

    @Test void oldUnownedDesignCanBeAssignedOnceWithOptimisticLock() {
        Draft legacy = service.create(input(0, null, null), "legacy-unowned");
        Draft assigned = service.update(legacy.id(), input(0, "ERP", SERVICE_A), 0);
        assertEquals(1, assigned.version());
        assertEquals("ERP", assigned.systemCode());
        assertThrows(StudioConflictException.class, () -> service.update(legacy.id(), input(0, "ERP", SERVICE_A), 0));
        for (Input forbidden : List.of(input(1, null, null), input(1, "HRMS", SERVICE_A), input(1, "ERP", null), input(1, "ERP", SERVICE_B))) {
            assertThrows(IllegalArgumentException.class, () -> service.update(assigned.id(), forbidden, 1));
        }
        assertEquals(assigned, service.get(assigned.id()));
    }

    @Test void systemOnlyDesignCanAddServiceLaterButCannotLoseItsSystem() {
        Draft systemOnly = service.create(input(0, "ERP", null), "system-only");
        assertThrows(IllegalArgumentException.class, () -> service.update(systemOnly.id(), input(0, null, null), 0));
        Draft withService = service.update(systemOnly.id(), input(0, "ERP", SERVICE_A), 0);
        assertEquals(SERVICE_A, withService.serviceCatalogItemId());
        assertEquals(2, service.update(withService.id(), withService.input(), 1).version());
    }

    @Test void saveRevalidatesMappingAndCannotSmuggleOwnershipThroughFrozenRevisionChange() {
        var field = new Field("f-one", "description_text", "说明", "text", 1, false, false, "", List.of(), null);
        var frozen = new Form("form-one", "FORM_ONE", "申请表", 1, "FROZEN", List.of(field));
        Draft original = service.create(new Input(0, "设计包", ORG, DesignStudioTest.XML, List.of(frozen), List.of(), "创建冻结表单设计", null, null), "frozen-adoption");
        assertThrows(IllegalArgumentException.class, () -> service.update(original.id(), input(0, "ERP", SERVICE_A), 0));
        assertNull(service.get(original.id()).systemCode());
        Draft assigned = service.update(original.id(), new Input(0, original.name(), ORG, original.bpmnXml(), original.forms(), original.nodeBindings(), original.reason(), "ERP", SERVICE_A), 0);
        disableMapping("ERP", null, SERVICE_A);
        assertThrows(IllegalArgumentException.class, () -> service.update(assigned.id(), assigned.input(), 1));
        assertEquals(assigned, service.get(assigned.id()));
    }

    @Test void oldJsonWithoutOwnershipLoadsAndJdbcRoundtripPersistsNewOwnershipWithoutSchemaChange() throws Exception {
        var json = JsonMapper.builder().findAndAddModules().build();
        Input oldInput = new Input(0, "旧设计包", ORG, DesignStudioTest.XML, List.of(), List.of(), "历史数据兼容验证");
        ObjectNode oldInputJson = json.valueToTree(oldInput);
        oldInputJson.remove(List.of("systemCode", "serviceCatalogItemId"));
        assertEquals(oldInput, json.treeToValue(oldInputJson, Input.class));
        Draft legacy = new Draft("DS-" + UUID.randomUUID(), 0, oldInput.name(), ORG, oldInput.bpmnXml(), oldInput.forms(), oldInput.nodeBindings(), oldInput.reason(), "DRAFT_ONLY", now);
        ObjectNode oldDraftJson = json.valueToTree(legacy);
        oldDraftJson.remove(List.of("systemCode", "serviceCatalogItemId"));
        assertEquals(legacy, json.treeToValue(oldDraftJson, Draft.class));
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:ownership_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE design_studio_draft(id VARCHAR(64) PRIMARY KEY,organization_id VARCHAR(128),name VARCHAR(120),version BIGINT,execution_mode VARCHAR(20),payload_json CLOB,updated_at TIMESTAMP)");
        jdbc.update("INSERT INTO design_studio_draft VALUES (?,?,?,?,?,?,?)", legacy.id(), ORG, legacy.name(), 0, "DRAFT_ONLY", json.writeValueAsString(oldDraftJson), java.sql.Timestamp.from(now));
        var persistent = new MySqlStudioDraftRepository(jdbc, json);
        assertEquals(legacy, persistent.find(legacy.id()).orElseThrow());
        Draft assigned = service(persistent).update(legacy.id(), input(0, "ERP", SERVICE_A), 0);
        assertEquals(assigned, persistent.find(legacy.id()).orElseThrow());
        String stored = jdbc.queryForObject("SELECT payload_json FROM design_studio_draft WHERE id=?", String.class, legacy.id());
        assertEquals("ERP", json.readTree(stored).get("systemCode").asText());
        assertEquals(SERVICE_A, json.readTree(stored).get("serviceCatalogItemId").asText());
        assertThrows(StudioConflictException.class, () -> persistent.update(assigned, 0));
    }

    private StudioDraftService service(StudioDraftRepository repo) {
        return new StudioDraftService(repo, users, mock(AuditEventPublisher.class), systems, catalogs, configurations);
    }
    private Input input(long version, String system, String catalog) {
        return new Input(version, "系统流程设计", ORG, DesignStudioTest.XML, List.of(), List.of(), "保存系统服务设计", system, catalog);
    }
    private void actor(String... roles) { when(users.requireCurrentUser()).thenReturn(new CurrentUser("manager", Set.of(roles), "test")); }
    private void system(String code, String org) {
        systems.saveSystem(new ServiceSystem(code, code, null, null, org, ServiceSystemStatus.DRAFT, 0, "test fixture", "manager", "manager", now, now, null), 0);
    }
    private void module(String system, String code, boolean active) {
        systems.saveModule(new ServiceSystemModule(system, code, code, null, active, 1, 0, "manager", now), 0);
    }
    private void mapping(String system, String module, String catalog, boolean active) {
        systems.saveCatalogMapping(new ServiceSystemCatalogMapping(system, module, catalog, active, false, 0, "manager", now), 0);
    }
    private void disableMapping(String system, String module, String catalog) {
        var previous = (module == null ? systems.findSystemCatalogMappings(system) : systems.findModuleCatalogMappings(system, module)).stream()
            .filter(mapping -> catalog.equals(mapping.serviceCatalogItemId())).findFirst().orElseThrow();
        systems.saveCatalogMapping(new ServiceSystemCatalogMapping(system, module, catalog, false, false, previous.version(), "manager", now), previous.version());
    }
}
