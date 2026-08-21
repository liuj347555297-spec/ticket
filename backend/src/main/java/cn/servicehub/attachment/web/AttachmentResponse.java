package cn.servicehub.attachment.web;
import cn.servicehub.attachment.domain.TicketAttachment;
import java.time.Instant;
public record AttachmentResponse(String id,String filename,String detectedMediaType,long sizeBytes,String scanStatus,Instant createdAt) { static AttachmentResponse from(TicketAttachment v) { return new AttachmentResponse(v.id(),v.originalFilename(),v.detectedMediaType(),v.sizeBytes(),v.scanStatus().name(),v.createdAt()); } }
