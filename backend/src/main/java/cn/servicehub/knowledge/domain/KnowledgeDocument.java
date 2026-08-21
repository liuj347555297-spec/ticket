package cn.servicehub.knowledge.domain;
import java.time.Instant;
import java.util.List;
public record KnowledgeDocument(String id, String title, String categoryCode, List<String> tags, KnowledgePublicationStatus status, String currentVersionId, String creatorIamUserId, Instant createdAt, Instant updatedAt) { public KnowledgeDocument { tags=tags==null?List.of():List.copyOf(tags); } }
