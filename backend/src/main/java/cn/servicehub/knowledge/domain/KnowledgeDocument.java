package cn.servicehub.knowledge.domain;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The source ticket reference is intentionally metadata only.  Ticket narratives and attachments
 * are never copied into a knowledge record by the conversion endpoint.
 */
public record KnowledgeDocument(String id, String title, String categoryCode, List<String> tags,
                                String owningOrganizationId, List<String> serviceCatalogItemIds,
                                KnowledgePublicationStatus status, String currentVersionId,
                                String creatorIamUserId, Instant createdAt, Instant updatedAt,
                                Instant reviewDueAt, String reviewOwnerIamUserId, String sourceTicketId) {
 public KnowledgeDocument {
  tags=tags==null?List.of():List.copyOf(tags);
  serviceCatalogItemIds=serviceCatalogItemIds==null?List.of():serviceCatalogItemIds.stream().filter(Objects::nonNull).map(String::trim).filter(v->!v.isBlank()).distinct().toList();
  if(status!=KnowledgePublicationStatus.MIGRATION_PENDING&&(owningOrganizationId==null||owningOrganizationId.isBlank()||serviceCatalogItemIds.isEmpty()))throw new IllegalArgumentException("Knowledge scope is required");
 }
}
