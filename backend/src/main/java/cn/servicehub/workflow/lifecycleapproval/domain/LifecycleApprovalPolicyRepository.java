package cn.servicehub.workflow.lifecycleapproval.domain;

import java.util.List;
import java.util.Optional;

public interface LifecycleApprovalPolicyRepository {
    List<LifecycleApprovalPolicy> findAll();
    List<LifecycleApprovalPolicy> findPublishedByAction(String actionCode);
    Optional<LifecycleApprovalPolicy> findById(String id);
    LifecycleApprovalPolicy save(LifecycleApprovalPolicy policy, Long expectedVersion);
}
