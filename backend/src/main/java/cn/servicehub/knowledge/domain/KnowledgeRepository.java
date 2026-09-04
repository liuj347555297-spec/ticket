package cn.servicehub.knowledge.domain;
import java.util.List;
import java.util.Optional;
public interface KnowledgeRepository {
 void save(KnowledgeDocument document,KnowledgeDocumentVersion version,KnowledgeImportTask task);
 void saveVersion(KnowledgeDocumentVersion version, KnowledgeImportTask task);
 Optional<KnowledgeDocument> findDocument(String id); Optional<KnowledgeDocumentVersion> findVersion(String id);
 List<KnowledgeDocument> findDocuments(); List<KnowledgeDocumentVersion> findVersions(String documentId);
 List<KnowledgeImportTask> findImportTasks(); void publish(String documentId,String versionId,String reviewerIamUserId);
 void archive(String documentId,String reviewerIamUserId);
 void completeReview(String documentId, String reviewerIamUserId, java.time.Instant nextReviewDueAt);
 void saveFeedback(KnowledgeFeedback feedback);
 List<KnowledgeFeedback> findFeedback(String documentId, String versionId);
}
