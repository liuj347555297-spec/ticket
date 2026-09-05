package cn.servicehub.knowledge;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest @AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KnowledgeWorkspaceControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;

    @Test void manualDraftReviewPublicationHistoryAndPersonalFavoriteAreRealAndScoped() throws Exception {
        String body="""
            {"title":"ERP 查询超时处理","categoryCode":"CLASSIC_CASE","tags":["#ERP"],
             "serviceCatalogItemIds":["SC-browser-performance"],"content":"确认影响范围后清理缓存，并记录发生时间。"}
            """;
        String created=mvc.perform(post("/api/v1/knowledge/documents/drafts").with(requester()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("DRAFT")))
            .andExpect(jsonPath("$.favorite",is(false))).andReturn().getResponse().getContentAsString();
        var draft=json.readTree(created);String id=draft.get("id").asText();
        String editedBody="{\"title\":\"ERP 查询超时处理（修订）\",\"categoryCode\":\"DOCUMENT\",\"tags\":[\"#ERP\",\"#超时\"],\"serviceCatalogItemIds\":[\"SC-browser-performance\"],\"content\":\"修订后的排查正文，保留完整可复用步骤。\"}";
        String edited=mvc.perform(put("/api/v1/knowledge/documents/{id}/draft",id).header("If-Match","\"1\"").with(requester()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(editedBody))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title",is("ERP 查询超时处理（修订）"))).andExpect(jsonPath("$.currentVersionNumber",is(2))).andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/v1/knowledge/documents/{id}/draft",id).header("If-Match","\"1\"").with(requester()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(editedBody))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code",is("KNOWLEDGE_DRAFT_CONFLICT")));
        String version=json.readTree(edited).get("currentVersionId").asText();

        mvc.perform(get("/api/v1/knowledge/documents/workbench?section=MY_DRAFTS").with(requester()))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].id",is(id)));
        mvc.perform(get("/api/v1/knowledge/documents/{id}/content",id).with(requester()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content",is("修订后的排查正文，保留完整可复用步骤。")));
        mvc.perform(get("/api/v1/knowledge/documents/workbench?section=MY_DRAFTS").with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));

        mvc.perform(post("/api/v1/knowledge/documents/{id}/submit",id).with(requester()).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PENDING_REVIEW")));
        mvc.perform(get("/api/v1/knowledge/documents/workbench?section=PENDING_REVIEW").with(manager()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id",is(id)));
        mvc.perform(post("/api/v1/knowledge/documents/{id}/publish",id).param("versionId",version).with(manager()).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PUBLISHED")));

        mvc.perform(put("/api/v1/knowledge/documents/{id}/favorite",id).with(requester()).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.favorite",is(true)));
        mvc.perform(get("/api/v1/knowledge/documents/favorites").with(requester()))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].favorite",is(true)));
        mvc.perform(get("/api/v1/knowledge/documents/{id}/versions",id).with(requester()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].versionNumber",is(2))).andExpect(jsonPath("$[0].status",is("PUBLISHED")))
            .andExpect(jsonPath("$[1].versionNumber",is(1))).andExpect(jsonPath("$[1].status",is("SUPERSEDED")));
        mvc.perform(delete("/api/v1/knowledge/documents/{id}/favorite",id).with(requester()).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.favorite",is(false)));
    }

    @Test void onlyDraftOwnerCanDeleteDraft() throws Exception {
        String body="{\"title\":\"待完善知识\",\"categoryCode\":\"DOCUMENT\",\"tags\":[],\"serviceCatalogItemIds\":[\"SC-browser-performance\"],\"content\":\"这是尚未提交审核的完整草稿内容。\"}";
        String created=mvc.perform(post("/api/v1/knowledge/documents/drafts").with(requester()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(created).get("id").asText();
        mvc.perform(delete("/api/v1/knowledge/documents/{id}/draft",id).with(user("iam-u-1002").roles("REQUESTER")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/knowledge/documents/{id}/draft",id).with(requester()).with(csrf())).andExpect(status().isNoContent());
    }

    private RequestPostProcessor requester(){return user("iam-u-1001").roles("REQUESTER");}
    private RequestPostProcessor manager(){return user("iam-u-1001").authorities(new SimpleGrantedAuthority("ROLE_SERVICE_MANAGER"),new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"));}
}
