package cn.servicehub.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Strict neutral alert envelope for approved monitoring adapters; vendor payload parsing is not enabled here. */
@Component
class GenericNormalizedAlertAdapter implements ExternalAlertAdapterPort {
    private static final Set<String> ALLOWED_FIELDS = Set.of("eventId", "fingerprint", "severity", "title", "configurationItemId", "occurredAt");
    private final ObjectMapper json;
    GenericNormalizedAlertAdapter(ObjectMapper json) { this.json = json; }
    /** A vendor adapter must be added explicitly; the neutral envelope is not a wildcard parser. */
    @Override public boolean supports(String sourceCode) { return "MONITORING".equals(sourceCode); }
    @Override public AlertInput normalize(String raw) {
        try {
            JsonNode input = json.readTree(raw);
            if (input == null || !input.isObject() || input.size() > ALLOWED_FIELDS.size()) throw new IllegalArgumentException();
            var names = input.fieldNames();
            while (names.hasNext()) if (!ALLOWED_FIELDS.contains(names.next())) throw new IllegalArgumentException();
            String eventId = required(input, "eventId", 128); String fingerprint = required(input, "fingerprint", 128);
            String severity = required(input, "severity", 16).toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO").contains(severity)) throw new IllegalArgumentException();
            String title = required(input, "title", 240); String ci = optional(input, "configurationItemId", 128);
            String occurredAt = required(input, "occurredAt", 40);
            return new AlertInput(eventId, fingerprint, severity, title, ci, Instant.parse(occurredAt));
        } catch (Exception exception) { throw new IllegalArgumentException("Alert payload is invalid"); }
    }
    private static String required(JsonNode input,String name,int max) { String value=optional(input,name,max); if(value==null) throw new IllegalArgumentException(); return value; }
    private static String optional(JsonNode input,String name,int max) { JsonNode value=input.get(name); if(value==null||!value.isTextual()||value.textValue().isBlank()||value.textValue().trim().length()>max) return null; String normalized=value.textValue().trim(); if (normalized.codePoints().anyMatch(Character::isISOControl)) return null; return normalized; }
}
