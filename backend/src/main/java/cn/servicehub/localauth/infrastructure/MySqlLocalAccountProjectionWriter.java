package cn.servicehub.localauth.infrastructure;

import cn.servicehub.localauth.domain.LocalAccountProjectionWriter;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlLocalAccountProjectionWriter implements LocalAccountProjectionWriter {
    private final JdbcTemplate jdbc;
    public MySqlLocalAccountProjectionWriter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean activeOrganizationExists(String id){Long count=jdbc.queryForObject("SELECT COUNT(*) FROM iam_organization_projection WHERE iam_organization_id=? AND active=TRUE",Long.class,id);return count!=null&&count==1;}
    @Override public void ensureLocalOrganization(String id,String name,Instant at){Timestamp now=Timestamp.from(at);jdbc.update("INSERT INTO iam_organization_projection (iam_organization_id,organization_code,organization_name,parent_iam_organization_id,organization_path,active,source_system,source_version,synced_at) VALUES (?,?,?,NULL,?,TRUE,'LOCAL_ACCOUNT','1',?) ON DUPLICATE KEY UPDATE iam_organization_id=iam_organization_id",id,id,name,"/"+id,now);}
    @Override public void upsert(String id,String login,String display,String org,boolean active,long version,Instant at){Timestamp now=Timestamp.from(at);String sourceVersion=Long.toString(version);
        jdbc.update("INSERT INTO iam_user_projection (iam_user_id,login_name,display_name,active,source_system,source_version,synced_at) VALUES (?,?,?,?, 'LOCAL_ACCOUNT',?,?) ON DUPLICATE KEY UPDATE login_name=VALUES(login_name),display_name=VALUES(display_name),active=VALUES(active),source_system='LOCAL_ACCOUNT',source_version=VALUES(source_version),synced_at=VALUES(synced_at)",id,login,display,active,sourceVersion,now);
        jdbc.update("INSERT INTO iam_user_organization_position_projection (iam_user_id,iam_organization_id,iam_position_id,position_name,is_primary,active,source_system,source_version,synced_at) VALUES (?,?,'LOCAL-ACCOUNT','本地账号',TRUE,?, 'LOCAL_ACCOUNT',?,?) ON DUPLICATE KEY UPDATE iam_organization_id=VALUES(iam_organization_id),position_name='本地账号',is_primary=TRUE,active=VALUES(active),source_system='LOCAL_ACCOUNT',source_version=VALUES(source_version),synced_at=VALUES(synced_at)",id,org,active,sourceVersion,now);
    }
}
