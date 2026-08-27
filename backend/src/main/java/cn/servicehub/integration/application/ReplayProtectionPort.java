package cn.servicehub.integration.application;

import java.time.Instant;

public interface ReplayProtectionPort {
    /** Returns false when the nonce was already consumed or has an invalid expiry. */
    boolean consume(String sourceCode, String nonce, Instant expiresAt);
}
