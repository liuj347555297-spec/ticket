package cn.servicehub.access.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BackofficeDataScopeRequest(@NotBlank @Size(max = 32) String scopeType,
                                         @NotBlank @Size(max = 128) String scopeId) {
}
