package cn.servicehub.workflow.application;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the approval mode exclusively from deployment-controlled policy.  It deliberately
 * has no request DTO or admin UI input: a browser must never choose between or-sign and countersign.
 */
@Component
public class ControlledJumpApprovalPolicyResolver {
    private static final Set<String> ALLOWED_TARGET_NODES = Set.of("classify", "assign", "accept", "processing", "user_feedback", "closure");
    private final Set<String> allOfTargetNodes;

    public ControlledJumpApprovalPolicyResolver(
        @Value("${servicehub.workflow.controlled-jump.all-of-target-nodes:}") String configuredNodes
    ) {
        this.allOfTargetNodes = Arrays.stream(configuredNodes.split(","))
            .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (!ALLOWED_TARGET_NODES.containsAll(allOfTargetNodes)) {
            throw new IllegalArgumentException("Controlled-jump countersign policy contains an unknown target node");
        }
    }

    public String decisionModeFor(String targetNode) {
        if (!ALLOWED_TARGET_NODES.contains(targetNode)) throw new IllegalArgumentException("Controlled-jump target node is invalid");
        return allOfTargetNodes.contains(targetNode) ? "ALL_OF" : "ANY_ONE";
    }
}
