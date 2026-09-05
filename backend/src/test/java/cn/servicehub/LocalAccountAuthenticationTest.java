package cn.servicehub;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={
    "servicehub.local-auth.enabled=true",
    "servicehub.local-auth.bootstrap-login-name=bootstrap.admin",
    "servicehub.local-auth.bootstrap-password=Bootstrap-Only!2026",
    "servicehub.local-auth.bootstrap-display-name=Bootstrap Admin",
    "servicehub.local-auth.bootstrap-organization-id=org-it",
    "servicehub.local-auth.bootstrap-organization-name=IT Organization"
})
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LocalAccountAuthenticationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;

    @Test
    void passwordLoginCreatesTrustedSessionAndAdminCanCreateAndListAccounts() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("loginName","missing.user","password","Bootstrap-Only!2026"))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code",is("AUTHENTICATION_FAILED")));

        MockHttpSession preAuthentication=new MockHttpSession();String oldId=preAuthentication.getId();
        var login=mvc.perform(post("/api/v1/auth/login").session(preAuthentication).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("loginName","BOOTSTRAP.ADMIN","password","Bootstrap-Only!2026"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.loginName",is("bootstrap.admin")))
            .andExpect(jsonPath("$.authorization.roles",hasItem("PLATFORM_ADMIN"))).andReturn();
        MockHttpSession authenticated=(MockHttpSession)login.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotEquals(oldId,authenticated.getId());

        mvc.perform(post("/api/v1/admin/local-accounts").session(authenticated).with(csrf())
            .header("Idempotency-Key","create-user-0001").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("loginName","ordinary.user","displayName","Ordinary User","organizationId","org-it","password","Ordinary-Only!2026","roles",java.util.List.of("REQUESTER"),"systemCodes",java.util.List.of(),"reason","create requester"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.loginName",is("ordinary.user")))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/admin/local-accounts?page=1&pageSize=20").session(authenticated))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page",is(1))).andExpect(jsonPath("$.total",is(2)));
        var requesterLogin=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("loginName","ordinary.user","password","Ordinary-Only!2026"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authorization.roles",hasItem("REQUESTER"))).andReturn();
        mvc.perform(get("/api/v1/admin/local-accounts?page=1&pageSize=20")
            .session((MockHttpSession)requesterLogin.getRequest().getSession(false))).andExpect(status().isForbidden());
    }

    @Test
    void fiveFailuresLockAccountAndUseTheSamePublicError() throws Exception {
        for(int count=0;count<5;count++)mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("loginName","bootstrap.admin","password","definitely-wrong-password"))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code",is("AUTHENTICATION_FAILED")));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("loginName","bootstrap.admin","password","Bootstrap-Only!2026"))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code",is("AUTHENTICATION_FAILED")));
    }
}
