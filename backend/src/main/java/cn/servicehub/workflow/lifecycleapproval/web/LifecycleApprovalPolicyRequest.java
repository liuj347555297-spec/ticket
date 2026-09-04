package cn.servicehub.workflow.lifecycleapproval.web;

import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.workflow.domain.WorkflowAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Candidate roles are policy-governed; individual IAM users and Flowable definition IDs are never accepted. */
public record LifecycleApprovalPolicyRequest(@NotBlank @Size(max=120) String name, @NotNull WorkflowAction action,
    @Size(max=128) String serviceCatalogItemId, TicketPriority priority, @NotEmpty Set<@Pattern(regexp="^ROLE_[A-Z0-9_]+$") String> candidateRoles,
    @NotBlank @Pattern(regexp="^(ANY_ONE|ALL_OF|QUORUM)$") String decisionMode, @Min(1) @Max(100) int approvalThresholdPercent,
    @Min(1) @Max(43200) int timeoutMinutes, @NotBlank @Size(max=64) String timeoutPolicyVersion,
    @NotBlank @Size(max=64) String escalationPolicyVersion, Long expectedVersion) { }
