package cn.servicehub.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Set;
import cn.servicehub.notification.application.WpsImManagedChannelConfiguration;
import org.junit.jupiter.api.Test;

class WpsImManagedChannelConfigurationTest {
    @Test void derivesTicketUrlFromTrustedBaseOnly() {
        var config = new WpsImManagedChannelConfiguration("app-A", "https://wps.internal/api", "vault:wps-a", "https://servicehub.internal/", "ticket-card-v1", Set.of("wps.internal"), 3000, 5000);
        assertEquals("https://servicehub.internal/tickets/TKT-20260820-000001", config.targetUrl("TKT-20260820-000001"));
        assertThrows(IllegalArgumentException.class, () -> config.targetUrl("https://outside/evil"));
    }
}
