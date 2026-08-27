package cn.servicehub.integration.application;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class InMemoryReplayProtectionPort implements ReplayProtectionPort {
    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();
    @Override public boolean consume(String sourceCode, String nonce, Instant expiresAt) {
        Instant now = Instant.now();
        seen.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        if (!expiresAt.isAfter(now)) return false;
        return seen.putIfAbsent(sourceCode + ':' + nonce, expiresAt) == null;
    }
}
