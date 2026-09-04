package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalEnginePort;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Verifies the Flowable multi-instance process, not a browser-side counter, decides a quorum. */
@SpringBootTest
class LifecycleActionApprovalQuorumFlowableTest {
    @Autowired LifecycleActionApprovalEnginePort engine;
    @Test void quorumApprovesOnThresholdAndRejectsOnlyWhenThresholdBecomesImpossible() {
        var definition=engine.resolveDefinition();
        var approved=engine.start("quorum-approved","ticket-q1","applicant",definition,Set.of("a","b","c"),"QUORUM",2);
        assertFalse(engine.decide(approved.instanceId(),"a","APPROVED").processCompleted());
        var completion=engine.decide(approved.instanceId(),"b","APPROVED");
        assertTrue(completion.processCompleted()); assertTrue("APPROVED".equals(completion.finalDecision()));
        var rejected=engine.start("quorum-rejected","ticket-q2","applicant",definition,Set.of("d","e","f"),"QUORUM",3);
        assertFalse(engine.decide(rejected.instanceId(),"d","APPROVED").processCompleted());
        assertFalse(engine.decide(rejected.instanceId(),"e","APPROVED").processCompleted());
        var impossible=engine.decide(rejected.instanceId(),"f","REJECTED");
        assertTrue(impossible.processCompleted()); assertTrue("REJECTED".equals(impossible.finalDecision()));
    }
}
