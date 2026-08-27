package cn.servicehub.security;

import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {
    private final PlatformAuthorizationResolver authorizations;
    public SecurityContextCurrentUserProvider(PlatformAuthorizationResolver authorizations) { this.authorizations = authorizations; }
    @Override
    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }
        if (authentication instanceof VerifiedIamAuthentication verified) {
            return Optional.of(authorizations.resolve(verified.getName(), verified.source()));
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
