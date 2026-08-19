package cn.servicehub;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiIsDeniedWithoutAnAuthenticatedIdentityAndReturnsRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    void protectedApiUsesAuthenticatedCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user", is("iam-u-1001")));
    }

    @Test
    void actuatorIsNotPublicAndRequiresDedicatedRole() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/health").with(user("observer").roles("ACTUATOR_VIEW")))
            .andExpect(status().isOk());
    }

    @Test
    void corsDoesNotReflectUnknownOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void csrfTokenIsRequiredForStateChangingRequests() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/system/ping")
                .with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isForbidden());

        // Ensures the configured filter accepts a valid CSRF token before endpoint method selection.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/system/ping")
                .with(user("iam-u-1001").roles("REQUESTER")).with(csrf()))
            .andExpect(status().isMethodNotAllowed());
    }
}
