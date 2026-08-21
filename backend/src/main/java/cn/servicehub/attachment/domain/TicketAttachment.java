package cn.servicehub.attachment.domain;

import java.time.Instant;

/** Metadata only. Bytes live behind a non-public StoragePort and filenames are never storage paths. */
public record TicketAttachment(String id, String ticketId, String originalFilename, String storageKey,
                               String detectedMediaType, long sizeBytes, AttachmentScanStatus scanStatus,
                               String scanDetail, String uploaderIamUserId, Instant createdAt) { }
