package cn.servicehub.knowledge.web;
import cn.servicehub.knowledge.domain.KnowledgeDocument;
import java.time.Instant;import java.util.List;
public record KnowledgeDocumentResponse(String id,String title,String categoryCode,List<String> tags,String owningOrganizationId,List<String> serviceCatalogItemIds,String status,String currentVersionId,int currentVersionNumber,String creatorIamUserId,Instant createdAt,Instant updatedAt,String sourceTicketId,boolean favorite){
 static KnowledgeDocumentResponse from(KnowledgeDocument d){return from(d,1,false);}
 static KnowledgeDocumentResponse from(KnowledgeDocument d,int version,boolean favorite){return new KnowledgeDocumentResponse(d.id(),d.title(),d.categoryCode(),d.tags(),d.owningOrganizationId(),d.serviceCatalogItemIds(),d.status().name(),d.currentVersionId(),version,d.creatorIamUserId(),d.createdAt(),d.updatedAt(),d.sourceTicketId(),favorite);}
}
