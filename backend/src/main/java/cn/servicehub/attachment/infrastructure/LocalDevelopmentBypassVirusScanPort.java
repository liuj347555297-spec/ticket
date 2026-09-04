package cn.servicehub.attachment.infrastructure;

import cn.servicehub.attachment.application.VirusScanPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local workstation only: preserves the upload validation and authorization path while bypassing
 * the unavailable enterprise scanner. This bean cannot be activated by a production profile.
 */
@Component
@Profile("local-dev & !prod")
public class LocalDevelopmentBypassVirusScanPort implements VirusScanPort {
    @Override
    public ScanResult scan(String storageKey, byte[] content) {
        return new ScanResult(true, "LOCAL_DEV_SCAN_BYPASSED");
    }
}
