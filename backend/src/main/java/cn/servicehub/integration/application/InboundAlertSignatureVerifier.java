package cn.servicehub.integration.application;

import cn.servicehub.integration.domain.ExternalConnectionConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** HMAC verification is performed before an alert payload is parsed or persisted. */
@Component
public class InboundAlertSignatureVerifier {
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    private final SecretResolverPort secrets; private final ReplayProtectionPort replay;
    public InboundAlertSignatureVerifier(SecretResolverPort secrets, ReplayProtectionPort replay) { this.secrets = secrets; this.replay = replay; }
    public void verify(ExternalConnectionConfiguration config, String remoteAddress, String timestamp, String nonce,
                       String signature, String body) {
        if (!config.enabled() || config.secretRef() == null || config.secretRef().isBlank() || !config.allowedCallbackSourceIps().contains(remoteAddress)) throw new IntegrationSecurityException();
        Instant at;
        try { at = Instant.parse(timestamp); } catch (RuntimeException e) { throw new IntegrationSecurityException(); }
        if (Duration.between(at, Instant.now()).abs().compareTo(CLOCK_SKEW) > 0 || nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,128}") || signature == null || !signature.matches("[A-Fa-f0-9]{64}")) throw new IntegrationSecurityException();
        char[] secret = secrets.resolve(config.secretRef()).orElseThrow(IntegrationSecurityException::new);
        byte[] key = null;
        try {
            String signed = timestamp + '.' + nonce + '.' + body;
            key = utf8(secret);
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal(signed.getBytes(StandardCharsets.UTF_8));
            byte[] provided = hex(signature);
            if (!MessageDigest.isEqual(expected, provided) || !replay.consume(config.code(), nonce, at.plus(CLOCK_SKEW))) throw new IntegrationSecurityException();
        } catch (IntegrationSecurityException e) { throw e; } catch (Exception e) { throw new IntegrationSecurityException(); }
        finally { java.util.Arrays.fill(secret, '\0'); if (key != null) java.util.Arrays.fill(key, (byte) 0); }
    }
    private static byte[] utf8(char[] value) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] result = new byte[encoded.remaining()]; encoded.get(result);
        if (encoded.hasArray()) java.util.Arrays.fill(encoded.array(), (byte) 0);
        return result;
    }
    private static byte[] hex(String value) { byte[] b = new byte[value.length() / 2]; for (int i=0; i<b.length; i++) b[i]=(byte) Integer.parseInt(value.substring(i*2,i*2+2),16); return b; }
}
