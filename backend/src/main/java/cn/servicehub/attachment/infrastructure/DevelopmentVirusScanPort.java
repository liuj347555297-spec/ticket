package cn.servicehub.attachment.infrastructure;

import cn.servicehub.attachment.application.VirusScanPort;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Deterministic scanner used only by the default development context and explicit test profile. */
@Component
@Profile("default | test")
public class DevelopmentVirusScanPort implements VirusScanPort {
    private static final byte[] EICAR = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE".getBytes(StandardCharsets.US_ASCII);
    @Override public ScanResult scan(String key, byte[] content) {
        return contains(content, EICAR) ? new ScanResult(false, "MALWARE_SIGNATURE") : new ScanResult(true, "DEVELOPMENT_SCANNER_CLEAN");
    }
    private boolean contains(byte[] content, byte[] needle) { outer: for (int i=0;i<=content.length-needle.length;i++) { for (int j=0;j<needle.length;j++) if (content[i+j]!=needle[j]) continue outer; return true; } return false; }
}
