package cn.servicehub.operations.infrastructure;

import cn.servicehub.operations.domain.ReportExportTask;
import cn.servicehub.operations.domain.ReportExportTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!mysql")
public class InMemoryReportExportTaskRepository implements ReportExportTaskRepository {
 private final ConcurrentHashMap<String,ReportExportTask> items=new ConcurrentHashMap<>();
 public void create(ReportExportTask t){if(items.putIfAbsent(t.id(),t)!=null)throw new IllegalStateException("Export task conflict");}
 public Optional<ReportExportTask> findById(String id){return Optional.ofNullable(items.get(id));}
 public List<ReportExportTask> pending(int n){return items.values().stream().filter(t->t.status()==ReportExportTask.Status.PENDING).sorted(java.util.Comparator.comparing(ReportExportTask::createdAt)).limit(n).toList();}
 public boolean claim(String id,long expected){return items.replace(id,items.get(id),transition(items.get(id),expected,ReportExportTask.Status.RUNNING,null,null,null,null));}
 public void complete(String id,long expected,byte[] data,String sha,String name){items.compute(id,(k,t)->transition(t,expected,ReportExportTask.Status.COMPLETED,data,sha,name,null));}
 public void fail(String id,long expected,String error){items.compute(id,(k,t)->transition(t,expected,ReportExportTask.Status.FAILED,null,null,null,error));}
 public boolean recordDownload(String id,long expected){return items.replace(id,items.get(id),new ReportExportTask(items.get(id).id(),items.get(id).requesterIamUserId(),items.get(id).reportType(),items.get(id).from(),items.get(id).to(),items.get(id).organizationScope(),items.get(id).unrestrictedScope(),items.get(id).status(),items.get(id).resultContent(),items.get(id).sha256(),items.get(id).fileName(),items.get(id).errorCode(),items.get(id).createdAt(),items.get(id).startedAt(),items.get(id).completedAt(),items.get(id).downloadCount()+1,expected+1));}
 private ReportExportTask transition(ReportExportTask t,long expected,ReportExportTask.Status status,byte[] data,String sha,String name,String error){if(t==null||t.version()!=expected)throw new IllegalStateException("Export task version conflict");Instant now=Instant.now();return new ReportExportTask(t.id(),t.requesterIamUserId(),t.reportType(),t.from(),t.to(),t.organizationScope(),t.unrestrictedScope(),status,data==null?t.resultContent():data,sha==null?t.sha256():sha,name==null?t.fileName():name,error,t.createdAt(),status==ReportExportTask.Status.RUNNING?now:t.startedAt(),status==ReportExportTask.Status.COMPLETED||status==ReportExportTask.Status.FAILED?now:t.completedAt(),t.downloadCount(),expected+1);}
}
