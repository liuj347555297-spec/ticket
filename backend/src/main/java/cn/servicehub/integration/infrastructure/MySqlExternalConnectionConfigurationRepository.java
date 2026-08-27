package cn.servicehub.integration.infrastructure;

import cn.servicehub.integration.domain.ExternalConnectionConfiguration;
import cn.servicehub.integration.domain.ExternalConnectionConfigurationRepository;
import cn.servicehub.integration.domain.ExternalSystemType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlExternalConnectionConfigurationRepository implements ExternalConnectionConfigurationRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlExternalConnectionConfigurationRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    @Override public List<ExternalConnectionConfiguration> findAll() { return jdbc.query("SELECT * FROM external_connection_configuration ORDER BY connection_code", (rs,n) -> map(rs.getString("connection_code"), rs.getString("display_name"), rs.getString("system_type"), rs.getString("trusted_base_url"), rs.getString("secret_ref"), rs.getBoolean("enabled"), rs.getInt("timeout_ms"), rs.getInt("rate_limit_per_minute"), rs.getString("allowed_callback_source_ips"), rs.getTimestamp("updated_at").toInstant())); }
    @Override public Optional<ExternalConnectionConfiguration> findByCode(String code) { return jdbc.query("SELECT * FROM external_connection_configuration WHERE connection_code = ?", (rs,n) -> map(rs.getString("connection_code"), rs.getString("display_name"), rs.getString("system_type"), rs.getString("trusted_base_url"), rs.getString("secret_ref"), rs.getBoolean("enabled"), rs.getInt("timeout_ms"), rs.getInt("rate_limit_per_minute"), rs.getString("allowed_callback_source_ips"), rs.getTimestamp("updated_at").toInstant()), code).stream().findFirst(); }
    private ExternalConnectionConfiguration map(String code,String name,String type,String url,String secret,boolean enabled,int timeout,int limit,String ips,java.time.Instant updated) { try { return new ExternalConnectionConfiguration(code,name,ExternalSystemType.valueOf(type),url,secret,enabled,timeout,limit,json.readValue(ips,new TypeReference<List<String>>(){}),updated); } catch(Exception e) { throw new IllegalStateException("Invalid external connection metadata",e); } }
}
