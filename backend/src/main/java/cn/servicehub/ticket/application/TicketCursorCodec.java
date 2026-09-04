package cn.servicehub.ticket.application;

import cn.servicehub.security.CurrentUser;
import cn.servicehub.ticket.domain.TicketQueue;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** HMAC signed, subject/scope/filter-bound cursor. No payload field is trusted before signature verification. */
@Component
public class TicketCursorCodec {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper json; private final byte[] key; private final TicketPaginationProperties properties; private final Clock clock;
    @Autowired
    public TicketCursorCodec(ObjectMapper json, TicketPaginationProperties properties) {
        this(json, properties, Clock.systemUTC());
    }
    TicketCursorCodec(ObjectMapper json, TicketPaginationProperties properties, Clock clock) {
        this.json = json; this.properties = properties; this.clock = clock;
        this.key = properties.cursorSigningKey().getBytes(StandardCharsets.UTF_8);
    }

    public String filterDigest(TicketStatus status, TicketType type, TicketPriority priority, String serviceCatalogItemId,
                               String requesterOrganizationId, Instant createdFrom, Instant createdTo,
                               TicketQueue queue,String teamQueueCode, String keyword, int pageSize) {
        return digest((status == null ? "" : status.name()) + "\n" + (type == null ? "" : type.name()) + "\n"
            + (priority == null ? "" : priority.name()) + "\n" + normalizeId(serviceCatalogItemId) + "\n"
            + normalizeId(requesterOrganizationId) + "\n" + (createdFrom == null ? "" : createdFrom.toString()) + "\n"
            + (createdTo == null ? "" : createdTo.toString()) + "\n"
            + (queue == null ? TicketQueue.ALL.name() : queue.name()) + "\n"+normalizeId(teamQueueCode)+"\n" + normalize(keyword) + "\n" + pageSize);
    }

    public String encode(CurrentUser user, String filterDigest, int pageSize, int page, Instant snapshotAt,
                         Instant lastCreatedAt, String lastId) {
        try {
            Payload payload = new Payload(1, user.iamUserId(), user.authorizationScopeVersion(), filterDigest,
                pageSize, page, snapshotAt, lastCreatedAt, lastId, clock.instant().plus(properties.cursorTtl()));
            String body = ENCODER.encodeToString(json.writeValueAsBytes(payload));
            return body + "." + ENCODER.encodeToString(sign(body));
        } catch (Exception exception) {
            throw new IllegalStateException("Ticket cursor cannot be encoded", exception);
        }
    }

    public Decoded decode(String cursor, CurrentUser user, String filterDigest, int pageSize) {
        try {
            if (cursor == null || cursor.length() > 4096) throw invalid();
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) throw invalid();
            byte[] supplied = DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), supplied)) throw invalid();
            Payload payload = json.readValue(DECODER.decode(parts[0]), Payload.class);
            if (payload.version() != 1 || payload.page() < 1 || payload.pageSize() != pageSize
                || !user.iamUserId().equals(payload.subject())
                || !user.authorizationScopeVersion().equals(payload.scopeVersion())
                || !filterDigest.equals(payload.filterDigest())
                || payload.expiresAt() == null || !payload.expiresAt().isAfter(clock.instant())
                || payload.snapshotAt() == null || payload.lastCreatedAt() == null
                || payload.lastCreatedAt().isAfter(payload.snapshotAt())
                || payload.lastId() == null || !payload.lastId().matches("^TKT-[0-9]{8}-[0-9]{6}$")) throw invalid();
            return new Decoded(payload.page() + 1, payload.snapshotAt(), payload.lastCreatedAt(), payload.lastId());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private byte[] sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
    }
    private static String normalize(String keyword) { return keyword == null ? "" : keyword.trim().replaceAll("[\\t\\r\\n ]+", " ").toLowerCase(java.util.Locale.ROOT); }
    private static String normalizeId(String value) { return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT); }
    private static String digest(String value) {
        try { return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Ticket filter digest is unavailable", exception); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Ticket cursor is invalid"); }
    private record Payload(int version, String subject, String scopeVersion, String filterDigest, int pageSize, int page,
                           Instant snapshotAt, Instant lastCreatedAt, String lastId, Instant expiresAt) { }
    public record Decoded(int page, Instant snapshotAt, Instant lastCreatedAt, String lastId) { }
}
