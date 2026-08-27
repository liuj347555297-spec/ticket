package cn.servicehub.integration.application;

import java.util.Optional;

/** Resolves an opaque secret reference from the approved deployment secret manager. */
public interface SecretResolverPort {
    Optional<char[]> resolve(String secretRef);
}
