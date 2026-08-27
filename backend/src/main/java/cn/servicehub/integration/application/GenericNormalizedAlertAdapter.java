package cn.servicehub.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Strict neutral alert envelope for approved monitoring adapters; vendor payload parsing is not enabled here. */
@Component
class GenericNormalizedAlertAdapter implements ExternalAlertAdapterPort {
    private final ObjectMapper json;
    GenericNormalizedAlertAdapter(ObjectMapper json) { this.json = json; }
    @Override public boolean supports(String sourceCode) { return sourceCode != null && sourceCode.matches("[A-Z0-9_-]{2,40}"); }
    @Override public AlertInput normalize(String raw) {
        try {
            JsonNode input = json.readTree(raw);
            String eventId = required(input, "eventId", 128); String fingerprint = required(input, "fingerprint", 128);
            String severity = required(input, "severity", 16).toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO").contains(severity)) throw new IllegalArgumentException();
            String title = required(input, "title", 240); String ci = optional(input, "configurationItemId", 128);
            String occurredAt = required(input, "occurredAt", 40);
            return new AlertInput(eventId, fingerprint, severity, title, ci, Instant.parse(occurredAt));
        } catch (Exception exception) { throw new IllegalArgumentException("Alert payload is invalid"); }
    }
    private static String required(JsonNode input,String name,int max) { String value=optional(input,name,max); if(value==null) throw new IllegalArgumentException(); return value; }
    private static String optional(JsonNode input,String name,int max) { JsonNode value=input.get(name); if(value==null||!value.isTextual()||value.textValue().isBlank()||value.textValue().trim().length()>max) return null; return value.textValue().trim(); }
}
