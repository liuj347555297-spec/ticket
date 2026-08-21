package cn.servicehub.attachment.infrastructure;

import cn.servicehub.attachment.domain.AttachmentRepository;
import cn.servicehub.attachment.domain.AttachmentScanStatus;
import cn.servicehub.attachment.domain.TicketAttachment;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("mysql")
public class MySqlAttachmentRepository implements AttachmentRepository {
    private final JdbcTemplate jdbc; public MySqlAttachmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void save(TicketAttachment a) { jdbc.update("INSERT INTO ticket_attachment (id,ticket_id,original_filename,storage_key,detected_media_type,size_bytes,scan_status,scan_detail,uploader_iam_user_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", a.id(),a.ticketId(),a.originalFilename(),a.storageKey(),a.detectedMediaType(),a.sizeBytes(),a.scanStatus().name(),a.scanDetail(),a.uploaderIamUserId(),Timestamp.from(a.createdAt())); }
    public Optional<TicketAttachment> findByIdAndTicketId(String id,String ticketId) { return jdbc.query("SELECT * FROM ticket_attachment WHERE id=? AND ticket_id=?",(rs,row)->row(rs),id,ticketId).stream().findFirst(); }
    public List<TicketAttachment> findByTicketId(String ticketId) { return jdbc.query("SELECT * FROM ticket_attachment WHERE ticket_id=? ORDER BY created_at",(rs,row)->row(rs),ticketId); }
    public long countByTicketId(String ticketId) { return jdbc.queryForObject("SELECT COUNT(*) FROM ticket_attachment WHERE ticket_id=?",Long.class,ticketId); }
    private TicketAttachment row(java.sql.ResultSet rs) throws java.sql.SQLException { return new TicketAttachment(rs.getString("id"),rs.getString("ticket_id"),rs.getString("original_filename"),rs.getString("storage_key"),rs.getString("detected_media_type"),rs.getLong("size_bytes"),AttachmentScanStatus.valueOf(rs.getString("scan_status")),rs.getString("scan_detail"),rs.getString("uploader_iam_user_id"),rs.getTimestamp("created_at").toInstant()); }
}
