package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.servicehub.workflow.engine.WorkflowApprovalDecisionResult;
import cn.servicehub.workflow.engine.WorkflowApprovalDefinition;
import cn.servicehub.workflow.engine.WorkflowEngineInstance;
import cn.servicehub.workflow.engine.WorkflowEnginePort;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/** Exercises the deployed BPMN rather than emulating multi-instance behavior in application code. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FlowableMultiInstanceApprovalTest {
    @Autowired WorkflowEnginePort workflowEngine;

    @Test
    void anyOneClosesAllRemainingTasksAfterOneFrozenApproverDecides() {
        WorkflowEngineInstance instance = start("approval-any-one", Set.of("iam-approver-a", "iam-approver-b"), "ANY_ONE");

        assertEquals(1, workflowEngine.findPendingControlledJumpApprovalTasks("iam-approver-a", 0, 10).size());
        assertEquals(1, workflowEngine.findPendingControlledJumpApprovalTasks("iam-approver-b", 0, 10).size());
        WorkflowApprovalDecisionResult result = workflowEngine.decideControlledJumpApproval(instance.instanceId(), "iam-approver-a", "APPROVED");

        assertTrue(result.processCompleted());
        assertEquals("APPROVED", result.finalDecision());
        assertEquals(0, workflowEngine.findPendingControlledJumpApprovalTasks("iam-approver-b", 0, 10).size());
    }

    @Test
    void allOfRemainsPendingUntilEveryFrozenApproverApprovesAndRejectClosesImmediately() {
        WorkflowEngineInstance approved = start("approval-all-of-approved", Set.of("iam-approver-c", "iam-approver-d"), "ALL_OF");
        WorkflowApprovalDecisionResult first = workflowEngine.decideControlledJumpApproval(approved.instanceId(), "iam-approver-c", "APPROVED");

        assertFalse(first.processCompleted());
        assertNull(first.finalDecision());
        assertEquals(1, workflowEngine.findPendingControlledJumpApprovalTasks("iam-approver-d", 0, 10).size());
        WorkflowApprovalDecisionResult second = workflowEngine.decideControlledJumpApproval(approved.instanceId(), "iam-approver-d", "APPROVED");
        assertTrue(second.processCompleted());
        assertEquals("APPROVED", second.finalDecision());

        WorkflowEngineInstance rejected = start("approval-all-of-rejected", Set.of("iam-approver-e", "iam-approver-f"), "ALL_OF");
        WorkflowApprovalDecisionResult rejection = workflowEngine.decideControlledJumpApproval(rejected.instanceId(), "iam-approver-e", "REJECTED");
        assertTrue(rejection.processCompleted());
        assertEquals("REJECTED", rejection.finalDecision());
        assertEquals(0, workflowEngine.findPendingControlledJumpApprovalTasks("iam-approver-f", 0, 10).size());
    }

    private WorkflowEngineInstance start(String requestId, Set<String> candidates, String mode) {
        WorkflowApprovalDefinition definition = workflowEngine.resolveControlledJumpApprovalDefinition();
        return workflowEngine.startControlledJumpApproval(requestId, "ticket-for-" + requestId, "iam-applicant", definition, candidates, mode);
    }
}
