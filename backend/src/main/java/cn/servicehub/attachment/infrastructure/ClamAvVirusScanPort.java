package cn.servicehub.attachment.infrastructure;

import cn.servicehub.attachment.ClamAvProperties;
import cn.servicehub.attachment.application.VirusScanPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("clamav & !local-dev")
@ConditionalOnProperty(prefix = "servicehub.attachment.clamav", name = "enabled", havingValue = "true")
public class ClamAvVirusScanPort implements VirusScanPort {
    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TERMINATOR = new byte[4];
    private static final String PREFIX = "stream: ";
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "clamav-scan-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    private final ClamAvProperties properties;
    private final List<InetAddress> resolvedTargets;

    public ClamAvVirusScanPort(ClamAvProperties properties) {
        this.properties = properties;
        this.resolvedTargets = resolveAllowedTargets(properties);
    }

    @Override
    public ScanResult scan(String storageKey, byte[] content) {
        if (content == null) {
            return new ScanResult(false, "PROTOCOL_ERROR");
        }
        long deadline = System.nanoTime() + properties.totalTimeout().toNanos();
        AtomicBoolean totalTimedOut = new AtomicBoolean();
        try (Socket socket = new Socket()) {
            ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
                totalTimedOut.set(true);
                closeQuietly(socket);
            }, properties.totalTimeout().toNanos(), TimeUnit.NANOSECONDS);
            try {
                socket.connect(
                        new InetSocketAddress(resolvedTargets.getFirst(), properties.port()),
                        boundedMillis(properties.connectTimeout(), deadline));
                writeRequest(socket.getOutputStream(), content, deadline);
                String response = readResponse(socket, deadline);
                return parse(response);
            } finally {
                watchdog.cancel(false);
            }
        } catch (ProtocolException exception) {
            return new ScanResult(false, "PROTOCOL_ERROR");
        } catch (SocketTimeoutException exception) {
            return new ScanResult(false, "TIMEOUT");
        } catch (IOException | RuntimeException exception) {
            return new ScanResult(false, totalTimedOut.get() ? "TIMEOUT" : "SCANNER_UNAVAILABLE");
        }
    }

    private void writeRequest(OutputStream output, byte[] content, long deadline) throws IOException {
        output.write(INSTREAM);
        int offset = 0;
        while (offset < content.length) {
            ensureTimeRemaining(deadline);
            int length = Math.min(properties.chunkSizeBytes(), content.length - offset);
            output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length).array());
            output.write(content, offset, length);
            offset += length;
        }
        ensureTimeRemaining(deadline);
        output.write(TERMINATOR);
        output.flush();
    }

    private String readResponse(Socket socket, long deadline) throws IOException {
        InputStream input = socket.getInputStream();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        while (true) {
            socket.setSoTimeout(boundedMillis(properties.readTimeout(), deadline));
            int next = input.read();
            if (next < 0) {
                throw new ProtocolException();
            }
            if (next == 0) {
                if (response.size() == 0 || input.available() > 0) {
                    throw new ProtocolException();
                }
                break;
            }
            if (next < 0x20 || next > 0x7e || response.size() >= properties.maxResponseBytes()) {
                throw new ProtocolException();
            }
            response.write(next);
        }
        return response.toString(StandardCharsets.US_ASCII);
    }

    private static ScanResult parse(String response) throws ProtocolException {
        if ((PREFIX + "OK").equals(response)) {
            return new ScanResult(true, "CLEAN");
        }
        if (!response.startsWith(PREFIX)) {
            throw new ProtocolException();
        }
        String result = response.substring(PREFIX.length());
        if (result.endsWith(" FOUND") && validMessage(result, " FOUND")) {
            return new ScanResult(false, "MALWARE_FOUND");
        }
        if (result.endsWith(" ERROR") && validMessage(result, " ERROR")) {
            return new ScanResult(false, "SCANNER_UNAVAILABLE");
        }
        throw new ProtocolException();
    }

    private static boolean validMessage(String value, String suffix) {
        String message = value.substring(0, value.length() - suffix.length()).trim();
        return !message.isEmpty() && message.length() <= 512 && message.chars().allMatch(c -> c >= 0x20 && c <= 0x7e);
    }

    private static int boundedMillis(Duration configured, long deadline) throws SocketTimeoutException {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new SocketTimeoutException("ClamAV total timeout elapsed");
        }
        long configuredMillis = Math.max(1, configured.toMillis());
        long remainingMillis = Math.max(1, Duration.ofNanos(remainingNanos).toMillis());
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.min(configuredMillis, remainingMillis)));
    }

    private static void ensureTimeRemaining(long deadline) throws SocketTimeoutException {
        if (System.nanoTime() >= deadline) {
            throw new SocketTimeoutException("ClamAV total timeout elapsed");
        }
    }

    private static List<InetAddress> resolveAllowedTargets(ClamAvProperties properties) {
        if (!properties.enabled() || !properties.allowedHosts().contains(properties.host())) {
            throw new IllegalArgumentException("ClamAV target is not enabled or allow-listed");
        }
        ExecutorService resolver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "clamav-dns-resolver");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Set<InetAddress> allowedAddresses = new LinkedHashSet<>();
            Set<InetAddress> targetAddresses = new LinkedHashSet<>();
            for (String allowedHost : properties.allowedHosts()) {
                Future<InetAddress[]> lookup = resolver.submit(() -> InetAddress.getAllByName(allowedHost));
                try {
                    List<InetAddress> resolved = Arrays.asList(lookup.get(
                            properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS));
                    allowedAddresses.addAll(resolved);
                    if (allowedHost.equals(properties.host())) {
                        targetAddresses.addAll(resolved);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("ClamAV allow-list resolution was interrupted", exception);
                } catch (ExecutionException | TimeoutException exception) {
                    lookup.cancel(true);
                    throw new IllegalStateException("ClamAV allow-list resolution failed", exception);
                }
            }
            if (targetAddresses.isEmpty() || !allowedAddresses.containsAll(targetAddresses)) {
                throw new IllegalStateException("ClamAV target did not resolve inside the startup allow-list");
            }
            return List.copyOf(targetAddresses);
        } finally {
            resolver.shutdownNow();
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing is only used to end blocked I/O at the total deadline.
        }
    }

    private static final class ProtocolException extends IOException { }
}
