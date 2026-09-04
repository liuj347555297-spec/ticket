package cn.servicehub.designer;

import cn.servicehub.designer.StudioModels.Draft;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlStudioDraftRepository implements StudioDraftRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlStudioDraftRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public List<Draft> list() { return jdbc.query("SELECT payload_json FROM design_studio_draft ORDER BY updated_at DESC,id", (rs,n)->decode(rs.getString(1))); }
    public Optional<Draft> find(String id) { return jdbc.query("SELECT payload_json FROM design_studio_draft WHERE id=?", (rs,n)->decode(rs.getString(1)),id).stream().findFirst(); }
    public Draft insert(Draft d) {
        try { jdbc.update("INSERT INTO design_studio_draft (id,organization_id,name,version,execution_mode,payload_json,updated_at) VALUES (?,?,?,?,?,?,?)",d.id(),d.organizationId(),d.name(),d.version(),d.executionMode(),encode(d),Timestamp.from(d.updatedAt())); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new StudioConflictException(); }
        return d;
    }
    public Draft update(Draft d,long expectedVersion) {
        int changed=jdbc.update("UPDATE design_studio_draft SET name=?,version=?,payload_json=?,updated_at=? WHERE id=? AND organization_id=? AND version=?",d.name(),d.version(),encode(d),Timestamp.from(d.updatedAt()),d.id(),d.organizationId(),expectedVersion);
        if(changed!=1)throw new StudioConflictException(); return d;
    }
    private String encode(Draft d) { try { return json.writeValueAsString(d); } catch(Exception e) { throw new IllegalStateException("Cannot serialize design metadata",e); } }
    private Draft decode(String s) { try { return json.readValue(s,Draft.class); } catch(Exception e) { throw new IllegalStateException("Invalid stored design metadata",e); } }
}
