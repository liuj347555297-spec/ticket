package cn.servicehub;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackofficeAccessControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void platformAdministratorCanBindAnActiveIamUserToBackofficeRolesAndScopes() throws Exception {
        mvc.perform(get("/api/v1/admin/backoffice-access/iam-u-1001").with(user("iam-u-local-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.iamUserId", is("iam-u-1001")))
            .andExpect(jsonPath("$.access.enabled", is(false))).andExpect(jsonPath("$.access.version", is(0)));
        mvc.perform(put("/api/v1/admin/backoffice-access/iam-u-1001").with(csrf()).with(user("iam-u-local-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"enabled":true,"roleCodes":["ROLE_FIRST_LINE_SUPPORT"],"dataScopes":[{"scopeType":"QUEUE","scopeId":"QUEUE-DESK-01"}],"expectedVersion":0}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access.enabled", is(true)))
            .andExpect(jsonPath("$.access.roleCodes", contains("ROLE_FIRST_LINE_SUPPORT")))
            .andExpect(jsonPath("$.access.dataScopes[0].scopeType", is("QUEUE")))
            .andExpect(jsonPath("$.access.version", is(1)));
    }

    @Test
    void ordinaryUserCannotManageBackofficeAccessAndAdminCannotChangeOwnGrant() throws Exception {
        mvc.perform(get("/api/v1/admin/backoffice-access/iam-u-1001").with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/backoffice-access/iam-u-local-admin").with(csrf()).with(user("iam-u-local-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{" + "\"enabled\":true,\"roleCodes\":[],\"dataScopes\":[],\"expectedVersion\":1}"))
            .andExpect(status().isForbidden());
    }
}
