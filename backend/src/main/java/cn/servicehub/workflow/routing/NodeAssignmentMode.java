package cn.servicehub.workflow.routing;

/** The server-owned way a target workflow node receives its primary handler. */
public enum NodeAssignmentMode { SYSTEM_RANDOM, PREVIOUS_HANDLER_SELECTS, SHARED_QUEUE }
