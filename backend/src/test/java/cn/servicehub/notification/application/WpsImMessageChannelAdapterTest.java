package cn.servicehub.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.servicehub.notification.domain.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WpsImMessageChannelAdapterTest {
    @Test
    void disabledOrIncompleteConfigurationNeverPretendsToDeliver() {
        WpsImMessageChannelAdapter adapter = new WpsImMessageChannelAdapter(
            new WpsImProperties(false, null, null, null, null, null, List.of(), 3000, 5000));

        assertFalse(adapter.enabled());
        ExternalMessageDeliveryException failure = assertThrows(ExternalMessageDeliveryException.class,
            () -> adapter.deliver(notification()));
        assertEquals("WPS_IM_NOT_CONFIGURED", failure.safeCode());
        assertFalse(failure.retryable());
    }

    @Test
    void configuredButUnavailableCredentialNeverPretendsToDeliver() {
        WpsImMessageChannelAdapter adapter = new WpsImMessageChannelAdapter(
            new WpsImProperties(true, "managed-app", "https://im.internal.example/api", "vault:wps/servicehub",
                "https://servicehub.internal.example", "ticket-card-v1", List.of("im.internal.example"), 3000, 5000));

        assertFalse(adapter.enabled());
        assertEquals("WPS_IM_CREDENTIAL_UNAVAILABLE", assertThrows(ExternalMessageDeliveryException.class,
            () -> adapter.deliver(notification())).safeCode());
    }

    @Test
    void sendsOnlyAFixedServerOwnedCardAndRequiresProviderReceipt() {
        AtomicReference<WpsImHttpRequest> captured = new AtomicReference<>();
        WpsImMessageChannelAdapter adapter = new WpsImMessageChannelAdapter(configuration(),
            reference -> Optional.of("managed-secret-value"), (ignored, request) -> {
                captured.set(request); return new WpsImHttpResponse(202, "{\"success\":true,\"receiptId\":\"wps-receipt-12\"}");
            }, new ObjectMapper());

        adapter.deliver(notification());

        assertEquals(64, captured.get().idempotencyKey().length());
        assertFalse(captured.get().jsonBody().contains("managed-secret-value"));
        assertTrue(captured.get().jsonBody().contains("https://servicehub.internal.example/tickets/TKT-20260830-000001"));
        assertTrue(captured.get().jsonBody().contains("ticket-card-v1"));
    }

    @Test
    void malformedProviderSuccessResponseIsNotTreatedAsDelivered() {
        WpsImMessageChannelAdapter adapter = new WpsImMessageChannelAdapter(configuration(), reference -> Optional.of("secret"),
            (ignored, request) -> new WpsImHttpResponse(200, "{\"success\":true}"), new ObjectMapper());
        assertEquals("WPS_IM_INVALID_RECEIPT", assertThrows(ExternalMessageDeliveryException.class,
            () -> adapter.deliver(notification())).safeCode());
    }

    private WpsImProperties configuration() {
        return new WpsImProperties(true, "managed-app", "https://im.internal.example/api", "env:WPS_SECRET",
            "https://servicehub.internal.example", "ticket-card-v1", List.of("im.internal.example"), 3000, 5000);
    }

    private Notification notification() {
        return new Notification("NTF-test-0001", "iam-u-1001", "TICKET", "工单已提交", "处理中",
            "TKT-20260830-000001", Map.of("targetPath", "/tickets/TKT-20260830-000001"), "dedupe", null,
            Instant.parse("2026-08-30T00:00:00Z"), 0);
    }
}
