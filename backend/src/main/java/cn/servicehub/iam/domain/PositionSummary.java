package cn.servicehub.iam.domain;

/** A read-only IAM position projection; it is not a platform permission grant. */
public record PositionSummary(String iamPositionId, String name, boolean primary) {
}
