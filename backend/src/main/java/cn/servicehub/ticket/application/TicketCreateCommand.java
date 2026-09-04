package cn.servicehub.ticket.application;

import cn.servicehub.ticket.domain.TicketTag;
import cn.servicehub.ticket.domain.TicketDescriptionFormat;
import cn.servicehub.ticket.domain.TicketType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record TicketCreateCommand(String serviceCatalogItemId, int serviceCatalogFormVersion, TicketType type, String title, String description,
                                  TicketDescriptionFormat descriptionFormat, String descriptionHtml,
                                  Map<String, Object> structuredFields, List<TicketTag> tags,
                                  List<String> relatedConfigurationItemIds, String serviceSystemCode, String serviceSystemModuleCode) {
    public TicketCreateCommand(String serviceCatalogItemId, TicketType type, String title, String description,
                               Map<String, Object> structuredFields, List<TicketTag> tags,
                               List<String> relatedConfigurationItemIds) {
        this(serviceCatalogItemId, 1, type, title, description, TicketDescriptionFormat.PLAIN_TEXT, null, structuredFields, tags, relatedConfigurationItemIds, null, null);
    }
    public TicketCreateCommand(String serviceCatalogItemId, int serviceCatalogFormVersion, TicketType type, String title, String description,
                               TicketDescriptionFormat descriptionFormat, String descriptionHtml, Map<String, Object> structuredFields,
                               List<TicketTag> tags, List<String> relatedConfigurationItemIds) {
        this(serviceCatalogItemId, serviceCatalogFormVersion, type, title, description, descriptionFormat, descriptionHtml,
            structuredFields, tags, relatedConfigurationItemIds, null, null);
    }
    public TicketCreateCommand {
        structuredFields = structuredFields == null ? Map.of() : Map.copyOf(structuredFields);
        tags = tags == null ? List.of() : List.copyOf(tags);
        relatedConfigurationItemIds = relatedConfigurationItemIds == null ? List.of() : List.copyOf(relatedConfigurationItemIds);
    }

    /** Stable enough for the supported JSON primitives; persistence will retain this with the idempotency record. */
    public String fingerprint() {
        String canonical = serviceCatalogItemId + '\u001f' + serviceCatalogFormVersion + '\u001f' + type + '\u001f' + title + '\u001f' + description + '\u001f' + descriptionFormat + '\u001f' + descriptionHtml + '\u001f'
            + canonicalValue(new TreeMap<>(structuredFields)) + '\u001f' + tags + '\u001f' + relatedConfigurationItemIds + '\u001f' + serviceSystemCode + '\u001f' + serviceSystemModuleCode;
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new TreeMap<>(map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> String.valueOf(entry.getKey()), Map.Entry::getValue))).entrySet().stream()
                .map(entry -> entry.getKey() + '=' + canonicalValue(entry.getValue())).collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(TicketCreateCommand::canonicalValue).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        return String.valueOf(value);
    }
}
