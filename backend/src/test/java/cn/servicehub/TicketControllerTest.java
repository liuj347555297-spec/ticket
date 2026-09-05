package cn.servicehub;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.servicehub.access.domain.BackofficeAccess;
import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.access.domain.BackofficeDataScope;
import cn.servicehub.security.VerifiedIamAuthenticationFactory;
import cn.servicehub.ticket.domain.IdentitySnapshot;
import cn.servicehub.sla.domain.TicketSlaTarget;
import cn.servicehub.sla.domain.TicketSlaTargetRepository;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.routing.NodeAssignmentResolver;
import java.time.Instant;
import java.util.Set;
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

    @Autowired
    private VerifiedIamAuthenticationFactory verifiedIam;

    @Autowired
    private BackofficeAccessRepository backofficeAccess;

    @Autowired
    private TicketWorkflowRepository workflows;

    @Autowired
    private NodeAssignmentResolver nodeAssignments;

    @Autowired
    private TicketSlaTargetRepository slaTargets;

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
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("NOT_FOUND")));
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
    void todayDueContainsOnlyCurrentTodoWithAnUnfinishedDeadlineToday() throws Exception {
        String ticketId = responseId(mockMvc.perform(create("b5d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn());
        TicketSlaTarget current = slaTargets.findByTicketId(ticketId).orElseThrow();
        Instant today = Instant.now();
        TicketSlaTarget dueToday = new TicketSlaTarget(current.ticketId(), current.policyId(), current.policyNameSnapshot(), current.calendarKeySnapshot(),
            today, today, null, null, current.pausedSeconds(), current.pauseStartedAt(), current.riskLevel(), false, false, today, current.version() + 1);
        slaTargets.save(dueToday, current.version());

        mockMvc.perform(get("/api/v1/tickets?queue=TODAY_DUE").with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", is(1)))
            .andExpect(jsonPath("$.items[0].id", is(ticketId)));
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
            .andExpect(status().isNotFound());
    }

    @Test
    void requesterCannotModifyOrRelateTicketsTheyDoNotOwn() throws Exception {
        String ownedId = responseId(mockMvc.perform(create("c6d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn());
        String foreignId = responseId(mockMvc.perform(create("d6d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST.replace("页面卡顿", "他人工单"), "iam-u-1002"))
            .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(patch("/api/v1/tickets/{ticketId}/description", foreignId)
                .with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"尝试篡改\",\"descriptionFormat\":\"PLAIN_TEXT\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("FORBIDDEN")));
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/relations", ownedId).with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetTicketId\":\"" + foreignId + "\",\"relationType\":\"RELATED\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void verifiedSupportScopeRejectsCrossOrganizationAndIntersectsScopeTypes() throws Exception {
        String itTicket = responseId(mockMvc.perform(create("e6d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(create("f6d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST.replace("页面卡顿", "财务网络异常"), "iam-u-1002"))
            .andExpect(status().isCreated());

        var financeSupport = verifiedIam.create("iam-u-1002", "TEST_OIDC");
        mockMvc.perform(get("/api/v1/tickets").with(authentication(financeSupport)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total", is(1)))
            .andExpect(jsonPath("$.items[0].requester.iamUserId", is("iam-u-1002")));
        mockMvc.perform(get("/api/v1/tickets/{id}", itTicket).with(authentication(financeSupport)))
            .andExpect(status().isNotFound());

        BackofficeAccess current = backofficeAccess.findByIamUserId("iam-u-local-service-manager").orElseThrow();
        Set<BackofficeDataScope> organizationsAndWrongCatalog = Set.of(
            new BackofficeDataScope("ORGANIZATION", "org-it"), new BackofficeDataScope("ORGANIZATION", "org-finance"),
            new BackofficeDataScope("SERVICE_CATALOG", "SC-not-matching"));
        backofficeAccess.save(new BackofficeAccess(current.iamUserId(), true, current.roleCodes(), organizationsAndWrongCatalog,
            current.version() + 1, Instant.now()), current.version(), "test-admin");
        mockMvc.perform(get("/api/v1/tickets").with(authentication(verifiedIam.create(current.iamUserId(), "TEST_OIDC"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total", is(0)));

        BackofficeAccess changed = backofficeAccess.findByIamUserId(current.iamUserId()).orElseThrow();
        Set<BackofficeDataScope> matchingIntersection = Set.of(
            new BackofficeDataScope("ORGANIZATION", "org-it"), new BackofficeDataScope("ORGANIZATION", "org-finance"),
            new BackofficeDataScope("SERVICE", "SC-browser-performance"));
        backofficeAccess.save(new BackofficeAccess(changed.iamUserId(), true, changed.roleCodes(), matchingIntersection,
            changed.version() + 1, Instant.now()), changed.version(), "test-admin");
        mockMvc.perform(get("/api/v1/tickets").with(authentication(verifiedIam.create(changed.iamUserId(), "TEST_OIDC"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total", is(2)));
    }

    @Test
    void cursorRejectsTamperingSubjectAndFilterChangesAndPagesStably() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(create("a7d3c2b1-1234-4ab" + i + "-8def-123456789012", CREATE_REQUEST.replace("页面卡顿", "分页工单" + i), "iam-u-1001"))
                .andExpect(status().isCreated());
        }
        MvcResult first = mockMvc.perform(get("/api/v1/tickets?pageSize=1").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasMore", is(true))).andExpect(jsonPath("$.total", is(3))).andReturn();
        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsByteArray());
        String firstId = firstJson.required("items").get(0).required("id").asText();
        String cursor = firstJson.required("nextCursor").asText();

        String newId = responseId(mockMvc.perform(create("b7d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST.replace("页面卡顿", "游标后新工单"), "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn());
        MvcResult second = mockMvc.perform(get("/api/v1/tickets?pageSize=1&cursor={cursor}", cursor).with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page", is(2))).andExpect(jsonPath("$.total", is(3))).andReturn();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsByteArray()).required("items").get(0).required("id").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);
        org.junit.jupiter.api.Assertions.assertNotEquals(newId, secondId);

        char replacement = cursor.charAt(cursor.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = cursor.substring(0, cursor.length() - 1) + replacement;
        mockMvc.perform(get("/api/v1/tickets?pageSize=1&cursor={cursor}", tampered).with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/tickets?pageSize=1&cursor={cursor}", cursor).with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/tickets?pageSize=1&q=changed&cursor={cursor}", cursor).with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void participantWithSupportRoleButNoCurrentScopeCannotReadListDetailOrAttachment() throws Exception {
        String ticketId = responseId(mockMvc.perform(create("c7d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn());
        workflows.addCoHandlerParticipant(ticketId, new IdentitySnapshot("iam-u-local-requester", "本地提单人",
            "ORG-LOCAL-IT", "本地组织", "用户", Instant.now()), Instant.now());
        backofficeAccess.save(new BackofficeAccess("iam-u-local-requester", true, Set.of("ROLE_FIRST_LINE_SUPPORT"), Set.of(),
            1, Instant.now()), 0, "test-admin");

        var participant = verifiedIam.create("iam-u-local-requester", "TEST_OIDC");
        mockMvc.perform(get("/api/v1/tickets").with(authentication(participant)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total", is(0)));
        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId).with(authentication(participant)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tickets/{id}/attachments/ATT-00000000-0000-4000-8000-000000000001/download", ticketId)
                .with(authentication(participant)))
            .andExpect(status().isNotFound());
    }

    @Test
    void routingCandidatesExcludeSupportUsersOutsideTicketScope() throws Exception {
        String ticketId = responseId(mockMvc.perform(create("d7d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated()).andReturn());
        org.junit.jupiter.api.Assertions.assertTrue(nodeAssignments.candidates(ticketId, "SC-browser-performance", "accept", "iam-u-1001").isEmpty());

        BackofficeAccess current = backofficeAccess.findByIamUserId("iam-u-1002").orElseThrow();
        Set<BackofficeDataScope> matching = Set.of(new BackofficeDataScope("ORGANIZATION", "org-it"),
            new BackofficeDataScope("SERVICE_CATALOG", "SC-browser-performance"));
        backofficeAccess.save(new BackofficeAccess(current.iamUserId(), true, current.roleCodes(), matching,
            current.version() + 1, Instant.now()), current.version(), "test-admin");
        org.junit.jupiter.api.Assertions.assertEquals(Set.of("iam-u-1002"), nodeAssignments.candidates(ticketId,
            "SC-browser-performance", "accept", "iam-u-1001").stream().map(NodeAssignmentResolver.HandlerCandidate::iamUserId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void catalogOrganizationNamesAndInclusiveUtcDatesAreServerFilteredWhileWildcardsStayLiteral() throws Exception {
        mockMvc.perform(create("e7d3c2b1-1234-4abc-8def-123456789012", CREATE_REQUEST, "iam-u-1001"))
            .andExpect(status().isCreated());
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        mockMvc.perform(get("/api/v1/tickets").param("serviceCatalog", "浏览器性能")
                .param("requesterOrganization", "信息技术").param("createdFrom", today).param("createdTo", today)
                .with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total", is(1)));
        mockMvc.perform(get("/api/v1/tickets").param("serviceCatalog", "%")
                .with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total", is(0)));
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
