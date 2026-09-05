package cn.servicehub.ticketdraft;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class TicketDraftModels {
    private TicketDraftModels() {}
    public record Input(long version, JsonNode payload) {}
    public record Draft(String id, String ownerId, String title, JsonNode payload, long version, Instant createdAt, Instant updatedAt) {}
    public record Summary(String id,String title,String systemCode,String catalogId,long version,Instant updatedAt) {
        static Summary of(Draft d) { return new Summary(d.id(),d.title(),d.payload().path("form").path("systemCode").asText(),d.payload().path("form").path("catalogId").asText(),d.version(),d.updatedAt()); }
    }
    public record Page(List<Summary> items,int page,int pageSize,long total) {}
}
