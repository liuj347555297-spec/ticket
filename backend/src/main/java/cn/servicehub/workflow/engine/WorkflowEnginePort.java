package cn.servicehub.workflow.engine;

/** Explicit anti-corruption boundary around Flowable. Domain services never call Flowable APIs directly. */
public interface WorkflowEnginePort {
    WorkflowEngineInstance start(String ticketId);
    WorkflowEngineInstance advance(String instanceId, String expectedNodeKey);
    void cancel(String instanceId, String reason);
}
