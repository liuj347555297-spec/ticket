package cn.servicehub;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.web.servlet.MvcResult;

/** Verifies that a real Flowable 7 process and server-side expected versions drive the lifecycle. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void lifecycleUsesServerStateAndRejectsStaleVersion() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "a1d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceCatalogItemId":"SC-browser-performance","type":"INCIDENT","title":"页面卡顿","description":"工作台缓慢","structuredFields":{"browser":"Chrome"},"tags":[]}
                """))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).required("id").asText();

        action(ticketId, 0, "CLASSIFY", null, "ROLE_FIRST_LINE_SUPPORT").andExpect(jsonPath("$.status", is("PENDING_ASSIGNMENT")));
        action(ticketId, 1, "ASSIGN", "iam-u-1001", "ROLE_SERVICE_MANAGER").andExpect(jsonPath("$.status", is("PENDING_ACCEPTANCE")));
        action(ticketId, 2, "ACCEPT", null, "ROLE_FIRST_LINE_SUPPORT").andExpect(jsonPath("$.status", is("IN_PROGRESS")));
        action(ticketId, 2, "REQUEST_USER_FEEDBACK", null, "ROLE_FIRST_LINE_SUPPORT").andExpect(status().isConflict()).andExpect(jsonPath("$.code", is("WORKFLOW_CONFLICT")));
        action(ticketId, 3, "REQUEST_USER_FEEDBACK", null, "ROLE_FIRST_LINE_SUPPORT").andExpect(jsonPath("$.status", is("PENDING_USER_FEEDBACK")));
    }

    @Test
    void controlledJumpIsOnlyAnApplicationAndCannotChangeStatus() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "b1d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceCatalogItemId":"SC-browser-performance","type":"INCIDENT","title":"跳转测试","description":"工作台缓慢","structuredFields":{"browser":"Chrome"},"tags":[]}
                """))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).required("id").asText();
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")).with(csrf())
                .header("If-Match", "0").contentType(MediaType.APPLICATION_JSON).content("""
                {"action":"CONTROLLED_JUMP_REQUEST","targetNode":"closure","reason":"需走审批的应急纠偏"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("SUBMITTED"))).andExpect(jsonPath("$.version", is(0)));
    }

    private org.springframework.test.web.servlet.ResultActions action(String id, long version, String action, String target, String role) throws Exception {
        String targetJson = target == null ? "" : ",\"targetIamUserId\":\"" + target + "\"";
        return mockMvc.perform(post("/api/v1/tickets/{id}/workflow/actions", id).with(user("iam-u-1001").roles(role.substring("ROLE_".length()))).with(csrf())
            .header("If-Match", Long.toString(version)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"" + action + "\"" + targetJson + "}"));
    }
}
