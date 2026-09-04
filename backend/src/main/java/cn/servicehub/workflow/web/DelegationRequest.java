package cn.servicehub.workflow.web;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Delegator is always derived from the authenticated primary handler, never supplied by a browser. */
public record DelegationRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9._:-]{1,128}$") String delegateIamUserId,
    @Future OffsetDateTime effectiveUntil,
    @NotBlank @Size(min = 5, max = 500) String reason) { }
