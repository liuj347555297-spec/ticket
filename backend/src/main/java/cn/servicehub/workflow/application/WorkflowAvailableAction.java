package cn.servicehub.workflow.application;

/**
 * A server-calculated action affordance for the current identity and workflow state.
 * It is a display contract only; the write endpoint repeats all authorization checks.
 */
public record WorkflowAvailableAction(String code, String label, boolean requiresTarget) {
}
