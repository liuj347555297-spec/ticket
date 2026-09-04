package cn.servicehub;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ServiceSystemControllerTest {
    private static final String ADMIN="iam-u-local-admin";
    @Autowired MockMvc mvc;

    @Test void requesterOnlySeesPublishedInOrganizationAndModuleMappingWins() throws Exception {
        createDraft();
        mvc.perform(put("/api/v1/admin/service-systems/ERP/modules/ORDER").with(csrf()).with(user(ADMIN).roles("PLATFORM_ADMIN"))
                .header("If-Match","0").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"订单中心\",\"path\":\"/orders\",\"active\":true,\"sortOrder\":10}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version",is(1)));
        mvc.perform(put("/api/v1/admin/service-systems/ERP/catalog-mappings/SC-browser-performance").with(csrf()).with(user(ADMIN).roles("PLATFORM_ADMIN"))
                .header("If-Match","0").contentType(MediaType.APPLICATION_JSON).content("{\"active\":true,\"defaultMapping\":true}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/service-systems/ERP/modules/ORDER/catalog-mappings/SC-browser-performance").with(csrf()).with(user(ADMIN).roles("PLATFORM_ADMIN"))
                .header("If-Match","0").contentType(MediaType.APPLICATION_JSON).content("{\"active\":true,\"defaultMapping\":true}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/service-systems/ERP/publish").with(csrf()).with(user(ADMIN).roles("PLATFORM_ADMIN"))
                .header("If-Match","1").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"发布 ERP 工单路由\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PUBLISHED")));
        mvc.perform(get("/api/v1/service-systems").with(user("iam-u-local-requester").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].code",is("ERP")));
        mvc.perform(get("/api/v1/service-systems/ERP/modules").with(user("iam-u-local-requester").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].code",is("ORDER")));
        mvc.perform(get("/api/v1/service-systems/ERP/catalog-mappings").param("moduleCode","ORDER").with(user("iam-u-local-requester").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].moduleCode",is("ORDER")));
    }

    @Test void requesterCannotManageAndRetiredSystemIsNotSelectable() throws Exception {
        mvc.perform(post("/api/v1/admin/service-systems").with(csrf()).with(user("iam-u-1001").roles("REQUESTER")).header("Idempotency-Key","f69cf260-f277-4f72-8723-17fb7f9aa021").contentType(MediaType.APPLICATION_JSON).content(draft()))
            .andExpect(status().isForbidden());
        createDraft();
        mvc.perform(put("/api/v1/admin/service-systems/ERP/catalog-mappings/SC-browser-performance").with(csrf()).with(user(ADMIN).roles("PLATFORM_ADMIN")).header("If-Match","0").contentType(MediaType.APPLICATION_JSON).content("{\"active\":true,\"defaultMapping\":true}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/service-systems/ERP/publish").with(csrf()).with(user(ADMIN).roles("PLATFORM_ADMIN")).header("If-Match","1").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"发布 ERP 工单路由\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/service-systems/ERP/retire").with(csrf()).with(user(ADMIN).roles("PLATFORM_ADMIN")).header("If-Match","2").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"ERP 系统已下线\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/service-systems").with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));
    }

    private void createDraft() throws Exception { mvc.perform(post("/api/v1/admin/service-systems").with(csrf()).with(user(ADMIN).roles("PLATFORM_ADMIN")).header("Idempotency-Key","4f69c260-f277-4f72-8723-17fb7f9aa021").contentType(MediaType.APPLICATION_JSON).content(draft())).andExpect(status().isCreated()).andExpect(jsonPath("$.status",is("DRAFT"))).andExpect(jsonPath("$.version",is(1))); }
    private String draft(){return "{\"code\":\"ERP\",\"name\":\"ERP\",\"owningOrganizationId\":\"ORG-LOCAL-IT\",\"version\":0,\"reason\":\"初始化 ERP 服务系统\"}";}
}
