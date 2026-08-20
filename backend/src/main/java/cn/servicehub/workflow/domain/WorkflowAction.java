package cn.servicehub.workflow.domain;

/** Browser requests an action only; the server derives the resulting state and assignee. */
public enum WorkflowAction {
    CLASSIFY, ASSIGN, ACCEPT, START_PROCESSING, REQUEST_USER_FEEDBACK, RESOLVE, CLOSE,
    REOPEN, CANCEL, HOLD, RESUME, ESCALATE, CLAIM, TRANSFER, ADD_COHANDLER, HANDOVER,
    INTERNAL_COMMENT, CONTROLLED_JUMP_REQUEST
}
