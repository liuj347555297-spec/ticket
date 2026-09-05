package cn.servicehub.security;

import cn.servicehub.config.SecurityProperties;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import cn.servicehub.localauth.domain.LocalAccountRepository;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {
    private final PlatformAuthorizationResolver authorizations;
    private final SecurityProperties securityProperties;
    private final Environment environment;
    private final LocalAccountRepository localAccounts;
    public SecurityContextCurrentUserProvider(PlatformAuthorizationResolver authorizations, SecurityProperties securityProperties,
                                              Environment environment, LocalAccountRepository localAccounts) {
        this.authorizations = authorizations; this.securityProperties = securityProperties; this.environment = environment; this.localAccounts = localAccounts;
    }
    @Override
    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }
        if (authentication instanceof VerifiedIamAuthentication verified) {
            return Optional.of(authorizations.resolve(verified.getName(), verified.source()));
        }
        if (authentication instanceof VerifiedLocalAuthentication verified) {
            var account = localAccounts.findById(verified.getName()).orElseThrow(() -> new AccessDeniedException("Local account is unavailable"));
            if (!account.enabled() || account.sessionVersion() != verified.sessionVersion()
                || (account.lockedUntil() != null && account.lockedUntil().isAfter(java.time.Instant.now()))) {
                throw new AccessDeniedException("Local account session is no longer valid");
            }
            return Optional.of(authorizations.resolve(verified.getName(), "LOCAL_ACCOUNT"));
        }
        if (!securityProperties.allowDirectTestIdentities() || environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new AccessDeniedException("A verified IAM authentication context is required");
        }
        return Optional.of(new CurrentUser(authentication.getName(), authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority()).collect(Collectors.toUnmodifiableSet()),
            authentication.getClass().getSimpleName()));
    }

    @Override
    public CurrentUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new AccessDeniedException("Authenticated identity is required"));
    }
}
