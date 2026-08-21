package cn.servicehub.knowledge.domain;
import java.time.Instant;
public record KnowledgeImportTask(String id,String documentId,String sourceAttachmentId,String status,String submitterIamUserId,String errorCode,Instant createdAt,Instant completedAt) { }
