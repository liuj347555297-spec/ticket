package cn.servicehub.iam.domain;

/** UI-facing summary only; every business request still performs server-side scope authorization. */
public record DataScopeSummary(String scopeType, String scopeId) {
}
