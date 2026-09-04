package cn.servicehub.notification.application;

import java.util.Optional;

/**
 * Boundary for deployment-managed secrets. Implementations receive a reference, never a secret
 * from an HTTP request or database routing rule. Production installations normally replace the
 * local environment resolver with a Vault/HSM-backed implementation.
 */
public interface ManagedSecretResolver {
    Optional<String> resolve(String reference);
}
