package cn.servicehub.integration.application;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** Safe default: callbacks and live connector calls stay disabled until a managed resolver is supplied. */
@Component
class DisabledSecretResolver implements SecretResolverPort {
    @Override public Optional<char[]> resolve(String secretRef) { return Optional.empty(); }
}
