package cn.servicehub;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.servicehub.workflow.engine.WorkflowEngineInstance;
import cn.servicehub.workflow.engine.WorkflowApprovalDefinition;
import cn.servicehub.workflow.engine.WorkflowEnginePort;
import cn.servicehub.workflow.engine.WorkflowApprovalDecisionResult;
import cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalEnginePort;
import cn.servicehub.access.domain.BackofficeAccess;
import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.access.domain.BackofficeDataScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** A failed engine call must never strand an approval in EXECUTING or create a false completion. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ControlledJumpFailureRollbackTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BackofficeAccessRepository backofficeAccess;
    @MockBean WorkflowEnginePort workflowEngine;
    // The production Flowable adapter implements both ports. Replacing only the lifecycle
    // adapter in this controlled-jump failure test would leave the new independent approval
    // port absent from the application context.
    @MockBean LifecycleActionApprovalEnginePort lifecycleActionApprovalEngine;

    @BeforeEach
    void engineFailureIsPrepared() {
        // The stricter approval-candidate contract requires a real current scope match. Keep the
        // failure test focused on engine rollback by explicitly granting its service manager the
        // ticket organization instead of relying on the former global role pool.
        BackofficeAccess manager = backofficeAccess.findByIamUserId("iam-u-local-service-manager").orElseThrow();
        backofficeAccess.save(new BackofficeAccess(manager.iamUserId(), true, manager.roleCodes(),
            Set.of(new BackofficeDataScope("ORGANIZATION", "org-it")), manager.version() + 1, Instant.now()),
            manager.version(), "test-admin");
        when(workflowEngine.start(anyString())).thenReturn(new WorkflowEngineInstance("lifecycle-1", "classify", "task-classify-1"));
        when(workflowEngine.resolveControlledJumpApprovalDefinition())
            .thenReturn(new WorkflowApprovalDefinition("servicehubControlledJumpApproval", "approval-definition-1", 1));
        when(workflowEngine.startControlledJumpApproval(anyString(), anyString(), anyString(), any(WorkflowApprovalDefinition.class), any(), anyString()))
            .thenReturn(new WorkflowEngineInstance("approval-1", "approval_decision", "task-approval-1"));
        when(workflowEngine.decideControlledJumpApproval(anyString(), anyString(), anyString()))
            .thenReturn(new WorkflowApprovalDecisionResult("task-approval-1", true, "APPROVED"));
        when(workflowEngine.moveControlledActivity(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("engine migration failure"));
    }

    @Test
    void failedEngineMigrationReleasesReservationAndKeepsTicketUnchanged() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "e1d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceCatalogItemId":"SC-browser-performance","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"引擎失败回滚","description":"验证失败关闭","structuredFields":{"browser":"Chrome"},"tags":[]}
                """))
            .andExpect(status().isCreated()).andReturn();
        String ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).required("id").asText();

        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")).with(csrf())
                .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content("""
                {"action":"CONTROLLED_JUMP_REQUEST","targetNode":"closure","reason":"验证受控执行引擎异常时的失败关闭"}
                """))
            .andExpect(status().isOk());
        MvcResult overview = mockMvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1001").roles("FIRST_LINE_SUPPORT")))
            .andExpect(status().isOk()).andReturn();
        String requestId = objectMapper.readTree(overview.getResponse().getContentAsByteArray()).at("/approvalRequests/0/id").asText();
        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/decisions", ticketId, requestId)
                .with(user("iam-u-local-service-manager").roles("SERVICE_MANAGER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\",\"reason\":\"可执行但需验证引擎异常\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tickets/{id}/workflow/approval-requests/{requestId}/execute", ticketId, requestId)
                .with(user("iam-u-local-service-manager").roles("SERVICE_MANAGER")).with(csrf()).header("If-Match", "\"0\""))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code", is("SERVICE_UNAVAILABLE")));

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId).with(user("iam-u-local-service-manager").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("SUBMITTED"))).andExpect(jsonPath("$.version", is(0)));
        mockMvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-local-service-manager").roles("SERVICE_MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.instance.currentNode", is("classify")))
            .andExpect(jsonPath("$.tasks[0].status", is("OPEN")))
            .andExpect(jsonPath("$.approvalRequests[0].status", is("APPROVED")))
            .andExpect(jsonPath("$.approvalRequests[0].executionFailureReason", is("ENGINE_EXECUTION_FAILED")));
    }
}
