package cn.servicehub.attachment.domain;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository {
    void save(TicketAttachment attachment);
    Optional<TicketAttachment> findByIdAndTicketId(String id, String ticketId);
    List<TicketAttachment> findByTicketId(String ticketId);
    long countByTicketId(String ticketId);
}
