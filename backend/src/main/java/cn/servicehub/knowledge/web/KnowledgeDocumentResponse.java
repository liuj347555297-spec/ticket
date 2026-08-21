package cn.servicehub.knowledge.web;
import cn.servicehub.knowledge.domain.KnowledgeDocument;
import java.time.Instant;import java.util.List;
public record KnowledgeDocumentResponse(String id,String title,String categoryCode,List<String> tags,String status,String currentVersionId,Instant updatedAt){static KnowledgeDocumentResponse from(KnowledgeDocument d){return new KnowledgeDocumentResponse(d.id(),d.title(),d.categoryCode(),d.tags(),d.status().name(),d.currentVersionId(),d.updatedAt());}}
