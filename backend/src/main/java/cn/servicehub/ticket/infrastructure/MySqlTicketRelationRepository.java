package cn.servicehub.ticket.infrastructure;

import cn.servicehub.ticket.domain.TicketRelation;
import cn.servicehub.ticket.domain.TicketRelationRepository;
import cn.servicehub.ticket.domain.TicketRelationType;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlTicketRelationRepository implements TicketRelationRepository {
    private final JdbcTemplate jdbc;
    public MySqlTicketRelationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public boolean createIfAbsent(TicketRelation relation) {
        return jdbc.update("""
            INSERT IGNORE INTO ticket_relation
              (ticket_id, related_ticket_id, relation_type, created_by_iam_user_id, created_at)
            VALUES (?, ?, ?, ?, ?)
            """, relation.ticketId(), relation.relatedTicketId(), relation.relationType().name(),
            relation.createdByIamUserId(), Timestamp.from(relation.createdAt())) == 1;
    }

    @Override public List<TicketRelation> findByTicketId(String ticketId) {
        return jdbc.query("""
            SELECT ticket_id, related_ticket_id, relation_type, created_by_iam_user_id, created_at
            FROM ticket_relation
            WHERE ticket_id = ? OR related_ticket_id = ?
            ORDER BY created_at DESC
            """, (rs, row) -> new TicketRelation(rs.getString("ticket_id"), rs.getString("related_ticket_id"),
                TicketRelationType.valueOf(rs.getString("relation_type")), rs.getString("created_by_iam_user_id"),
                rs.getTimestamp("created_at").toInstant()), ticketId, ticketId);
    }
}
