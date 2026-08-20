package cn.servicehub;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class NotificationControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void exposesOnlyTheAuthenticatedUsersNotificationsAndMarksOwnedRecordsRead() throws Exception {
        mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "f4d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content(request()))
            .andExpect(status().isCreated());
        String body = mockMvc.perform(get("/api/v1/notifications").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].category", is("TICKET"))).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("items").get(0).get("id").asText();
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount", is(1)));
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", id).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("Idempotency-Key", "a4d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(jsonPath("$.items[0].readState", is("READ")));
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount", is(0)));
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", id).with(user("iam-u-1002").roles("REQUESTER")).with(csrf()).header("Idempotency-Key", "b4d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
            .andExpect(status().isForbidden());
    }
    private static String request() { return """
        {"serviceCatalogItemId":"SC-browser-performance","type":"INCIDENT","title":"核协 E+ 页面卡顿","description":"打开工作台后响应缓慢","structuredFields":{"browser":"Chrome"},"tags":[{"name":"#核协E+","kind":"FREE"}]}
        """; }
}
