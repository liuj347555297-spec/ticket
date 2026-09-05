package cn.servicehub;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@SpringBootTest @AutoConfigureMockMvc
class TicketDraftControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    String body(long version,String title)throws Exception{return json.writeValueAsString(Map.of("version",version,"payload",Map.of("formVersion",1,"form",Map.of("systemCode","ERP","moduleCode","","catalogId","SC-ERP-PERFORMANCE","type","INCIDENT","title",title,"descriptionHtml","<p>草稿正文</p><script>alert(1)</script>","descriptionText","草稿正文","tags",java.util.List.of()),"fieldValues",Map.of("affected_system","ERP"))));}
    @Test void draftIsPrivateReconcilesLostResponseAndUsesVersions()throws Exception{
        String id="TD-"+java.util.UUID.randomUUID(),path="/api/v1/ticket-drafts/"+id;
        mvc.perform(put(path).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("If-Match","0").contentType("application/json").content(body(0,"暂存测试"))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.payload.form.descriptionHtml").value("<p>草稿正文</p>"));
        mvc.perform(put(path).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("If-Match","0").contentType("application/json").content(body(0,"暂存测试"))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(path).with(user("iam-u-1002").roles("PLATFORM_ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(delete(path).with(user("iam-u-1002").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match","1")).andExpect(status().isNotFound());
        mvc.perform(put(path).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("If-Match","0").contentType("application/json").content(body(0,"修改过的草稿"))).andExpect(status().isConflict());
        mvc.perform(delete(path).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("If-Match","1")).andExpect(status().isNoContent());
        mvc.perform(get(path).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isNotFound());
    }
    @Test void rejectsMissingCsrfAndOversizedContent()throws Exception{
        String path="/api/v1/ticket-drafts/TD-"+java.util.UUID.randomUUID();
        mvc.perform(put(path).with(user("iam-u-1001").roles("REQUESTER")).header("If-Match","0").contentType("application/json").content(body(0,"test"))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("If-Match","0").contentType("application/json").content(body(0,"a".repeat(201)))).andExpect(status().isBadRequest());
    }
}
