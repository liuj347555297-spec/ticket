package cn.servicehub.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Comparator;
import java.util.Set;

/** Identity resolved by an authentication mechanism; never taken from a request body or header. */
public record CurrentUser(String iamUserId, Set<String> authorities, String authenticationType,
                          boolean trustedAuthorizationContext, String authorizationScopeVersion) {
    public CurrentUser {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        if (iamUserId == null || iamUserId.isBlank()) throw new IllegalArgumentException("IAM user id is required");
        if (authorizationScopeVersion == null || authorizationScopeVersion.isBlank()) {
            throw new IllegalArgumentException("Authorization scope version is required");
        }
    }

    /** Compatibility path for tests, local preview identities and internal audit-only actors. */
    public CurrentUser(String iamUserId, Set<String> authorities, String authenticationType) {
        this(iamUserId, authorities, authenticationType, false, directScopeVersion(iamUserId, authorities));
    }

    private static String directScopeVersion(String iamUserId, Set<String> authorities) {
        try {
            String canonical = iamUserId + "\n" + (authorities == null ? "" : authorities.stream().sorted(Comparator.naturalOrder()).reduce("", (a, b) -> a + b + "\n"));
            return "direct-" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Authorization scope digest is unavailable", exception);
        }
    }
}
