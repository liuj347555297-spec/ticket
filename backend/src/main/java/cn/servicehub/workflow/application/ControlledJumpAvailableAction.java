package cn.servicehub.workflow.application;

/**
 * Server-calculated management affordance for one approved controlled-jump request.
 * This is intentionally only a read model: both preflight and execution repeat object
 * authorization, role checks and optimistic-version checks on the server.
 */
public record ControlledJumpAvailableAction(String requestId, boolean canPreflight, boolean canExecute,
                                            String disabledReason) {
}
