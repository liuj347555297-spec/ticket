package cn.servicehub.knowledge.application;

import cn.servicehub.attachment.AttachmentProperties;
import cn.servicehub.attachment.application.StoragePort;
import cn.servicehub.attachment.application.VirusScanPort;
import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.knowledge.domain.*;
import cn.servicehub.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Metadata and controlled source-file import only. No AI generation, vector indexing or automatic publication exists here. */
@Service public class KnowledgeService {
 private final KnowledgeRepository repo; private final StoragePort storage; private final VirusScanPort scanner; private final AttachmentProperties limits; private final CurrentUserProvider users; private final ObjectAuthorizationService auth; private final AuditEventPublisher audit; private final Clock clock=Clock.systemUTC();
 public KnowledgeService(KnowledgeRepository r,StoragePort s,VirusScanPort v,AttachmentProperties l,CurrentUserProvider u,ObjectAuthorizationService a,AuditEventPublisher e){repo=r;storage=s;scanner=v;limits=l;users=u;auth=a;audit=e;}
 public KnowledgeDocument importDocument(String title,String category,List<String> tags,MultipartFile file){CurrentUser actor=users.requireCurrentUser(); authorize(actor,"NEW",ObjectAction.CREATE); if(title==null||title.trim().isBlank()||title.length()>200||category==null||!category.matches("^[A-Z][A-Z0-9_-]{1,63}$")||file==null||file.isEmpty()||file.getSize()>limits.maxFileSizeBytes())throw new KnowledgeValidationException();List<String> cleanTags=cleanTags(tags);byte[] bytes;try{bytes=file.getBytes();}catch(Exception e){throw new KnowledgeValidationException();}String type=detect(bytes);if(type==null)throw new KnowledgeValidationException();String did="KDOC-"+UUID.randomUUID(),vid="KVER-"+UUID.randomUUID(),taskId="KIMP-"+UUID.randomUUID(),key="knowledge-quarantine/"+vid.toLowerCase(Locale.ROOT);storage.put(key,bytes);VirusScanPort.ScanResult scan;try{scan=scanner.scan(key,bytes);}catch(RuntimeException e){scan=new VirusScanPort.ScanResult(false,"SCANNER_UNAVAILABLE");}KnowledgePublicationStatus status=scan.clean()?KnowledgePublicationStatus.PENDING_REVIEW:KnowledgePublicationStatus.REJECTED;var now=clock.instant();KnowledgeDocument d=new KnowledgeDocument(did,title.trim(),category,cleanTags,status,vid,actor.iamUserId(),now,now);KnowledgeDocumentVersion v=new KnowledgeDocumentVersion(vid,did,1,key,type,bytes.length,status,null,now,null);KnowledgeImportTask task=new KnowledgeImportTask(taskId,did,null,scan.clean()?"COMPLETED":"REJECTED",actor.iamUserId(),scan.clean()?null:scan.detail(),now,now);try{repo.save(d,v,task);}catch(RuntimeException e){storage.delete(key);throw e;}audit(actor,"KNOWLEDGE_IMPORTED",did,Map.of("status",status.name(),"mediaType",type,"sizeBytes",String.valueOf(bytes.length)));return d;}
 public List<KnowledgeDocument> list(){CurrentUser actor=users.requireCurrentUser();authorize(actor,"collection",ObjectAction.READ);return repo.findDocuments();}
 public KnowledgeDocument get(String id){CurrentUser actor=users.requireCurrentUser();authorize(actor,id,ObjectAction.READ);return repo.findDocument(id).orElseThrow(KnowledgeNotFoundException::new);}
 public KnowledgeDocument publish(String id,String versionId){CurrentUser actor=users.requireCurrentUser();authorize(actor,id,ObjectAction.APPROVE);KnowledgeDocument document=repo.findDocument(id).orElseThrow(KnowledgeNotFoundException::new);KnowledgeDocumentVersion version=repo.findVersion(versionId).filter(v->v.documentId().equals(id)).orElseThrow(KnowledgeNotFoundException::new);if(version.status()!=KnowledgePublicationStatus.PENDING_REVIEW)throw new KnowledgeValidationException();repo.publish(id,versionId,actor.iamUserId());audit(actor,"KNOWLEDGE_PUBLISHED",id,Map.of("versionId",versionId));return repo.findDocument(id).orElseThrow(KnowledgeNotFoundException::new);}
 private void authorize(CurrentUser user,String id,ObjectAction action){auth.requireAuthorized(user,new ObjectAuthorizationRequest("knowledge-document",id,action,Map.of()));}
 private static List<String> cleanTags(List<String> values){if(values==null)return List.of();if(values.size()>20)throw new KnowledgeValidationException();return values.stream().map(v->{if(v==null||!v.matches("^#[^\\s#]{1,64}$"))throw new KnowledgeValidationException();return v;}).distinct().toList();}
 private static String detect(byte[] b){if(starts(b,"%PDF-".getBytes(StandardCharsets.US_ASCII)))return "application/pdf";String text=new String(b,StandardCharsets.UTF_8);if(text.indexOf('\uFFFD')<0&&text.chars().noneMatch(c->c==0||(c<32&&c!='\n'&&c!='\r'&&c!='\t')))return "text/plain";return null;}
 private static boolean starts(byte[] b,byte[] p){if(b.length<p.length)return false;for(int i=0;i<p.length;i++)if(b[i]!=p[i])return false;return true;}
 private void audit(CurrentUser actor,String action,String id,Map<String,String> attr){String requestId=MDC.get("requestId");audit.publish(new AuditEvent(clock.instant(),requestId==null?"system":requestId,actor.iamUserId(),action,"knowledge-document",id,attr));}
}
