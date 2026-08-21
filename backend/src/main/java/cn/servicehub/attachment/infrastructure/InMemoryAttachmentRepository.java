package cn.servicehub.attachment.infrastructure;

import cn.servicehub.attachment.domain.AttachmentRepository;
import cn.servicehub.attachment.domain.TicketAttachment;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!mysql")
public class InMemoryAttachmentRepository implements AttachmentRepository {
    private final ConcurrentHashMap<String, TicketAttachment> values = new ConcurrentHashMap<>();
    public void save(TicketAttachment attachment) { if (values.putIfAbsent(attachment.id(), attachment) != null) throw new IllegalStateException("Attachment id collision"); }
    public Optional<TicketAttachment> findByIdAndTicketId(String id, String ticketId) { return Optional.ofNullable(values.get(id)).filter(value -> value.ticketId().equals(ticketId)); }
    public List<TicketAttachment> findByTicketId(String ticketId) { return values.values().stream().filter(value -> value.ticketId().equals(ticketId)).sorted(Comparator.comparing(TicketAttachment::createdAt)).toList(); }
    public long countByTicketId(String ticketId) { return values.values().stream().filter(value -> value.ticketId().equals(ticketId)).count(); }
}
