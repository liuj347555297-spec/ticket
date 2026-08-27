package cn.servicehub.access.web;

import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Deliberately contains no IAM profile fields: user/organization data is read only from IAM. */
public record BackofficeAccessRequest(boolean enabled,
                                     @NotNull @Size(max = 16) Set<@Size(max = 64) String> roleCodes,
                                     @NotNull @Size(max = 200) Set<@Valid BackofficeDataScopeRequest> dataScopes,
                                     long expectedVersion) {
}
