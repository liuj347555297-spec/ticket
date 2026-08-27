package cn.servicehub;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TicketControllerTest {
    private static final String CREATE_REQUEST = """
        {
          "serviceCatalogItemId":"SC-browser-performance",
          "serviceCatalogFormVersion":1,
          "type":"INCIDENT",
          "title":"核协 E+ 页面卡顿",
          "description":"打开工作台后响应缓慢",
          "structuredFields":{"browser":"Chrome","error_code":"E-101"},
          "tags":[{"name":"#核协E+","kind":"FREE"}]
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUsesAuthenticatedIdentityAndServerRules() throws Exception {
        mockMvc.perform(create("a4d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/api/v1/tickets/TKT-")))
            .andExpect(jsonPath("$.status", is("SUBMITTED")))
            .andExpect(jsonPath("$.priority", is("P3")))
            .andExpect(jsonPath("$.requester.iamUserId", is("iam-u-1001")))
            .andExpect(jsonPath("$.requester.organizationName", is("信息技术部")))
            .andExpect(jsonPath("$.serviceCatalogItem.id", is("SC-browser-performance")));
    }

    @Test
    void richDescriptionIsSanitizedAndReturnedWithItsPlainTextProjection() throws Exception {
        String richRequest = CREATE_REQUEST.replace("\"description\":\"打开工作台后响应缓慢\"",
            "\"description\":\"<p>打开<strong>工作台</strong>后响应缓慢</p><a href=\\\"https://kb.intra.example/case\\\" onclick=\\\"alert(1)\\\">案例</a>\",\"descriptionFormat\":\"RICH_TEXT\"");

        mockMvc.perform(create("0ad3c2b1-1234-4abc-8def-123456789012", richRequest, "iam-u-1001"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.descriptionFormat", is("RICH_TEXT")))
            .andExpect(jsonPath("$.description", containsString("打开工作台后响应缓慢")))
            .andExpect(jsonPath("$.descriptionHtml", containsString("<strong>工作台</strong>")))
            .andExpect(jsonPath("$.descriptionHtml", org.hamcrest.Matchers.not(containsString("onclick"))));
    }

    @Test
    void sameActorAndIdempotencyKeyReplaysTheOriginalTicket() throws Exception {
        String key = "b4d3c2b1-1234-4abc-8def-123456789012";
        MvcResult first = mockMvc.perform(create(key, CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = responseId(first);

        mockMvc.perform(create(key, CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.id", is(ticketId)));
    }

    @Test
    void reusedKeyWithDifferentRequestIsRejected() throws Exception {
        String key = "c4d3c2b1-1234-4abc-8def-123456789012";
        mockMvc.perform(create(key, CREATE_REQUEST, "iam-u-1001")).andExpect(status().isCreated());

        mockMvc.perform(create(key, CREATE_REQUEST.replace("页面卡顿", "无法登录"), "iam-u-1001"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    void listAndDetailAreFilteredByObjectAuthorization() throws Exception {
        MvcResult owned = mockMvc.perform(create("d4d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn();
        mockMvc.perform(create("e4d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST.replace("页面卡顿", "网络异常"), "iam-u-1002"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tickets?q=核协").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", is(1)))
            .andExpect(jsonPath("$.items[0].requester.iamUserId", is("iam-u-1001")));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}", responseId(owned))
                .with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void clientCannotSupplyServerControlledFields() throws Exception {
        String malicious = CREATE_REQUEST.substring(0, CREATE_REQUEST.length() - 2)
            + ",\"requesterId\":\"iam-admin\",\"priority\":\"P1\",\"status\":\"CLOSED\"}";
        mockMvc.perform(create("f4d3c2b1-1234-4abc-8def-123456789012", malicious, "iam-u-1001"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void fixedQueuesAreDerivedFromCurrentIdentityAndWorkflowTasks() throws Exception {
        mockMvc.perform(create("a5d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tickets?queue=MY_REQUESTED").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", is(1)));
        mockMvc.perform(get("/api/v1/tickets?queue=MY_TODO").with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", is(1)));
    }

    @Test
    void relatedTicketsRequireAuthorizationAtBothEndpointsAndDeduplicateReversedRelatedLinks() throws Exception {
        String sourceId = responseId(mockMvc.perform(create("a6d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn());
        String targetId = responseId(mockMvc.perform(create("b6d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST.replace("页面卡顿", "网络异常"), "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn());

        String body = "{\"targetTicketId\":\"" + targetId + "\",\"relationType\":\"RELATED\"}";
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/relations", sourceId).with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.relatedTicket.id", is(targetId)));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/relations", targetId).with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetTicketId\":\"" + sourceId + "\",\"relationType\":\"RELATED\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/relations", sourceId).with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)));
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/relations", sourceId).with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(String key, String body, String userId) {
        return post("/api/v1/tickets")
            .with(user(userId).roles("REQUESTER"))
            .with(csrf())
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    private String responseId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return root.required("id").asText();
    }
}
