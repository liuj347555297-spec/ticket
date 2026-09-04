package cn.servicehub.integration.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.servicehub.integration.domain.ExternalConnectionConfiguration;
import cn.servicehub.integration.domain.ExternalSystemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalIntegrationBoundaryTest {

    @Test
    void signedCallbackRequiresMonitoringOpaqueSecretReferenceAndLiteralSourceIp() {
        assertTrue(connection("secret://monitoring/alert-hmac", List.of("10.20.30.40"), ExternalSystemType.MONITORING).supportsSignedAlertCallback());
        assertFalse(connection("plain-text-secret", List.of("10.20.30.40"), ExternalSystemType.MONITORING).supportsSignedAlertCallback());
        assertFalse(connection("secret://monitoring/alert-hmac", List.of("monitoring.internal"), ExternalSystemType.MONITORING).supportsSignedAlertCallback());
        assertFalse(connection("secret://monitoring/alert-hmac", List.of("10.20.30.40"), ExternalSystemType.CMDB).supportsSignedAlertCallback());
    }

    @Test
    void neutralEnvelopeIsExplicitlyScopedAndRejectsUnknownFieldsBeforeMapping() {
        var adapter = new GenericNormalizedAlertAdapter(new ObjectMapper());
        assertTrue(adapter.supports("MONITORING"));
        assertFalse(adapter.supports("APM"));
        assertDoesNotThrow(() -> adapter.normalize("""
            {"eventId":"evt-1","fingerprint":"fp-1","severity":"HIGH","title":"CPU 高","occurredAt":"2026-08-31T00:00:00Z"}
            """));
        assertThrows(IllegalArgumentException.class, () -> adapter.normalize("""
            {"eventId":"evt-1","fingerprint":"fp-1","severity":"HIGH","title":"CPU 高","occurredAt":"2026-08-31T00:00:00Z","headers":{"x":"must-not-be-accepted"}}
            """));
    }

    private static ExternalConnectionConfiguration connection(String secretReference, List<String> sourceIps, ExternalSystemType type) {
        return new ExternalConnectionConfiguration("MONITORING", "受管监控", type, "https://monitoring.internal", secretReference,
            true, 1500, 60, sourceIps, Instant.parse("2026-08-31T00:00:00Z"));
    }
}
