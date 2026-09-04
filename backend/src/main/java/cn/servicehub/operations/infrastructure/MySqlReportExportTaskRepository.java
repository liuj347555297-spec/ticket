package cn.servicehub.operations.infrastructure;

import cn.servicehub.operations.domain.ReportExportTask;
import cn.servicehub.operations.domain.ReportExportTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("mysql")
public class MySqlReportExportTaskRepository implements ReportExportTaskRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper json;public MySqlReportExportTaskRepository(JdbcTemplate j,ObjectMapper o){jdbc=j;json=o;}
 public void create(ReportExportTask t){try{jdbc.update("INSERT INTO operation_report_export_task (id,requester_iam_user_id,report_type,date_from,date_to,organization_scope_json,unrestricted_scope,status,created_at,download_count,version) VALUES (?,?,?,?,?,?,?,?,?,?,?)",t.id(),t.requesterIamUserId(),t.reportType(),Date.valueOf(t.from()),Date.valueOf(t.to()),json.writeValueAsString(t.organizationScope()),t.unrestrictedScope(),t.status().name(),Timestamp.from(t.createdAt()),0,t.version());}catch(Exception e){throw new IllegalStateException("Cannot create export task",e);}}
 public Optional<ReportExportTask> findById(String id){return jdbc.query("SELECT * FROM operation_report_export_task WHERE id=?",(r,n)->map(r),id).stream().findFirst();}
 public List<ReportExportTask> pending(int limit){return jdbc.query("SELECT * FROM operation_report_export_task WHERE status='PENDING' ORDER BY created_at ASC LIMIT ?",(r,n)->map(r),Math.max(1,Math.min(100,limit)));}
 public boolean claim(String id,long v){return jdbc.update("UPDATE operation_report_export_task SET status='RUNNING',started_at=?,version=version+1 WHERE id=? AND status='PENDING' AND version=?",Timestamp.from(Instant.now()),id,v)==1;}
 public void complete(String id,long v,byte[] c,String s,String n){if(jdbc.update("UPDATE operation_report_export_task SET status='COMPLETED',result_content=?,result_sha256=?,result_file_name=?,completed_at=?,version=version+1 WHERE id=? AND status='RUNNING' AND version=?",c,s,n,Timestamp.from(Instant.now()),id,v)!=1)throw new IllegalStateException("Export task version conflict");}
 public void fail(String id,long v,String e){if(jdbc.update("UPDATE operation_report_export_task SET status='FAILED',error_code=?,completed_at=?,version=version+1 WHERE id=? AND status='RUNNING' AND version=?",e,Timestamp.from(Instant.now()),id,v)!=1)throw new IllegalStateException("Export task version conflict");}
 public boolean recordDownload(String id,long v){return jdbc.update("UPDATE operation_report_export_task SET download_count=download_count+1,version=version+1 WHERE id=? AND status='COMPLETED' AND version=?",id,v)==1;}
 private ReportExportTask map(java.sql.ResultSet r)throws java.sql.SQLException{try{return new ReportExportTask(r.getString("id"),r.getString("requester_iam_user_id"),r.getString("report_type"),r.getDate("date_from").toLocalDate(),r.getDate("date_to").toLocalDate(),new java.util.HashSet<>(json.readValue(r.getString("organization_scope_json"),new TypeReference<List<String>>(){})),r.getBoolean("unrestricted_scope"),ReportExportTask.Status.valueOf(r.getString("status")),r.getBytes("result_content"),r.getString("result_sha256"),r.getString("result_file_name"),r.getString("error_code"),r.getTimestamp("created_at").toInstant(),instant(r,"started_at"),instant(r,"completed_at"),r.getInt("download_count"),r.getLong("version"));}catch(Exception e){throw new java.sql.SQLException("Invalid export task",e);}}
 private Instant instant(java.sql.ResultSet r,String c)throws java.sql.SQLException{Timestamp t=r.getTimestamp(c);return t==null?null:t.toInstant();}
}
