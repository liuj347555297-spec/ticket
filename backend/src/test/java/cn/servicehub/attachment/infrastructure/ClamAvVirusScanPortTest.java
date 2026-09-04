package cn.servicehub.attachment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.servicehub.attachment.ClamAvProperties;
import cn.servicehub.attachment.application.VirusScanPort;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ClamAvVirusScanPortTest {
    private static final byte[] COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);

    @Test
    void streamsNetworkOrderChunksAndAcceptsOnlyCleanResponse() throws Exception {
        byte[] content = "abcdefgh".getBytes(StandardCharsets.US_ASCII);
        try (FakeClamAv server = FakeClamAv.respond("stream: OK\0")) {
            VirusScanPort.ScanResult result = scanner(server.port(), Duration.ofMillis(300)).scan("quarantine/a", content);

            assertThat(result.clean()).isTrue();
            assertThat(result.detail()).isEqualTo("CLEAN");
            assertThat(server.command()).containsExactly(COMMAND);
            assertThat(server.content()).containsExactly(content);
            assertThat(server.chunkSizes()).containsExactly(3, 3, 2, 0);
        }
    }

    @Test
    void mapsFoundAndScannerErrorWithoutTrustingTheirMessages() throws Exception {
        try (FakeClamAv found = FakeClamAv.respond("stream: Eicar-Test-Signature FOUND\0")) {
            VirusScanPort.ScanResult result = scanner(found.port(), Duration.ofMillis(300)).scan("quarantine/a", new byte[] {1});
            assertThat(result).isEqualTo(new VirusScanPort.ScanResult(false, "MALWARE_FOUND"));
        }
        try (FakeClamAv error = FakeClamAv.respond("stream: temporary failure ERROR\0")) {
            VirusScanPort.ScanResult result = scanner(error.port(), Duration.ofMillis(300)).scan("quarantine/a", new byte[] {1});
            assertThat(result).isEqualTo(new VirusScanPort.ScanResult(false, "SCANNER_UNAVAILABLE"));
        }
    }

    @Test
    void failsClosedOnDisconnectTimeoutAndMalformedResponse() throws Exception {
        try (FakeClamAv disconnect = FakeClamAv.disconnect()) {
            assertThat(scanner(disconnect.port(), Duration.ofMillis(200)).scan("quarantine/a", new byte[] {1}))
                    .isEqualTo(new VirusScanPort.ScanResult(false, "PROTOCOL_ERROR"));
        }
        try (FakeClamAv timeout = FakeClamAv.delayed("stream: OK\0", Duration.ofMillis(400))) {
            assertThat(scanner(timeout.port(), Duration.ofMillis(50)).scan("quarantine/a", new byte[] {1}))
                    .isEqualTo(new VirusScanPort.ScanResult(false, "TIMEOUT"));
        }
        try (FakeClamAv malformed = FakeClamAv.respond("stream: OK\nFORGED\0")) {
            assertThat(scanner(malformed.port(), Duration.ofMillis(200)).scan("quarantine/a", new byte[] {1}))
                    .isEqualTo(new VirusScanPort.ScanResult(false, "PROTOCOL_ERROR"));
        }
    }

    @Test
    void rejectsTargetOutsideExactManagedAllowList() {
        assertThatThrownBy(() -> new ClamAvProperties(
                        true, "127.0.0.1", List.of("scanner.internal"), 3310,
                        Duration.ofMillis(100), Duration.ofMillis(100), Duration.ofMillis(300), 1024, 256))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalTimeoutActivelyClosesAConnectionBlockedWhileWriting() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ExecutorService blockedServer = Executors.newSingleThreadExecutor()) {
            Future<?> accepted = blockedServer.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setReceiveBufferSize(1024);
                    Thread.sleep(2_000);
                }
                return null;
            });
            ClamAvProperties properties = new ClamAvProperties(
                    true,
                    InetAddress.getLoopbackAddress().getHostAddress(),
                    List.of(InetAddress.getLoopbackAddress().getHostAddress()),
                    server.getLocalPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(50),
                    Duration.ofMillis(150),
                    65_536,
                    128);

            long started = System.nanoTime();
            VirusScanPort.ScanResult result = new ClamAvVirusScanPort(properties)
                    .scan("quarantine/a", new byte[32 * 1024 * 1024]);

            assertThat(result).isEqualTo(new VirusScanPort.ScanResult(false, "TIMEOUT"));
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
            accepted.cancel(true);
            blockedServer.shutdownNow();
        }
    }

    private static ClamAvVirusScanPort scanner(int port, Duration readTimeout) {
        return new ClamAvVirusScanPort(new ClamAvProperties(
                true,
                InetAddress.getLoopbackAddress().getHostAddress(),
                List.of(InetAddress.getLoopbackAddress().getHostAddress()),
                port,
                Duration.ofMillis(200),
                readTimeout,
                Duration.ofSeconds(2),
                3,
                128));
    }

    private static final class FakeClamAv implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<CapturedRequest> request;

        private FakeClamAv(byte[] response, Duration delay, boolean disconnect) throws Exception {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            request = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    byte[] command = input.readNBytes(COMMAND.length);
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    java.util.ArrayList<Integer> chunkSizes = new java.util.ArrayList<>();
                    while (true) {
                        int length = input.readInt();
                        chunkSizes.add(length);
                        if (length == 0) break;
                        content.write(input.readNBytes(length));
                    }
                    if (!disconnect) {
                        if (!delay.isZero()) Thread.sleep(delay);
                        socket.getOutputStream().write(response);
                        socket.getOutputStream().flush();
                    }
                    return new CapturedRequest(command, content.toByteArray(), chunkSizes.stream().mapToInt(Integer::intValue).toArray());
                }
            });
        }

        static FakeClamAv respond(String response) throws Exception {
            return new FakeClamAv(response.getBytes(StandardCharsets.US_ASCII), Duration.ZERO, false);
        }

        static FakeClamAv delayed(String response, Duration delay) throws Exception {
            return new FakeClamAv(response.getBytes(StandardCharsets.US_ASCII), delay, false);
        }

        static FakeClamAv disconnect() throws Exception {
            return new FakeClamAv(new byte[0], Duration.ZERO, true);
        }

        int port() {
            return server.getLocalPort();
        }

        byte[] command() throws Exception {
            return captured().command();
        }

        byte[] content() throws Exception {
            return captured().content();
        }

        int[] chunkSizes() throws Exception {
            return captured().chunkSizes();
        }

        private CapturedRequest captured() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
            return request.get(2, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            server.close();
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private record CapturedRequest(byte[] command, byte[] content, int[] chunkSizes) { }
}
