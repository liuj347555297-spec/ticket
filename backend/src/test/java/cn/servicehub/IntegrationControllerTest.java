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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IntegrationControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void operationsProjectionRequiresOperationsRoleAndNeverExposesConnectionSecrets() throws Exception {
        mvc.perform(get("/api/v1/integrations/operations-overview").with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/integrations/operations-overview").with(user("iam-u-1001").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scopeLabel").exists())
            .andExpect(jsonPath("$.connectionHealths[0].secretRef").doesNotExist());
    }

    @Test
    void signedCallbackEndpointFailsClosedBeforeParsingAndIsNotCsrfProtected() throws Exception {
        mvc.perform(post("/api/v1/integrations/alerts/MONITORING").contentType(MediaType.APPLICATION_JSON)
                .header("X-Integration-Timestamp", "2026-08-21T00:00:00Z").header("X-Integration-Nonce", "1234567890123456")
                .header("X-Integration-Signature", "0".repeat(64)).content("{not-json}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INTEGRATION_REJECTED"));
    }

    @Test
    void configurationItemsFollowServerResolvedOrganizationScope() throws Exception {
        mvc.perform(get("/api/v1/integrations/configuration-items").param("organizationId", "org-it")
                .with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/integrations/configuration-items").param("organizationId", "org-it")
                .with(user("iam-u-1001").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("CI-NET-001"));
    }
}
