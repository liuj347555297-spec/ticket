package cn.servicehub.knowledge.domain;
import java.util.List;
import java.util.Optional;
public interface KnowledgeRepository { void save(KnowledgeDocument document,KnowledgeDocumentVersion version,KnowledgeImportTask task); Optional<KnowledgeDocument> findDocument(String id); Optional<KnowledgeDocumentVersion> findVersion(String id); List<KnowledgeDocument> findDocuments(); void publish(String documentId,String versionId,String reviewerIamUserId); }
