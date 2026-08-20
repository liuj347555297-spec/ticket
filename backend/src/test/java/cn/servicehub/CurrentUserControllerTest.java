package cn.servicehub;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CurrentUserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOnlyTheAuthenticatedUsersReadOnlyProjectionAndServerDerivedAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(user("iam-u-1001")
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_REQUESTER"),
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.iamUserId", is("iam-u-1001")))
            .andExpect(jsonPath("$.user.displayName", is("王小明")))
            .andExpect(jsonPath("$.user.organization.iamOrganizationId", is("org-it")))
            .andExpect(jsonPath("$.authorization.roles", contains("REQUESTER")))
            .andExpect(jsonPath("$.authorization.dataScopes[0].scopeType", is("ORGANIZATION")))
            .andExpect(jsonPath("$.authorization.dataScopes[0].scopeId", is("org-it")))
            .andExpect(jsonPath("$.user.workEmail").doesNotExist())
            .andExpect(jsonPath("$.user.workMobile").doesNotExist());
    }

    @Test
    void rejectsAuthenticatedSubjectsWithoutAnActivePlatformProjection() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(user("iam-unknown").roles("REQUESTER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }
}
