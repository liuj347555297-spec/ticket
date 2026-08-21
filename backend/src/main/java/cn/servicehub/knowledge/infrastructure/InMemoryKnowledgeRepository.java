package cn.servicehub.knowledge.infrastructure;
import cn.servicehub.knowledge.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
@Repository @Profile("!mysql") public class InMemoryKnowledgeRepository implements KnowledgeRepository {
 private final Map<String,KnowledgeDocument> docs=new ConcurrentHashMap<>(); private final Map<String,KnowledgeDocumentVersion> versions=new ConcurrentHashMap<>(); private final Map<String,KnowledgeImportTask> tasks=new ConcurrentHashMap<>();
 public void save(KnowledgeDocument d,KnowledgeDocumentVersion v,KnowledgeImportTask t){ if(docs.putIfAbsent(d.id(),d)!=null)throw new IllegalStateException("Knowledge document collision"); versions.put(v.id(),v);tasks.put(t.id(),t); }
 public Optional<KnowledgeDocument> findDocument(String id){return Optional.ofNullable(docs.get(id));} public Optional<KnowledgeDocumentVersion> findVersion(String id){return Optional.ofNullable(versions.get(id));} public List<KnowledgeDocument> findDocuments(){return docs.values().stream().sorted(Comparator.comparing(KnowledgeDocument::updatedAt).reversed()).toList();}
 public void publish(String did,String vid,String reviewer){ KnowledgeDocument d=docs.get(did);KnowledgeDocumentVersion v=versions.get(vid);if(d==null||v==null||!v.documentId().equals(did))throw new IllegalArgumentException(); Instant now=Instant.now(); versions.put(vid,new KnowledgeDocumentVersion(v.id(),v.documentId(),v.versionNumber(),v.attachmentStorageKey(),v.detectedMediaType(),v.sizeBytes(),KnowledgePublicationStatus.PUBLISHED,reviewer,v.createdAt(),now));docs.put(did,new KnowledgeDocument(d.id(),d.title(),d.categoryCode(),d.tags(),KnowledgePublicationStatus.PUBLISHED,vid,d.creatorIamUserId(),d.createdAt(),now)); }
}
