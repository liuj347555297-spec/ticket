package cn.servicehub.attachment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.servicehub.attachment.infrastructure.ClamAvVirusScanPort;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionVirusScanStartupGuardTest {
    @Test
    void rejectsProductionWithoutManagedScanner() {
        ClamAvProperties enabled = properties(true);
        assertThatThrownBy(() -> new ProductionVirusScanStartupGuard(enabled, List.of()).run(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDisabledProductionConfiguration() {
        ClamAvProperties disabled = properties(false);
        assertThatThrownBy(() -> new ProductionVirusScanStartupGuard(disabled, List.of()).run(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsExactlyOneEnabledManagedScanner() {
        ClamAvProperties enabled = properties(true);
        assertThatCode(() -> new ProductionVirusScanStartupGuard(
                        enabled, List.of(new ClamAvVirusScanPort(enabled))).run(null))
                .doesNotThrowAnyException();
    }

    private static ClamAvProperties properties(boolean enabled) {
        return new ClamAvProperties(
                enabled,
                "127.0.0.1",
                List.of("127.0.0.1"),
                3310,
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ofMillis(300),
                1024,
                256);
    }
}
