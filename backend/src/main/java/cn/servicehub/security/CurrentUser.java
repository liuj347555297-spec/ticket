package cn.servicehub.security;

import java.util.Set;

/** Identity resolved by an authentication mechanism; never taken from a request body or header. */
public record CurrentUser(String iamUserId, Set<String> authorities, String authenticationType) {
    public CurrentUser {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }
}
