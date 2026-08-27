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
class AnnouncementControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void managerCanCreateAndAudienceIsCalculatedFromIamProjection() throws Exception {
        mvc.perform(post("/api/v1/announcements").with(csrf()).with(user("iam-u-1001").roles("SERVICE_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"title":"服务窗口调整","body":"本周末进行例行维护。","audienceScope":"ORGANIZATION","targetOrganizationIamId":"org-it","pinned":true,"effectiveUntil":"2026-12-31T00:00:00Z"}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(true));
        mvc.perform(get("/api/v1/announcements").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("服务窗口调整"));
        mvc.perform(get("/api/v1/announcements").with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void requesterCannotCreateAnnouncement() throws Exception {
        mvc.perform(post("/api/v1/announcements").with(csrf()).with(user("iam-u-1002").roles("REQUESTER"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"title":"无权限","body":"不应发布。","audienceScope":"ALL","pinned":false,"effectiveUntil":"2026-12-31T00:00:00Z"}
                    """))
            .andExpect(status().isForbidden());
    }
}
