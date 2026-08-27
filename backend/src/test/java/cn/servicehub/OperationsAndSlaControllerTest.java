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

@SpringBootTest
@AutoConfigureMockMvc
class OperationsAndSlaControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void reportUsesServerSideScopeAndRejectsRequesterRole() throws Exception {
        mvc.perform(get("/api/v1/reports/operations/kpis").param("from", "2026-08-01T00:00:00Z").param("to", "2026-08-02T00:00:00Z")
                .with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/operations/kpis").param("from", "2026-08-01T00:00:00Z").param("to", "2026-08-02T00:00:00Z")
                .with(user("iam-u-1001").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scope.dataScopeFiltered").value(true));
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
}
