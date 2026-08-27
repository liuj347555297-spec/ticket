package cn.servicehub.access.domain;

/** Platform-local data scope attached to a backoffice IAM identity. */
public record BackofficeDataScope(String scopeType, String scopeId) {
}
