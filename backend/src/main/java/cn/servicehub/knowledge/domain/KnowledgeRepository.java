package cn.servicehub.knowledge.domain;
import java.util.List;
import java.util.Optional;
public interface KnowledgeRepository {
 void save(KnowledgeDocument document,KnowledgeDocumentVersion version,KnowledgeImportTask task);
 void saveDraft(KnowledgeDocument document, KnowledgeDocumentVersion version);
 void updateDraft(KnowledgeDocument document, KnowledgeDocumentVersion version, String expectedCurrentVersionId);
 void saveVersion(KnowledgeDocumentVersion version, KnowledgeImportTask task);
 Optional<KnowledgeDocument> findDocument(String id); Optional<KnowledgeDocumentVersion> findVersion(String id);
 List<KnowledgeDocument> findDocuments(); List<KnowledgeDocumentVersion> findVersions(String documentId);
 List<KnowledgeImportTask> findImportTasks(); void publish(String documentId,String versionId,String reviewerIamUserId);
 void archive(String documentId,String reviewerIamUserId);
 void completeReview(String documentId, String reviewerIamUserId, java.time.Instant nextReviewDueAt);
 void submitDraft(String documentId, String versionId, String actorIamUserId, java.time.Instant submittedAt);
 boolean deleteDraft(String documentId, String creatorIamUserId);
 boolean isFavorite(String iamUserId, String documentId);
 void saveFavorite(String iamUserId, String documentId, java.time.Instant createdAt);
 void deleteFavorite(String iamUserId, String documentId);
 List<String> findFavoriteDocumentIds(String iamUserId);
 void saveFeedback(KnowledgeFeedback feedback);
 List<KnowledgeFeedback> findFeedback(String documentId, String versionId);
}
