package cn.servicehub;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ServiceCatalogAdminControllerTest {
    @Autowired private MockMvc mvc;
    private static final String KEY = "4f69c260-f277-4f72-8723-17fb7f9aa021";
    private static final String DRAFT = """
        {"version":0,"code":"BROWSER_HELP","name":"浏览器支持","summary":"规范化浏览器故障建单","ticketType":"INCIDENT","categoryCode":"BROWSER","applicableOrganizationIds":["org-it"],"reason":"创建标准浏览器表单","tagPolicy":{"allowStandardTags":true,"allowFreeTags":true,"maxTags":5,"allowedStandardTagCodes":[]},"fields":[{"code":"affected_page","label":"受影响页面","type":"TEXT","required":true,"defaultValue":"首页","helpText":"填写页面地址或名称","maxLength":500,"displayOrder":10},{"code":"description","label":"问题现象","type":"RICH_TEXT","required":true,"displayOrder":20},{"code":"browser","label":"浏览器","type":"SINGLE_SELECT","required":false,"dictionaryCode":"BROWSER","displayOrder":30,"visibleWhen":[{"fieldCode":"affected_page","operator":"HAS_VALUE","values":[]}]}]}
        """;

    @Test void managementRequiresBackofficeRoleAndRejectsSystemField() throws Exception {
        mvc.perform(post("/api/v1/admin/service-catalog/items").with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/service-catalog/items").with(user("iam-u-admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(DRAFT.replace("affected_page","status")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code",is("FORM_CONFIGURATION_INVALID")));
    }

    @Test void createsDraftAndRequiresDifferentApproverForPublication() throws Exception {
        var created = mvc.perform(post("/api/v1/admin/service-catalog/items").with(user("iam-u-admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id",is("SC-browser-help"))).andExpect(jsonPath("$.lifecycleStatus",is("DRAFT"))).andReturn();
        mvc.perform(post("/api/v1/admin/service-catalog/items/SC-browser-help/publish-requests").with(user("iam-u-admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key", "2d6e2b64-66a9-44e5-93b8-8b9ccad8158a").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"提交双人复核发布\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status",is("PENDING_REVIEW")));
    }

    @Test void publishedConfigurationDrivesRequesterFormAndLocksTicketToItsActualVersion() throws Exception {
        mvc.perform(post("/api/v1/admin/service-catalog/items").with(user("iam-u-admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
            .andExpect(status().isCreated());
        String publish = mvc.perform(post("/api/v1/admin/service-catalog/items/SC-browser-help/publish-requests").with(user("iam-u-admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key", "5a55e380-2a40-42c2-9381-b85ce72cd402").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"提交双人复核发布\"}"))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(publish, "$.requestId");
        mvc.perform(post("/api/v1/admin/service-catalog/items/SC-browser-help/publish-requests/" + requestId + "/approve").with(user("iam-u-reviewer").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key", "a0e2f8b9-f154-4489-a5be-0150d0230a24"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleStatus", is("PUBLISHED"))).andExpect(jsonPath("$.formVersion", is(2)));
        mvc.perform(get("/api/v1/service-catalog/items/SC-browser-help/form").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.formVersion",is(2))).andExpect(jsonPath("$.fields[0].defaultValue", is("首页"))).andExpect(jsonPath("$.fields[0].helpText", is("填写页面地址或名称")))
            .andExpect(jsonPath("$.fields[1].type",is("RICH_TEXT"))).andExpect(jsonPath("$.fields[2].visibleWhen[0].operator", is("HAS_VALUE")));
        mvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("Idempotency-Key", "544f9b12-9bd1-4a31-9668-10b439b30fa4").contentType(MediaType.APPLICATION_JSON).content("""
            {"serviceCatalogItemId":"SC-browser-help","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"页面卡顿","description":"已经按要求描述问题","structuredFields":{"affected_page":"首页","browser":"Chrome"},"tags":[]}
            """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code",is("SERVICE_CATALOG_INVALID")));
        mvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("Idempotency-Key", "33b1da5c-bc75-4302-8f2f-d49a9802261d").contentType(MediaType.APPLICATION_JSON).content("""
            {"serviceCatalogItemId":"SC-browser-help","serviceCatalogFormVersion":2,"type":"INCIDENT","title":"页面卡顿","description":"已经按要求描述问题","structuredFields":{"affected_page":"首页","browser":"Chrome"},"tags":[]}
            """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.serviceCatalogFormVersion",is(2)));
    }
}
