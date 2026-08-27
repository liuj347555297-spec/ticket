package cn.servicehub;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @Autowired TaskService taskService;

    @Test
    void lifecycleUsesServerStateAndRejectsStaleVersion() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "a1d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceCatalogItemId":"SC-browser-performance","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"页面卡顿","description":"工作台缓慢","structuredFields":{"browser":"Chrome"},"tags":[]}
                """))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).required("id").asText();

        action(ticketId, 0, "CLASSIFY", null, "ROLE_FIRST_LINE_SUPPORT").andExpect(jsonPath("$.status", is("PENDING_ASSIGNMENT")));
        action(ticketId, 1, "ASSIGN", "iam-u-1002", "iam-u-1001", "ROLE_SERVICE_MANAGER").andExpect(jsonPath("$.status", is("PENDING_ACCEPTANCE")));
        action(ticketId, 2, "ACCEPT", null, "iam-u-1002", "ROLE_FIRST_LINE_SUPPORT").andExpect(jsonPath("$.status", is("IN_PROGRESS")));
        action(ticketId, 2, "REQUEST_USER_FEEDBACK", null, "iam-u-1002", "ROLE_FIRST_LINE_SUPPORT").andExpect(status().isConflict()).andExpect(jsonPath("$.code", is("WORKFLOW_CONFLICT")));
        action(ticketId, 3, "REQUEST_USER_FEEDBACK", null, "iam-u-1002", "ROLE_FIRST_LINE_SUPPORT").andExpect(jsonPath("$.status", is("PENDING_USER_FEEDBACK")));
    }

    @Test
    void controlledJumpIsOnlyAnApplicationAndCannotChangeStatus() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "b1d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceCatalogItemId":"SC-browser-performance","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"跳转测试","description":"工作台缓慢","structuredFields":{"browser":"Chrome"},"tags":[]}
                """))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).required("id").asText();
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")).with(csrf())
                .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content("""
                {"action":"CONTROLLED_JUMP_REQUEST","targetNode":"closure","reason":"需走审批的应急纠偏"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("SUBMITTED"))).andExpect(jsonPath("$.version", is(0)));
    }

    @Test
    void controlledJumpApprovalUsesIndependentFlowableProcessAndRejectsSelfApproval() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "d1d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceCatalogItemId":"SC-browser-performance","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"审批流程测试","description":"工作台缓慢","structuredFields":{"browser":"Chrome"},"tags":[]}
                """))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).required("id").asText();
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")).with(csrf())
                .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content("""
                {"action":"CONTROLLED_JUMP_REQUEST","targetNode":"closure","reason":"需走独立审批流程"}
                """))
            .andExpect(status().isOk());
        MvcResult overview = mockMvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.approvalRequests[0].status", is("PENDING_APPROVAL")))
            .andExpect(jsonPath("$.approvalRequests[0].approvalEngineInstanceId").isNotEmpty())
            .andExpect(jsonPath("$.approvalRequests[0].approvalPolicy.processKey", is("servicehubControlledJumpApproval")))
            .andExpect(jsonPath("$.approvalRequests[0].approvalPolicy.processDefinitionId").isNotEmpty())
            .andExpect(jsonPath("$.approvalRequests[0].approvalPolicy.processVersion", is(1)))
            .andExpect(jsonPath("$.approvalRequests[0].approvalPolicy.decisionMode", is("ANY_ONE")))
            .andExpect(jsonPath("$.approvalRequests[0].approvalPolicy.candidateRoles").isArray())
            .andExpect(jsonPath("$.approvalRequests[0].approvalPolicy.candidateIamUserIds").doesNotExist()).andReturn();
        String requestId = objectMapper.readTree(overview.getResponse().getContentAsByteArray()).at("/approvalRequests/0/id").asText();
        String approvalEngineInstanceId = objectMapper.readTree(overview.getResponse().getContentAsByteArray()).at("/approvalRequests/0/approvalEngineInstanceId").asText();
        assertEquals(java.util.Set.of("iam-u-1002", "iam-u-local-admin", "iam-u-local-service-manager"), taskService.createTaskQuery()
            .processInstanceId(approvalEngineInstanceId).taskDefinitionKey("approval_decision").list().stream().map(Task::getAssignee).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.Set.of(requestId), taskService.createTaskQuery().processInstanceId(approvalEngineInstanceId).taskDefinitionKey("approval_decision").list().stream()
            .map(task -> taskService.getVariable(task.getId(), "approvalRequestId")).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, taskService.createTaskQuery().processInstanceId(approvalEngineInstanceId).taskDefinitionKey("approval_decision").taskAssignee("iam-u-1002").active().count());
        mockMvc.perform(get("/api/v1/workflow/approval-tasks").with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workflow/approval-tasks").with(user("iam-u-1001").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/api/v1/workflow/approval-tasks").with(user("iam-u-1002").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].approvalRequestId", is(requestId)))
            .andExpect(jsonPath("$.items[0].ticketId", is(ticketId)))
            .andExpect(jsonPath("$.items[0].decisionMode", is("ANY_ONE")))
            .andExpect(jsonPath("$.items[0].candidateApprovalCount", is(3)))
            .andExpect(jsonPath("$.items[0].requiredApprovalCount", is(1)))
            .andExpect(jsonPath("$.items[0].canDecide", is(true)));
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/decisions", ticketId, requestId)
                .with(user("iam-u-1001").roles("SERVICE_MANAGER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\",\"reason\":\"申请人不得自批\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/decisions", ticketId, requestId)
                .with(user("iam-u-1002").roles("SERVICE_MANAGER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\",\"reason\":\"同意进入受控执行预演\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("APPROVED")));
        mockMvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1002").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.approvalDecisions[0].approvalRequestId", is(requestId)))
            .andExpect(jsonPath("$.approvalDecisions[0].decision", is("APPROVED")))
            .andExpect(jsonPath("$.approvalDecisions[0].engineTaskId").isNotEmpty());
        mockMvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.controlledJumpActions").isEmpty());
        mockMvc.perform(get("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/preflight", ticketId, requestId)
                .with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/execute", ticketId, requestId)
                .with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")).with(csrf()).header("If-Match", "\"0\""))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1002").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.controlledJumpActions[0].requestId", is(requestId)))
            .andExpect(jsonPath("$.controlledJumpActions[0].canPreflight", is(true)))
            .andExpect(jsonPath("$.controlledJumpActions[0].canExecute", is(true)));
        mockMvc.perform(get("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/preflight", ticketId, requestId)
                .with(user("iam-u-1002").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.executable", is(true)))
            .andExpect(jsonPath("$.blockingReasons").isEmpty())
            .andExpect(jsonPath("$.currentTaskDisposition", is("CANCEL_CURRENT_TASK_WITH_AUDIT")))
            .andExpect(jsonPath("$.targetCandidateRole", is("ROLE_REQUESTER")))
            .andExpect(jsonPath("$.candidateResolution", is("REQUESTER_SNAPSHOT_REVALIDATION")))
            .andExpect(jsonPath("$.candidateRecalculationRequired", is(true)))
            .andExpect(jsonPath("$.slaImpact", is("RECALCULATE_REQUIRED_ON_EXECUTION")));
        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId).with(user("iam-u-1002").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("SUBMITTED"))).andExpect(jsonPath("$.version", is(0)));
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/execute", ticketId, requestId)
                .with(user("iam-u-1002").roles("SERVICE_MANAGER")).with(csrf()).header("If-Match", "\"0\""))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("RESOLVED"))).andExpect(jsonPath("$.version", is(1)));
        mockMvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1002").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.instance.currentNode", is("closure")))
            .andExpect(jsonPath("$.tasks[0].nodeKey", is("classify")))
            .andExpect(jsonPath("$.tasks[0].status", is("CANCELLED")))
            .andExpect(jsonPath("$.tasks[1].nodeKey", is("closure")))
            .andExpect(jsonPath("$.tasks[1].status", is("OPEN")))
            .andExpect(jsonPath("$.approvalRequests[0].status", is("EXECUTED")))
            .andExpect(jsonPath("$.approvalRequests[0].executorIamUserId", is("iam-u-1002")))
            .andExpect(jsonPath("$.approvalRequests[0].executedFromNode", is("classify")))
            .andExpect(jsonPath("$.approvalRequests[0].executedToNode", is("closure")));
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/execute", ticketId, requestId)
                .with(user("iam-u-1002").roles("SERVICE_MANAGER")).with(csrf()).header("If-Match", "\"1\""))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code", is("WORKFLOW_CONFLICT")));
    }

    @Test
    void workflowOverviewPublishesOnlyServerCalculatedActions() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "c1d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceCatalogItemId":"SC-browser-performance","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"动作读模型","description":"验证服务端动作","structuredFields":{"browser":"Chrome"},"tags":[]}
                """))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).required("id").asText();

        mockMvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableActions[0].code", is("CLASSIFY")))
            .andExpect(jsonPath("$.availableActions[?(@.code == 'ASSIGN')]").isEmpty());
    }

    @Test
    void assignmentRejectsActiveIamUserWithoutSynchronizedSupportRole() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "e1d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceCatalogItemId":"SC-browser-performance","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"分派角色校验","description":"测试只读角色投影","structuredFields":{"browser":"Chrome"},"tags":[]}
                """))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).required("id").asText();
        action(ticketId, 0, "CLASSIFY", null, "iam-u-1001", "ROLE_FIRST_LINE_SUPPORT").andExpect(status().isOk());
        action(ticketId, 1, "ASSIGN", "iam-u-1001", "iam-u-1002", "ROLE_SERVICE_MANAGER")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    void workflowDefinitionRegistryIsReadOnlyAndPlatformAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/definitions").with(user("iam-u-1001").roles("SERVICE_MANAGER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workflow/definitions").with(user("iam-u-1002").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.processKey == 'servicehubTicketLifecycle')]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.processKey == 'servicehubControlledJumpApproval')]").isNotEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions action(String id, long version, String action, String target, String role) throws Exception {
        return action(id, version, action, target, "iam-u-1001", role);
    }

    private org.springframework.test.web.servlet.ResultActions action(String id, long version, String action, String target, String iamUserId, String role) throws Exception {
        String targetJson = target == null ? "" : ",\"targetIamUserId\":\"" + target + "\"";
        return mockMvc.perform(post("/api/v1/tickets/{id}/workflow/actions", id).with(user(iamUserId).roles(role.substring("ROLE_".length()))).with(csrf())
            .header("If-Match", "\"" + version + "\"").contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"" + action + "\"" + targetJson + "}"));
    }
}
