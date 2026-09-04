package cn.servicehub.workflow.domain;

import java.time.Instant;

public record WorkflowTask(String id, String ticketId, String engineTaskId, String nodeKey,
                           WorkflowTaskStatus status, String candidateRole, String candidateIamUserId,
                           String assigneeIamUserId, CollaborationRole collaborationRole,
                           long version, Instant createdAt, Instant updatedAt, String queueCode) {
    public WorkflowTask(String id,String ticketId,String engineTaskId,String nodeKey,WorkflowTaskStatus status,String candidateRole,String candidateIamUserId,String assigneeIamUserId,CollaborationRole collaborationRole,long version,Instant createdAt,Instant updatedAt){this(id,ticketId,engineTaskId,nodeKey,status,candidateRole,candidateIamUserId,assigneeIamUserId,collaborationRole,version,createdAt,updatedAt,null);}
}
