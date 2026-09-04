package cn.servicehub.notification.application;

import cn.servicehub.notification.domain.MessageChannel;
import cn.servicehub.notification.domain.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * WPS enterprise-application message boundary. Routing is resolved before this point and the
 * adapter accepts no browser recipient, URL, endpoint, template, or credential input.
 */
@Component
public class WpsImMessageChannelAdapter implements MessageChannelPort {
    private final WpsImProperties properties;
    private final ManagedSecretResolver secrets;
    private final WpsImHttpTransport transport;
    private final ObjectMapper json;

    /** Compatibility constructor for small unit tests; it deliberately has no usable secret. */
    public WpsImMessageChannelAdapter(WpsImProperties properties) {
        this(properties, reference -> java.util.Optional.empty(), (configuration, request) -> {
            throw new ExternalMessageDeliveryException("WPS_IM_PROVIDER_ADAPTER_NOT_ACTIVATED", false);
        }, new ObjectMapper());
    }

    @Autowired
    public WpsImMessageChannelAdapter(WpsImProperties properties, ManagedSecretResolver secrets,
                                      WpsImHttpTransport transport, ObjectMapper json) {
        this.properties = properties; this.secrets = secrets; this.transport = transport; this.json = json;
    }

    @Override public MessageChannel channel() { return MessageChannel.WPS_IM; }
    /** A route is available only when its fixed configuration and managed credential both resolve. */
    @Override public boolean enabled() {
        try { return properties.readyForDelivery() && secrets.resolve(properties.secretReference()).filter(value -> !value.isBlank()).isPresent(); }
        catch (RuntimeException ignored) { return false; }
    }
    @Override public void deliver(Notification notification) {
        WpsImManagedChannelConfiguration configuration = properties.managedConfiguration();
        String credential = secrets.resolve(configuration.secretRef())
            .filter(value -> !value.isBlank()).orElseThrow(() -> new ExternalMessageDeliveryException("WPS_IM_CREDENTIAL_UNAVAILABLE", false));
        WpsImNotificationCard card = renderCard(notification, configuration);
        WpsImHttpResponse response = transport.post(configuration,
            new WpsImHttpRequest(idempotencyKey(notification), credential, serializeRequest(notification, configuration, card)));
        validateProviderReceipt(response);
    }

    /** Builds only a fixed card once a managed configuration has been approved; no caller supplies a URL or template. */
    WpsImNotificationCard renderCard(Notification notification, WpsImManagedChannelConfiguration configuration) {
        if (notification.ticketId() == null) throw new IllegalArgumentException("WPS notification requires a server-owned ticket reference");
        return new WpsImNotificationCard(cardText(notification.title(), 120), cardText(notification.body(), 500),
            configuration.targetUrl(notification.ticketId()), configuration.templateRef());
    }

    private String serializeRequest(Notification notification, WpsImManagedChannelConfiguration configuration, WpsImNotificationCard card) {
        if (notification.recipientIamUserId() == null || !notification.recipientIamUserId().matches("^[A-Za-z0-9._:@-]{1,128}$")) {
            throw new ExternalMessageDeliveryException("WPS_IM_RECIPIENT_INVALID", false);
        }
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("appId", configuration.appId());
            message.put("recipientIamUserId", notification.recipientIamUserId());
            message.put("templateRef", card.templateRef());
            message.put("title", card.title());
            message.put("summary", card.summary());
            message.put("targetUrl", card.targetUrl());
            // Route data is server-side only. Keep an optional provider channel code constrained
            // to an identifier so it can never become a URL, expression, or recipient list.
            String providerChannel = notification.payload().get("providerChannelCode");
            if (providerChannel != null && !providerChannel.isBlank()) {
                if (!providerChannel.matches("^[A-Za-z0-9._:-]{1,128}$")) throw new ExternalMessageDeliveryException("WPS_IM_ROUTE_INVALID", false);
                message.put("providerChannelCode", providerChannel);
            }
            return json.writeValueAsString(message);
        } catch (ExternalMessageDeliveryException failure) { throw failure;
        } catch (Exception failure) { throw new ExternalMessageDeliveryException("WPS_IM_REQUEST_SERIALIZATION_FAILED", false); }
    }

    private void validateProviderReceipt(WpsImHttpResponse response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            if (status == 408 || status == 429 || status >= 500) throw new ExternalMessageDeliveryException("WPS_IM_PROVIDER_UNAVAILABLE", true);
            if (status == 401 || status == 403) throw new ExternalMessageDeliveryException("WPS_IM_AUTH_REJECTED", false);
            throw new ExternalMessageDeliveryException("WPS_IM_PROVIDER_REJECTED", false);
        }
        try {
            JsonNode root = json.readTree(response.body());
            if (root == null || !root.path("success").isBoolean() || !root.path("success").booleanValue()) {
                throw new ExternalMessageDeliveryException("WPS_IM_INVALID_RECEIPT", false);
            }
            JsonNode receipt = root.path("receiptId");
            if (!receipt.isTextual() || !receipt.textValue().matches("^[A-Za-z0-9._:-]{1,128}$")) {
                throw new ExternalMessageDeliveryException("WPS_IM_INVALID_RECEIPT", false);
            }
            // The receipt proves provider acknowledgement for the outbox state machine. Raw
            // receipt data intentionally is not stored because it can reveal provider topology.
        } catch (ExternalMessageDeliveryException failure) { throw failure;
        } catch (Exception malformed) { throw new ExternalMessageDeliveryException("WPS_IM_INVALID_RECEIPT", false); }
    }

    private String idempotencyKey(Notification notification) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(("wps-im|" + notification.deduplicationKey()).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is required", impossible); }
    }

    private String cardText(String source, int maxLength) {
        if (source == null) return "";
        String normalized = source.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 1) + "…";
    }
}
