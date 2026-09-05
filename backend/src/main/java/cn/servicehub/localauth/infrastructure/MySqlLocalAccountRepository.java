package cn.servicehub.localauth.infrastructure;

import cn.servicehub.localauth.application.LocalAccountConflictException;
import cn.servicehub.localauth.domain.LocalAccount;
import cn.servicehub.localauth.domain.LocalAccountPage;
import cn.servicehub.localauth.domain.LocalAccountRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlLocalAccountRepository implements LocalAccountRepository {
    private static final String COLUMNS = "id,login_name,normalized_login_name,password_hash,display_name,iam_organization_id,enabled,failed_login_count,locked_until,password_changed_at,session_version,version,created_by_iam_user_id,updated_by_iam_user_id,created_at,updated_at";
    private final JdbcTemplate jdbc;
    public MySqlLocalAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<LocalAccount> findById(String id) { return one("SELECT "+COLUMNS+" FROM platform_local_account WHERE id=?", id); }
    @Override public Optional<LocalAccount> findByNormalizedLoginName(String login) { return one("SELECT "+COLUMNS+" FROM platform_local_account WHERE normalized_login_name=?", login); }
    @Override public long count() { Long value=jdbc.queryForObject("SELECT COUNT(*) FROM platform_local_account",Long.class);return value==null?0:value; }
    @Override public LocalAccountPage search(int page,int pageSize,String query,String status,Instant now) {
        StringBuilder where=new StringBuilder(" WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(query!=null&&!query.isBlank()){where.append(" AND (normalized_login_name LIKE ? ESCAPE '!' OR LOWER(display_name) LIKE ? ESCAPE '!')");String like="%"+escape(query.toLowerCase(Locale.ROOT))+"%";args.add(like);args.add(like);}
        if(status!=null&&!status.isBlank()&&!"ALL".equals(status)){switch(status){case "ACTIVE"->where.append(" AND enabled=TRUE AND (locked_until IS NULL OR locked_until<=?)");case "LOCKED"->where.append(" AND enabled=TRUE AND locked_until>?");case "DISABLED"->where.append(" AND enabled=FALSE");default->where.append(" AND 1=0");}if("ACTIVE".equals(status)||"LOCKED".equals(status))args.add(Timestamp.from(now));}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM platform_local_account"+where,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(pageSize);pageArgs.add((page-1)*pageSize);
        var items=jdbc.query("SELECT "+COLUMNS+" FROM platform_local_account"+where+" ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",(rs,n)->map(rs),pageArgs.toArray());
        return new LocalAccountPage(items,page,pageSize,total==null?0:total);
    }
    @Override public LocalAccount insert(LocalAccount a) { try {
        jdbc.update("INSERT INTO platform_local_account ("+COLUMNS+") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",a.id(),a.loginName(),a.normalizedLoginName(),a.passwordHash(),a.displayName(),a.organizationId(),a.enabled(),a.failedLoginCount(),ts(a.lockedUntil()),Timestamp.from(a.passwordChangedAt()),a.sessionVersion(),a.version(),a.createdBy(),a.updatedBy(),Timestamp.from(a.createdAt()),Timestamp.from(a.updatedAt()));return a;
    } catch(DuplicateKeyException ex){throw new LocalAccountConflictException();} }
    @Override public LocalAccount update(LocalAccount a,long expectedVersion) { try {
        int changed=jdbc.update("UPDATE platform_local_account SET login_name=?,normalized_login_name=?,password_hash=?,display_name=?,iam_organization_id=?,enabled=?,failed_login_count=?,locked_until=?,password_changed_at=?,session_version=?,version=?,updated_by_iam_user_id=?,updated_at=? WHERE id=? AND version=?",a.loginName(),a.normalizedLoginName(),a.passwordHash(),a.displayName(),a.organizationId(),a.enabled(),a.failedLoginCount(),ts(a.lockedUntil()),Timestamp.from(a.passwordChangedAt()),a.sessionVersion(),a.version(),a.updatedBy(),Timestamp.from(a.updatedAt()),a.id(),expectedVersion);if(changed!=1)throw new LocalAccountConflictException();return a;
    } catch(DuplicateKeyException ex){throw new LocalAccountConflictException();} }
    private Optional<LocalAccount> one(String sql,Object arg){return jdbc.query(sql,(rs,n)->map(rs),arg).stream().findFirst();}
    private LocalAccount map(java.sql.ResultSet rs)throws java.sql.SQLException{return new LocalAccount(rs.getString("id"),rs.getString("login_name"),rs.getString("normalized_login_name"),rs.getString("password_hash"),rs.getString("display_name"),rs.getString("iam_organization_id"),rs.getBoolean("enabled"),rs.getInt("failed_login_count"),instant(rs.getTimestamp("locked_until")),rs.getTimestamp("password_changed_at").toInstant(),rs.getLong("session_version"),rs.getLong("version"),rs.getString("created_by_iam_user_id"),rs.getString("updated_by_iam_user_id"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());}
    private static Timestamp ts(Instant value){return value==null?null:Timestamp.from(value);}private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}private static String escape(String value){return value.replace("!","!!").replace("%","!%").replace("_","!_");}
}
