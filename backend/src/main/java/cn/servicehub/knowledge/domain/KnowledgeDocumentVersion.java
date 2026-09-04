package cn.servicehub.knowledge.domain;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
public record KnowledgeDocumentVersion(String id,String documentId,int versionNumber,String attachmentStorageKey,String detectedMediaType,long sizeBytes,String owningOrganizationId,List<String> serviceCatalogItemIds,KnowledgePublicationStatus status,String reviewerIamUserId,Instant createdAt,Instant publishedAt) {
 public KnowledgeDocumentVersion {serviceCatalogItemIds=serviceCatalogItemIds==null?List.of():serviceCatalogItemIds.stream().filter(Objects::nonNull).map(String::trim).filter(v->!v.isBlank()).distinct().toList();if(status!=KnowledgePublicationStatus.MIGRATION_PENDING&&(owningOrganizationId==null||owningOrganizationId.isBlank()||serviceCatalogItemIds.isEmpty()))throw new IllegalArgumentException("Knowledge version scope is required");}
}
