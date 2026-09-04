package cn.servicehub.notification.application;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.net.ssl.SSLParameters;
import org.springframework.stereotype.Component;

/**
 * HTTPS-only JDK transport. Redirects are never followed, JSSE hostname validation remains on,
 * and response data is capped before it can reach the JSON parser.
 */
@Component
public class JdkWpsImHttpTransport implements WpsImHttpTransport {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    @Override public WpsImHttpResponse post(WpsImManagedChannelConfiguration configuration, WpsImHttpRequest payload) {
        URI endpoint = URI.create(configuration.endpoint());
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
            || !configuration.allowedHosts().contains(endpoint.getHost().toLowerCase(java.util.Locale.ROOT))) {
            throw new ExternalMessageDeliveryException("WPS_IM_ENDPOINT_NOT_ALLOWED", false);
        }
        try {
            SSLParameters ssl = new SSLParameters();
            ssl.setProtocols(new String[] { "TLSv1.3", "TLSv1.2" });
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(configuration.connectTimeoutMs()))
                .sslParameters(ssl).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMillis(configuration.requestTimeoutMs()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("X-ServiceHub-App-Id", configuration.appId())
                .header("Idempotency-Key", payload.idempotencyKey())
                .header("Authorization", "Bearer " + payload.credential())
                .POST(HttpRequest.BodyPublishers.ofString(payload.jsonBody(), StandardCharsets.UTF_8)).build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return new WpsImHttpResponse(response.statusCode(), readBounded(response.body()));
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw new ExternalMessageDeliveryException("WPS_IM_TIMEOUT", true);
        } catch (javax.net.ssl.SSLException tls) {
            throw new ExternalMessageDeliveryException("WPS_IM_TLS_FAILURE", false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExternalMessageDeliveryException("WPS_IM_INTERRUPTED", true);
        } catch (IOException transportFailure) {
            throw new ExternalMessageDeliveryException("WPS_IM_TRANSPORT_FAILURE", true);
        }
    }

    private String readBounded(InputStream body) throws IOException {
        try (body) {
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new ExternalMessageDeliveryException("WPS_IM_RESPONSE_TOO_LARGE", false);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
