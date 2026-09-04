package cn.servicehub.workflow.team;

import cn.servicehub.workflow.application.WorkflowConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("mysql")
public class MySqlSupportQueueRepository implements SupportQueueRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlSupportQueueRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public List<SupportQueue> findAll(){return jdbc.queryForList("SELECT queue_code FROM support_queue ORDER BY queue_code",String.class).stream().map(this::required).toList();}
    public Optional<SupportQueue> findByCode(String code){return jdbc.queryForList("SELECT queue_code FROM support_queue WHERE queue_code=?",String.class,code).stream().findFirst().map(this::required);}
    public SupportQueue save(SupportQueue v,long expected,String actor){
        Timestamp from=Timestamp.from(v.effectiveFrom()),until=ts(v.effectiveUntil());
        if(expected==0){
            if(jdbc.update("INSERT INTO support_team(team_code,team_name,owning_organization_id,status,version,created_by_iam_user_id,updated_by_iam_user_id,created_at,updated_at) SELECT ?,?,?,?,1,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM support_team WHERE team_code=?)",v.code(),v.name(),v.owningOrganizationId(),v.status().name(),actor,actor,v.code())!=1)throw new WorkflowConflictException();
            jdbc.update("INSERT INTO support_queue(queue_code,team_code,queue_name,shared_claim_enabled,capacity_limit,effective_from,effective_until,status,version,updated_by_iam_user_id,updated_at) VALUES(?,?,?,?,?,?,?,?,1,?,UTC_TIMESTAMP(6))",v.code(),v.code(),v.name(),v.sharedClaimEnabled(),v.capacityLimit(),from,until,v.status().name(),actor);
        }else{
            if(jdbc.update("UPDATE support_team SET team_name=?,owning_organization_id=?,status=?,version=version+1,updated_by_iam_user_id=?,updated_at=UTC_TIMESTAMP(6) WHERE team_code=? AND version=?",v.name(),v.owningOrganizationId(),v.status().name(),actor,v.code(),expected)!=1)throw new WorkflowConflictException();
            if(jdbc.update("UPDATE support_queue SET queue_name=?,shared_claim_enabled=?,capacity_limit=?,effective_from=?,effective_until=?,status=?,version=version+1,updated_by_iam_user_id=?,updated_at=UTC_TIMESTAMP(6) WHERE queue_code=? AND version=?",v.name(),v.sharedClaimEnabled(),v.capacityLimit(),from,until,v.status().name(),actor,v.code(),expected)!=1)throw new WorkflowConflictException();
        }
        jdbc.update("UPDATE support_team_member SET active=FALSE,version=version+1,updated_by_iam_user_id=?,updated_at=UTC_TIMESTAMP(6) WHERE team_code=? AND active=TRUE",actor,v.code());
        for(SupportQueueMember m:v.members())jdbc.update("INSERT INTO support_team_member(team_code,iam_user_id,member_role,effective_from,effective_until,active,version,updated_by_iam_user_id,updated_at) VALUES(?,?,?,?,?,TRUE,1,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE effective_until=VALUES(effective_until),active=TRUE,version=version+1,updated_by_iam_user_id=VALUES(updated_by_iam_user_id),updated_at=UTC_TIMESTAMP(6)",v.code(),m.iamUserId(),m.role().name(),Timestamp.from(m.effectiveFrom()),ts(m.effectiveUntil()),actor);
        jdbc.update("UPDATE support_queue_scope SET active=FALSE,version=version+1,updated_by_iam_user_id=?,updated_at=UTC_TIMESTAMP(6) WHERE queue_code=? AND active=TRUE",actor,v.code());
        Set<SupportQueueScope> all=new LinkedHashSet<>(v.scopes());v.serviceCatalogItemIds().forEach(id->all.add(new SupportQueueScope(SupportQueueScopeType.SERVICE_CATALOG,id)));
        for(SupportQueueScope s:all)jdbc.update("INSERT INTO support_queue_scope(queue_code,scope_type,scope_id,active,version,updated_by_iam_user_id,updated_at) VALUES(?,?,?,TRUE,1,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE active=TRUE,version=version+1,updated_by_iam_user_id=VALUES(updated_by_iam_user_id),updated_at=UTC_TIMESTAMP(6)",v.code(),s.scopeType().name(),s.scopeId(),actor);
        return required(v.code());
    }
    public void saveRoutingSnapshot(WorkflowQueueRoutingSnapshot s){try{jdbc.update("INSERT INTO ticket_workflow_queue_routing_snapshot(id,ticket_id,workflow_task_id,node_key,queue_code,assignment_mode,policy_version,queue_version,queue_scope_digest,candidate_iam_user_ids_json,ticket_context_digest,captured_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",s.id(),s.ticketId(),s.workflowTaskId(),s.nodeKey(),s.queueCode(),s.assignment().mode(),s.assignment().policyVersion(),s.queueVersion(),s.queueScopeDigest(),json.writeValueAsString(s.candidateIamUserIds()),s.ticketContextDigest(),Timestamp.from(s.capturedAt()));}catch(Exception e){throw new IllegalStateException("Queue routing snapshot cannot be persisted",e);}}
    public List<WorkflowQueueRoutingSnapshot> findRoutingSnapshots(String ticketId){return jdbc.query("SELECT * FROM ticket_workflow_queue_routing_snapshot WHERE ticket_id=? ORDER BY captured_at,id",(rs,n)->{try{return new WorkflowQueueRoutingSnapshot(rs.getString("id"),rs.getString("ticket_id"),rs.getString("workflow_task_id"),rs.getString("node_key"),rs.getString("queue_code"),new WorkflowQueueRoutingSnapshot.NodeAssignmentEvidence(rs.getString("assignment_mode"),rs.getLong("policy_version")),rs.getLong("queue_version"),rs.getString("queue_scope_digest"),json.readValue(rs.getString("candidate_iam_user_ids_json"),new com.fasterxml.jackson.core.type.TypeReference<Set<String>>(){}),rs.getString("ticket_context_digest"),rs.getTimestamp("captured_at").toInstant());}catch(Exception e){throw new java.sql.SQLException(e);}},ticketId);}
    private SupportQueue required(String code){return jdbc.query("SELECT q.*,t.owning_organization_id FROM support_queue q JOIN support_team t ON t.team_code=q.team_code WHERE q.queue_code=?",(rs,n)->new SupportQueue(code,rs.getString("queue_name"),rs.getString("owning_organization_id"),catalogs(code),scopes(code),members(code),rs.getBoolean("shared_claim_enabled"),(Integer)rs.getObject("capacity_limit"),rs.getTimestamp("effective_from").toInstant(),instant(rs.getTimestamp("effective_until")),SupportQueueStatus.valueOf(rs.getString("status")),rs.getLong("version")),code).stream().findFirst().orElseThrow();}
    private Set<String> catalogs(String code){return Set.copyOf(jdbc.queryForList("SELECT scope_id FROM support_queue_scope WHERE queue_code=? AND scope_type='SERVICE_CATALOG' AND active=TRUE ORDER BY scope_id",String.class,code));}
    private Set<SupportQueueScope> scopes(String code){return Set.copyOf(jdbc.query("SELECT scope_type,scope_id FROM support_queue_scope WHERE queue_code=? AND active=TRUE ORDER BY scope_type,scope_id",(rs,n)->new SupportQueueScope(SupportQueueScopeType.valueOf(rs.getString(1)),rs.getString(2)),code));}
    private List<SupportQueueMember> members(String code){return jdbc.query("SELECT iam_user_id,member_role,effective_from,effective_until FROM support_team_member WHERE team_code=? AND active=TRUE ORDER BY iam_user_id,member_role",(rs,n)->new SupportQueueMember(rs.getString(1),SupportQueueMemberRole.valueOf(rs.getString(2)),rs.getTimestamp(3).toInstant(),instant(rs.getTimestamp(4))),code);}
    private static Timestamp ts(java.time.Instant v){return v==null?null:Timestamp.from(v);}private static java.time.Instant instant(Timestamp v){return v==null?null:v.toInstant();}
}
