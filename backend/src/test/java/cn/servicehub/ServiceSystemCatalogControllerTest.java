package cn.servicehub;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.servicehub.catalog.config.*;
import cn.servicehub.servicesystem.domain.*;
import cn.servicehub.ticket.domain.TicketType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ServiceSystemCatalogControllerTest {
    private static final String ORG = "ORG-LOCAL-IT";
    private static final String ACTOR = "iam-u-local-requester";
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ServiceSystemRepository systems;
    @Autowired FormConfigurationRepository forms;

    @Test void completeArrayExcludesUnpublishedForeignUnscopedAndInactiveOfferings() throws Exception {
        system("PORTAL", ORG, ServiceSystemStatus.PUBLISHED);
        for (int i = 0; i < 105; i++) {
            String id = "SC-portal-" + String.format("%03d", i);
            form(id, List.of(ORG), FormConfigurationStatus.PUBLISHED);
            mapping("PORTAL", null, id, true);
        }
        form("SC-portal-foreign", List.of("ORG-OTHER"), FormConfigurationStatus.PUBLISHED);
        form("SC-portal-empty", List.of(), FormConfigurationStatus.PUBLISHED);
        form("SC-portal-draft", List.of(ORG), FormConfigurationStatus.DRAFT);
        form("SC-portal-retired", List.of(ORG), FormConfigurationStatus.RETIRED);
        form("SC-portal-inactive", List.of(ORG), FormConfigurationStatus.PUBLISHED);
        form("SC-portal-unmapped", List.of(ORG), FormConfigurationStatus.PUBLISHED);
        for (String suffix : List.of("foreign", "empty", "draft", "retired", "missing")) mapping("PORTAL", null, "SC-portal-" + suffix, true);
        mapping("PORTAL", null, "SC-portal-inactive", false);
        mvc.perform(get("/api/v1/service-systems/PORTAL/catalog-items").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(105)))
            .andExpect(jsonPath("$[104].id", is("SC-portal-104")))
            .andExpect(jsonPath("$[0].ticketType", is("ACCESS_REQUEST")))
            .andExpect(jsonPath("$[0].publishedVersion", is(3)))
            .andExpect(jsonPath("$[0].formSchemaHash", is("test-schema-hash")));
    }

    @Test void moduleOverridesBeforeVisibilityFilteringAndFallsBackOnlyWithoutActiveMappings() throws Exception {
        system("MODULES", ORG, ServiceSystemStatus.PUBLISHED);
        form("SC-module-default", List.of(ORG), FormConfigurationStatus.PUBLISHED);
        form("SC-module-own", List.of(ORG), FormConfigurationStatus.PUBLISHED);
        form("SC-module-foreign", List.of("ORG-OTHER"), FormConfigurationStatus.PUBLISHED);
        mapping("MODULES", null, "SC-module-default", true);
        for (String module : List.of("OWN", "FALLBACK", "HIDDEN")) module("MODULES", module, true);
        module("MODULES", "DISABLED", false);
        mapping("MODULES", "OWN", "SC-module-own", true);
        mapping("MODULES", "FALLBACK", "SC-module-own", false);
        mapping("MODULES", "HIDDEN", "SC-module-foreign", true);
        mvc.perform(get("/api/v1/service-systems/MODULES/catalog-items").param("moduleCode", "OWN").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id", is("SC-module-own")));
        mvc.perform(get("/api/v1/service-systems/MODULES/catalog-items").param("moduleCode", "FALLBACK").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id", is("SC-module-default")));
        mvc.perform(get("/api/v1/service-systems/MODULES/catalog-items").param("moduleCode", "HIDDEN").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        for (String invalid : List.of("DISABLED", "UNKNOWN")) {
            mvc.perform(get("/api/v1/service-systems/MODULES/catalog-items").param("moduleCode", invalid).with(user(ACTOR).roles("REQUESTER")))
                .andExpect(status().isBadRequest());
        }
    }

    @Test void draftRetiredForeignSystemsAndMissingActiveIamAreDeniedEvenForAdministrator() throws Exception {
        system("DRAFT_PORTAL", ORG, ServiceSystemStatus.DRAFT);
        system("RETIRED_PORTAL", ORG, ServiceSystemStatus.RETIRED);
        system("FOREIGN_PORTAL", "ORG-OTHER", ServiceSystemStatus.PUBLISHED);
        system("IAM_PORTAL", ORG, ServiceSystemStatus.PUBLISHED);
        for (String code : List.of("DRAFT_PORTAL", "RETIRED_PORTAL", "FOREIGN_PORTAL")) {
            mvc.perform(get("/api/v1/service-systems/" + code + "/catalog-items").with(user("iam-u-local-admin").roles("PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/service-systems/IAM_PORTAL/catalog-items").with(user("iam-u-unregistered").roles("REQUESTER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/service-systems/IAM_PORTAL/catalog-items").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test void retiredManagedRecordCannotResurrectSameIdLegacySeed() throws Exception {
        system("SHADOW_PORTAL", ORG, ServiceSystemStatus.PUBLISHED);
        mapping("SHADOW_PORTAL", null, "SC-browser-performance", true);
        mvc.perform(get("/api/v1/service-systems/SHADOW_PORTAL/catalog-items").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        form("SC-browser-performance", List.of(ORG), FormConfigurationStatus.RETIRED);
        mvc.perform(get("/api/v1/service-systems/SHADOW_PORTAL/catalog-items").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    private void system(String code, String org, ServiceSystemStatus state) {
        systems.saveSystem(new ServiceSystem(code, code, null, null, org, state, 0, "test fixture", ACTOR, ACTOR, NOW, NOW,
            state == ServiceSystemStatus.PUBLISHED ? NOW : null), 0);
    }
    private void module(String system, String code, boolean active) {
        systems.saveModule(new ServiceSystemModule(system, code, code, null, active, 1, 0, ACTOR, NOW), 0);
    }
    private void mapping(String system, String module, String id, boolean active) {
        systems.saveCatalogMapping(new ServiceSystemCatalogMapping(system, module, id, active, false, 0, ACTOR, NOW), 0);
    }
    private void form(String id, List<String> orgs, FormConfigurationStatus state) {
        forms.save(new ManagedFormConfiguration(id, id.replace('-', '_').toUpperCase(), id, "独立工单服务", TicketType.ACCESS_REQUEST,
            "ACCESS", orgs, List.of(new ConfiguredFormField("detail", "申请说明", ConfigurableFormFieldType.TEXT, false, null, null, 100,
                null, 1, List.of(), List.of())), new TagPolicy(true, false, 10, List.of()), state, 1, 3, "test-schema-hash", "test fixture",
            ACTOR, ACTOR, NOW, NOW, state == FormConfigurationStatus.PUBLISHED ? NOW : null), 0);
    }
}
