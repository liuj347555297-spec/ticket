package cn.servicehub.workflow.processing;

import cn.servicehub.workflow.application.WorkflowConflictException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlProcessingDetailsRepository implements ProcessingDetailsRepository {
    private final JdbcTemplate jdbc;
    public MySqlProcessingDetailsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<ProcessingDetails> findByTicketId(String ticketId) {
        return jdbc.query("SELECT * FROM ticket_processing_details WHERE ticket_id=?", (rs, n) -> new ProcessingDetails(
            rs.getString("ticket_id"), rs.getString("event_source"), rs.getString("proposing_organization"),
            nullableBoolean(rs, "on_site_support_required"), rs.getString("cause_category"),
            rs.getString("processing_description"), rs.getString("resolution_description"),
            nullableBoolean(rs, "third_party_handled"), rs.getString("current_progress"), rs.getLong("version"),
            rs.getString("updated_by_iam_user_id"), rs.getTimestamp("updated_at").toInstant()), ticketId).stream().findFirst();
    }

    @Override public ProcessingDetails save(ProcessingDetails value, long expectedVersion) {
        int changed = jdbc.update("UPDATE ticket_processing_details SET event_source=?,proposing_organization=?,on_site_support_required=?,cause_category=?,processing_description=?,resolution_description=?,third_party_handled=?,current_progress=?,version=version+1,updated_by_iam_user_id=?,updated_at=? WHERE ticket_id=? AND version=?",
            value.eventSource(), value.proposingOrganization(), value.onSiteSupportRequired(), value.causeCategory(),
            value.processingDescription(), value.resolutionDescription(), value.thirdPartyHandled(), value.currentProgress(),
            value.updatedByIamUserId(), Timestamp.from(value.updatedAt()), value.ticketId(), expectedVersion);
        if (changed == 0 && expectedVersion == 0) {
            try {
                jdbc.update("INSERT INTO ticket_processing_details(ticket_id,event_source,proposing_organization,on_site_support_required,cause_category,processing_description,resolution_description,third_party_handled,current_progress,version,updated_by_iam_user_id,updated_at) VALUES(?,?,?,?,?,?,?,?,?,1,?,?)",
                    value.ticketId(), value.eventSource(), value.proposingOrganization(), value.onSiteSupportRequired(), value.causeCategory(),
                    value.processingDescription(), value.resolutionDescription(), value.thirdPartyHandled(), value.currentProgress(),
                    value.updatedByIamUserId(), Timestamp.from(value.updatedAt()));
            } catch (DuplicateKeyException concurrentWrite) { throw new WorkflowConflictException(); }
        } else if (changed == 0) throw new WorkflowConflictException();
        return findByTicketId(value.ticketId()).orElseThrow(WorkflowConflictException::new);
    }

    private Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
