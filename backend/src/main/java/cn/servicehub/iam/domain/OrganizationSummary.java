package cn.servicehub.iam.domain;

/** Minimal organization information safe to return from the current-user endpoint. */
public record OrganizationSummary(String iamOrganizationId, String name) {
}
