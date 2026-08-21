package cn.servicehub.attachment.application;

import cn.servicehub.attachment.AttachmentProperties;
import cn.servicehub.attachment.domain.AttachmentRepository;
import cn.servicehub.attachment.domain.AttachmentScanStatus;
import cn.servicehub.attachment.domain.TicketAttachment;
import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.security.ObjectAction;
import cn.servicehub.security.ObjectAuthorizationRequest;
import cn.servicehub.security.ObjectAuthorizationService;
import cn.servicehub.ticket.application.TicketNotFoundException;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    private static final Map<String, String> DETECTED_TYPES = Map.of("pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "txt", "text/plain", "csv", "text/csv");
    private final AttachmentRepository attachments; private final TicketRepository tickets; private final StoragePort storage;
    private final VirusScanPort scanner; private final AttachmentProperties properties; private final CurrentUserProvider users;
    private final ObjectAuthorizationService authorization; private final AuditEventPublisher audit; private final Clock clock = Clock.systemUTC();
    public AttachmentService(AttachmentRepository attachments, TicketRepository tickets, StoragePort storage, VirusScanPort scanner, AttachmentProperties properties, CurrentUserProvider users, ObjectAuthorizationService authorization, AuditEventPublisher audit) { this.attachments=attachments; this.tickets=tickets; this.storage=storage; this.scanner=scanner; this.properties=properties; this.users=users; this.authorization=authorization; this.audit=audit; }
    public TicketAttachment upload(String ticketId, MultipartFile file) {
        CurrentUser actor = users.requireCurrentUser(); Ticket ticket = ticket(ticketId); authorize(actor, ticket, ObjectAction.UPLOAD_ATTACHMENT);
        if (file == null || file.isEmpty() || file.getSize() > properties.maxFileSizeBytes() || attachments.countByTicketId(ticketId) >= properties.maxFilesPerTicket()) throw new AttachmentValidationException();
        byte[] bytes; try { bytes = file.getBytes(); } catch (Exception e) { throw new AttachmentValidationException(); }
        String detected = detect(bytes); if (detected == null) throw new AttachmentValidationException();
        String safeName = safeFilename(file.getOriginalFilename()); String id = "ATT-" + UUID.randomUUID(); String key = "quarantine/" + id.toLowerCase(java.util.Locale.ROOT);
        storage.put(key, bytes); // Bytes are persisted in isolation before any scan decision.
        VirusScanPort.ScanResult scan;
        try { scan=scanner.scan(key, bytes); } catch (RuntimeException e) { scan=new VirusScanPort.ScanResult(false, "SCANNER_UNAVAILABLE"); }
        TicketAttachment value = new TicketAttachment(id,ticketId,safeName,key,detected,bytes.length,scan.clean()?AttachmentScanStatus.CLEAN:AttachmentScanStatus.REJECTED,scan.detail(),actor.iamUserId(),clock.instant());
        try { attachments.save(value); } catch (RuntimeException exception) { storage.delete(key); throw exception; }
        audit(actor,"ATTACHMENT_UPLOADED",value.id(),Map.of("ticketId",ticketId,"scanStatus",value.scanStatus().name(),"mediaType",detected,"sizeBytes",String.valueOf(bytes.length)));
        return value;
    }
    public List<TicketAttachment> list(String ticketId) { CurrentUser actor=users.requireCurrentUser(); Ticket ticket=ticket(ticketId); authorize(actor,ticket,ObjectAction.READ); return attachments.findByTicketId(ticketId); }
    public Download download(String ticketId, String attachmentId) {
        CurrentUser actor=users.requireCurrentUser(); Ticket ticket=ticket(ticketId); authorize(actor,ticket,ObjectAction.DOWNLOAD_ATTACHMENT);
        TicketAttachment attachment=attachments.findByIdAndTicketId(attachmentId,ticketId).orElseThrow(AttachmentNotFoundException::new);
        if (attachment.scanStatus()!=AttachmentScanStatus.CLEAN) throw new AttachmentValidationException();
        InputStream content=storage.open(attachment.storageKey()); audit(actor,"ATTACHMENT_DOWNLOADED",attachment.id(),Map.of("ticketId",ticketId,"scanStatus",attachment.scanStatus().name()));
        return new Download(attachment,content);
    }
    private Ticket ticket(String id) { return tickets.findById(id).orElseThrow(() -> new TicketNotFoundException(id)); }
    private void authorize(CurrentUser actor, Ticket t, ObjectAction action) { authorization.requireAuthorized(actor,new ObjectAuthorizationRequest("ticket",t.id(),action,Map.of("requesterIamUserId",t.requester().iamUserId(),"serviceCatalogItemId",t.serviceCatalogItem().id()))); }
    private static String safeFilename(String source) { if (source==null) return "attachment"; String n=source.replace('\\','/'); n=n.substring(n.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_").trim(); if (n.isBlank()||n.equals(".")||n.equals("..")) return "attachment"; return n.length()>128?n.substring(0,128):n; }
    private static String detect(byte[] b) { if (starts(b,"%PDF-".getBytes(StandardCharsets.US_ASCII))) return DETECTED_TYPES.get("pdf"); if (starts(b,new byte[]{(byte)137,80,78,71,13,10,26,10})) return DETECTED_TYPES.get("png"); if (starts(b,new byte[]{(byte)255,(byte)216,(byte)255})) return DETECTED_TYPES.get("jpg"); if (isText(b)) return new String(b,StandardCharsets.UTF_8).contains(",")?DETECTED_TYPES.get("csv"):DETECTED_TYPES.get("txt"); return null; }
    private static boolean starts(byte[] b,byte[] p) { if(b.length<p.length)return false; for(int i=0;i<p.length;i++)if(b[i]!=p[i])return false;return true; }
    private static boolean isText(byte[] b) { if(b.length==0)return false; String text=new String(b,StandardCharsets.UTF_8); if(text.indexOf('\uFFFD')>=0)return false; for(char c:text.toCharArray()) if(c==0||(c<32&&c!='\n'&&c!='\r'&&c!='\t'))return false; return true; }
    private void audit(CurrentUser actor,String action,String resourceId,Map<String,String> attributes) { String requestId=MDC.get("requestId"); audit.publish(new AuditEvent(clock.instant(),requestId==null?"system":requestId,actor.iamUserId(),action,"attachment",resourceId,attributes)); }
    public record Download(TicketAttachment attachment, InputStream content) { }
}
