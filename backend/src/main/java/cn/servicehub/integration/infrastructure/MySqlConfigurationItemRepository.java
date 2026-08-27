package cn.servicehub.integration.infrastructure;

import cn.servicehub.integration.domain.ConfigurationItem;
import cn.servicehub.integration.domain.ConfigurationItemRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlConfigurationItemRepository implements ConfigurationItemRepository {
    private final JdbcTemplate jdbc; public MySqlConfigurationItemRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public Optional<ConfigurationItem> findById(String id) { return jdbc.query("SELECT * FROM cmdb_configuration_item_projection WHERE ci_id = ? AND active = TRUE", (rs,n)->map(rs.getString("ci_id"),rs.getString("source_code"),rs.getString("ci_name"),rs.getString("ci_type"),rs.getString("ci_status"),rs.getString("organization_id")),id).stream().findFirst(); }
    @Override public List<ConfigurationItem> findByTicketId(String ticketId) { return jdbc.query("SELECT c.* FROM ticket_configuration_item t JOIN cmdb_configuration_item_projection c ON c.ci_id=t.ci_id WHERE t.ticket_id=? AND c.active=TRUE ORDER BY c.ci_name,c.ci_id",(rs,n)->map(rs.getString("ci_id"),rs.getString("source_code"),rs.getString("ci_name"),rs.getString("ci_type"),rs.getString("ci_status"),rs.getString("organization_id")),ticketId); }
    @Override public List<ConfigurationItem> findByOrganizationId(String organizationId) { return jdbc.query("SELECT * FROM cmdb_configuration_item_projection WHERE organization_id=? AND active=TRUE ORDER BY ci_name,ci_id",(rs,n)->map(rs.getString("ci_id"),rs.getString("source_code"),rs.getString("ci_name"),rs.getString("ci_type"),rs.getString("ci_status"),rs.getString("organization_id")),organizationId); }
    @Override public void replaceTicketAssociations(String ticketId,List<String> ids) { jdbc.update("DELETE FROM ticket_configuration_item WHERE ticket_id=?",ticketId); for(String id:ids) jdbc.update("INSERT INTO ticket_configuration_item(ticket_id,ci_id,linked_at) VALUES(?,?,UTC_TIMESTAMP(6))",ticketId,id); }
    private static ConfigurationItem map(String id,String source,String name,String type,String status,String org) { return new ConfigurationItem(id,source,name,type,status,org); }
}
