package cn.servicehub.notification.application;

import java.util.Optional;

/**
 * Local-development resolver only. `env:NAME` resolves a process environment variable; all
 * other references fail closed so a Vault-style reference can never be mistaken for its value.
 */
public class EnvironmentManagedSecretResolver implements ManagedSecretResolver {
    @Override public Optional<String> resolve(String reference) {
        if (reference == null || !reference.matches("^env:[A-Z][A-Z0-9_]{0,127}$")) return Optional.empty();
        String value = System.getenv(reference.substring("env:".length()));
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
