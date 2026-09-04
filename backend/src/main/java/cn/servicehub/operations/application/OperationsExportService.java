package cn.servicehub.operations.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.operations.domain.DailyTicketKpiRow;
import cn.servicehub.operations.domain.OperationsReportRepository;
import cn.servicehub.operations.domain.ReportExportTask;
import cn.servicehub.operations.domain.ReportExportTaskRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-side asynchronous CSV export. There is intentionally no synchronous report-download endpoint. */
@Service
public class OperationsExportService {
 private static final Set<String> ROLES=Set.of("ROLE_SERVICE_MANAGER","ROLE_PLATFORM_ADMIN","ROLE_AUDITOR","ROLE_FIRST_LINE_SUPPORT","ROLE_SECOND_LINE_SUPPORT");
 private final ReportExportTaskRepository tasks;private final OperationsReportRepository reports;private final CurrentUserProvider users;private final OperationsAuthorizationScopeResolver scopes;private final AuditEventPublisher audit;
 public OperationsExportService(ReportExportTaskRepository t,OperationsReportRepository r,CurrentUserProvider u,OperationsAuthorizationScopeResolver s,AuditEventPublisher a){tasks=t;reports=r;users=u;scopes=s;audit=a;}
 @Transactional public ReportExportTask request(LocalDate from,LocalDate to){validateRange(from,to);CurrentUser user=requireRole();Set<String> organizations=scopes.organizations(user);if(organizations.isEmpty())throw new AccessDeniedException("Explicit operational organization scope is required");ReportExportTask task=new ReportExportTask(UUID.randomUUID().toString(),user.iamUserId(),"DAILY_TICKET_KPI",from,to,organizations,false,ReportExportTask.Status.PENDING,null,null,null,null,Instant.now(),null,null,0,0);tasks.create(task);audit(user,"OPERATIONS_EXPORT_REQUESTED",task.id(),"PENDING");return task;}
 public ReportExportTask get(String id){ReportExportTask t=tasks.findById(id).orElseThrow(()->new IllegalArgumentException("Export task not found"));CurrentUser u=requireRole();requireCurrentTaskScope(u,t);return withoutContent(t);}
 @Transactional public ExportContent download(String id){ReportExportTask t=tasks.findById(id).orElseThrow(()->new IllegalArgumentException("Export task not found"));CurrentUser u=requireRole();requireCurrentTaskScope(u,t);if(t.status()!=ReportExportTask.Status.COMPLETED||t.resultContent()==null)throw new IllegalStateException("Export is not ready");if(!tasks.recordDownload(id,t.version()))throw new IllegalStateException("Export task changed; retry metadata first");audit(u,"OPERATIONS_EXPORT_DOWNLOADED",id,"COMPLETED");return new ExportContent(t.fileName(),t.resultContent(),t.sha256());}
 public void processPending(){for(ReportExportTask pending:tasks.pending(20)){if(!tasks.claim(pending.id(),pending.version()))continue;ReportExportTask running=tasks.findById(pending.id()).orElse(null);if(running==null)continue;try{byte[] bytes=csv(running).getBytes(StandardCharsets.UTF_8);tasks.complete(running.id(),running.version(),bytes,sha(bytes),"运营日报-"+running.from()+"-"+running.to()+".csv");audit.publish(new AuditEvent(Instant.now(),"system","system","OPERATIONS_EXPORT_COMPLETED","operations-export",running.id(),java.util.Map.of("rows",String.valueOf(bytes.length))));}catch(RuntimeException e){tasks.fail(running.id(),running.version(),"GENERATION_FAILED");audit.publish(new AuditEvent(Instant.now(),"system","system","OPERATIONS_EXPORT_FAILED","operations-export",running.id(),java.util.Map.of("code","GENERATION_FAILED")));}}}
 private String csv(ReportExportTask task){StringBuilder out=new StringBuilder("\uFEFF统计日期,申请组织ID,工单状态,工单量,在办量,响应样本数,平均响应分钟,解决样本数,平均解决分钟,临近违约,已违约\n");for(DailyTicketKpiRow r:reports.findDaily(task.from(),task.to(),task.organizationScope(),task.unrestrictedScope()))out.append(r.businessDate()).append(',').append(safe(r.organizationId())).append(',').append(r.status()).append(',').append(r.volume()).append(',').append(r.openVolume()).append(',').append(r.responseSamples()).append(',').append(avg(r.responseSecondsSum(),r.responseSamples())).append(',').append(r.resolutionSamples()).append(',').append(avg(r.resolutionSecondsSum(),r.resolutionSamples())).append(',').append(r.atRiskVolume()).append(',').append(r.breachedVolume()).append('\n');return out.toString();}
 private void requireCurrentTaskScope(CurrentUser u,ReportExportTask t){if(!u.iamUserId().equals(t.requesterIamUserId()))throw new AccessDeniedException("Export task is not owned by current user");Set<String> current=scopes.organizations(u);if(t.unrestrictedScope()||t.organizationScope().isEmpty()||!current.containsAll(t.organizationScope()))throw new AccessDeniedException("Export task scope is no longer authorized");}
 private CurrentUser requireRole(){CurrentUser u=users.requireCurrentUser();if(u.authorities().stream().noneMatch(ROLES::contains))throw new AccessDeniedException("Operational export is not authorized");return u;}
 private void validateRange(LocalDate f,LocalDate t){if(f==null||t==null||t.isBefore(f)||f.plusDays(30).isBefore(t))throw new IllegalArgumentException("Report date range must be between 1 and 31 days");}
 private void audit(CurrentUser u,String action,String id,String state){audit.publish(new AuditEvent(Instant.now(),"system",u.iamUserId(),action,"operations-export",id,java.util.Map.of("state",state)));}
 private static String safe(String s){return s==null?"":'\''+s.replace("\"","\"\"")+'\'';}
 private static String avg(long seconds,long samples){return samples==0?"":String.format(java.util.Locale.ROOT,"%.2f",seconds/(samples*60.0));}
 private static String sha(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
 private static ReportExportTask withoutContent(ReportExportTask t){return new ReportExportTask(t.id(),t.requesterIamUserId(),t.reportType(),t.from(),t.to(),t.organizationScope(),t.unrestrictedScope(),t.status(),null,t.sha256(),t.fileName(),t.errorCode(),t.createdAt(),t.startedAt(),t.completedAt(),t.downloadCount(),t.version());}
 public record ExportContent(String fileName,byte[] bytes,String sha256){}
}
