package cn.servicehub.workflow.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(@NotNull @Pattern(regexp = "^(APPROVED|REJECTED)$") String decision,
                                      @Size(min = 5, max = 1000) String reason) { }
