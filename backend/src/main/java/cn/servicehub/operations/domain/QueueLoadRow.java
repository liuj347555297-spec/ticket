package cn.servicehub.operations.domain;

/** Bounded current workload projection, grouped only by assignee and queue state. */
public record QueueLoadRow(String assigneeIamUserId, String queueState, long openCount) {
}
