package cn.servicehub.knowledge.domain;
import java.time.Instant;
public record KnowledgeDocumentVersion(String id,String documentId,int versionNumber,String attachmentStorageKey,String detectedMediaType,long sizeBytes,KnowledgePublicationStatus status,String reviewerIamUserId,Instant createdAt,Instant publishedAt) { }
