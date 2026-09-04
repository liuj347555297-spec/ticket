package cn.servicehub.workflow.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The target may only accept or reject its own server-assigned handover task. */
public record HandoverDecisionRequest(@NotNull @Pattern(regexp = "^(ACCEPTED|REJECTED)$") String decision,
                                     @Size(min = 5, max = 1000) String reason) {
}
