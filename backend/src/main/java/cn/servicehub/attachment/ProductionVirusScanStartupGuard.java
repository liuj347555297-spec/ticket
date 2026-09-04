package cn.servicehub.attachment;

import cn.servicehub.attachment.application.VirusScanPort;
import cn.servicehub.attachment.infrastructure.ClamAvVirusScanPort;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionVirusScanStartupGuard implements ApplicationRunner {
    private final ClamAvProperties properties;
    private final List<VirusScanPort> scanners;

    public ProductionVirusScanStartupGuard(ClamAvProperties properties, List<VirusScanPort> scanners) {
        this.properties = properties;
        this.scanners = List.copyOf(scanners);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()
                || scanners.size() != 1
                || !(scanners.getFirst() instanceof ClamAvVirusScanPort)) {
            throw new IllegalStateException("Production requires exactly one managed ClamAV scanner");
        }
    }
}
