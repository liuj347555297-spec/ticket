package cn.servicehub.workflow.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.Set;

/** Immutable approval policy evidence captured before an approval process instance is started. */
public record ApprovalPolicySnapshot(String processKey, String processDefinitionId, int processVersion,
                                     Set<String> candidateRoles, String decisionMode,
                                     String timeoutPolicyVersion, String escalationPolicyVersion,
                                     Instant capturedAt, @JsonIgnore Set<String> candidateIamUserIds) {
    public ApprovalPolicySnapshot {
        candidateRoles = candidateRoles == null ? Set.of() : Set.copyOf(candidateRoles);
        candidateIamUserIds = candidateIamUserIds == null ? Set.of() : Set.copyOf(candidateIamUserIds);
        if (processKey == null || processDefinitionId == null || processVersion < 0 || candidateRoles.isEmpty()
            || decisionMode == null || timeoutPolicyVersion == null || escalationPolicyVersion == null || capturedAt == null) {
            throw new IllegalArgumentException("Approval policy snapshot is incomplete");
        }
    }

    /** Legacy rows may be displayed for audit, but cannot be decided using an unrecorded definition. */
    public boolean hasRecordedDefinition() {
        return processVersion > 0 && !"LEGACY_UNRECORDED".equals(processDefinitionId) && !candidateIamUserIds.isEmpty();
    }
}
