package cn.servicehub;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import cn.servicehub.operations.application.OperationsExportService;
import cn.servicehub.operations.domain.ReportExportTask;
import cn.servicehub.operations.domain.ReportExportTaskRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class OperationsAndSlaControllerTest {
    @Autowired MockMvc mvc;
    @Autowired OperationsExportService exportService;
    @Autowired ReportExportTaskRepository exportTasks;

    @Test
    void reportUsesServerSideScopeAndRejectsRequesterRole() throws Exception {
        mvc.perform(get("/api/v1/reports/operations/kpis").param("from", "2026-08-01T00:00:00Z").param("to", "2026-08-02T00:00:00Z")
                .with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/operations/kpis").param("from", "2026-08-01T00:00:00Z").param("to", "2026-08-02T00:00:00Z")
                .with(user("iam-u-1001").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scope.dataScopeFiltered").value(true));
        mvc.perform(get("/api/v1/reports/operations/kpis").param("from", "2026-08-01T00:00:00Z").param("to", "2026-08-02T00:00:00Z")
                .with(user("iam-u-1001").authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                    new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:*"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void slaPolicyWriteRequiresAdminAndIsAuditedByService() throws Exception {
        String body = """
            {"name":"测试 SLA","priority":"P3","responseTargetMinutes":30,"resolutionTargetMinutes":120,
             "calendarKey":"24X7","pauseStatuses":["ON_HOLD"],"active":true}
            """;
        mvc.perform(post("/api/v1/admin/sla/policies").with(csrf()).with(user("iam-u-1002").roles("REQUESTER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/sla/policies").with(csrf()).with(user("iam-u-1001").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("测试 SLA"));
    }

    @Test
    void serviceManagerCannotCreateGlobalOrOutOfScopeSlaPolicy() throws Exception {
        String global = """
            {"name":"全局规则","priority":"P3","responseTargetMinutes":30,"resolutionTargetMinutes":120,
             "calendarKey":"24X7","pauseStatuses":["ON_HOLD"],"active":true}
            """;
        String scoped = """
            {"name":"分支规则","serviceCatalogItemId":"SC-browser-performance","organizationScopeId":"org-it","priority":"P3",
             "responseTargetMinutes":30,"resolutionTargetMinutes":120,"calendarKey":"24X7","pauseStatuses":["ON_HOLD"],"active":true}
            """;
        var scopedManager = user("iam-u-1001").authorities(
            new SimpleGrantedAuthority("ROLE_SERVICE_MANAGER"),
            new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"),
            new SimpleGrantedAuthority("DATA_SCOPE_SERVICE:SC-browser-performance"));
        mvc.perform(post("/api/v1/admin/sla/policies").with(csrf()).with(scopedManager).contentType(MediaType.APPLICATION_JSON).content(global))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/sla/policies").with(csrf()).with(scopedManager).contentType(MediaType.APPLICATION_JSON).content(scoped))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.organizationScopeId").value("org-it"));
    }

    @Test
    void exportIsAsynchronousAndOwnedByItsRequester() throws Exception {
        mvc.perform(post("/api/v1/reports/operations/exports").with(csrf()).with(user("iam-u-1001").roles("PLATFORM_ADMIN"))
                .param("from", "2026-08-01").param("to", "2026-08-02"))
            .andExpect(status().isForbidden());
        String body = mvc.perform(post("/api/v1/reports/operations/exports").with(csrf()).with(user("iam-u-1001").authorities(
                    new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"), new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it")))
                .param("from", "2026-08-01").param("to", "2026-08-02"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.resultContent").doesNotExist()).andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();
        mvc.perform(get("/api/v1/reports/operations/exports/{id}", id).with(user("iam-u-2002").authorities(
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"), new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/operations/exports/{id}", id).with(user("iam-u-1001").authorities(
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"), new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").exists());
    }

    @Test
    void exportDownloadRejectsScopeRevocationAndLegacyUnrestrictedTasks() throws Exception {
        String body = mvc.perform(post("/api/v1/reports/operations/exports").with(csrf()).with(user("iam-u-1001").authorities(
                    new SimpleGrantedAuthority("ROLE_AUDITOR"), new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it")))
                .param("from", "2026-08-01").param("to", "2026-08-02"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();
        exportService.processPending();

        mvc.perform(get("/api/v1/reports/operations/exports/{id}/content", id).with(user("iam-u-1001").authorities(
                new SimpleGrantedAuthority("ROLE_AUDITOR"), new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-other"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/operations/exports/{id}/content", id).with(user("iam-u-1001").authorities(
                new SimpleGrantedAuthority("ROLE_AUDITOR"), new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"))))
            .andExpect(status().isOk());

        String legacyId = UUID.randomUUID().toString();
        byte[] legacyContent = "legacy".getBytes(StandardCharsets.UTF_8);
        exportTasks.create(new ReportExportTask(legacyId, "iam-u-1001", "DAILY_TICKET_KPI", LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-02"), Set.of(), true, ReportExportTask.Status.COMPLETED, legacyContent, "legacy-sha",
            "legacy.csv", null, Instant.now(), Instant.now(), Instant.now(), 0, 0));
        mvc.perform(get("/api/v1/reports/operations/exports/{id}/content", legacyId).with(user("iam-u-1001").authorities(
                new SimpleGrantedAuthority("ROLE_AUDITOR"), new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"))))
            .andExpect(status().isForbidden());
    }
}
