package cn.servicehub.ticketdraft;

import cn.servicehub.ticketdraft.TicketDraftModels.Draft;
import cn.servicehub.workflow.application.WorkflowConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import java.sql.Timestamp;
import java.util.*;

@Repository @Profile("mysql")
public class MySqlTicketDraftRepository implements TicketDraftRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public MySqlTicketDraftRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private RowMapper<Draft> mapper(){return (rs,n)->{try{return new Draft(rs.getString("id"),rs.getString("owner_id"),rs.getString("title"),json.readTree(rs.getString("payload_json")),rs.getLong("version"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());}catch(Exception e){throw new java.sql.SQLException("Draft cannot be read",e);}};}
    public Optional<Draft> find(String id,String owner){return jdbc.query("SELECT * FROM personal_ticket_draft WHERE id=? AND owner_id=?",mapper(),id,owner).stream().findFirst();}
    public List<Draft> list(String owner,int offset,int limit){return jdbc.query("SELECT * FROM personal_ticket_draft WHERE owner_id=? ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",mapper(),owner,limit,offset);}
    public long count(String owner){return jdbc.queryForObject("SELECT COUNT(*) FROM personal_ticket_draft WHERE owner_id=?",Long.class,owner);}
    public Draft save(Draft d,long expected){
        if(expected==0){try{jdbc.update("INSERT INTO personal_ticket_draft(id,owner_id,title,payload_json,version,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",d.id(),d.ownerId(),d.title(),d.payload().toString(),d.version(),Timestamp.from(d.createdAt()),Timestamp.from(d.updatedAt()));}catch(DuplicateKeyException e){throw new WorkflowConflictException();}}
        else if(jdbc.update("UPDATE personal_ticket_draft SET title=?,payload_json=?,version=?,updated_at=? WHERE id=? AND owner_id=? AND version=?",d.title(),d.payload().toString(),d.version(),Timestamp.from(d.updatedAt()),d.id(),d.ownerId(),expected)!=1)throw new WorkflowConflictException();
        return d;
    }
    public boolean delete(String id,String owner,long version){return jdbc.update("DELETE FROM personal_ticket_draft WHERE id=? AND owner_id=? AND version=?",id,owner,version)==1;}
}
