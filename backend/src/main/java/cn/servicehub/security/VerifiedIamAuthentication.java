package cn.servicehub.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Server-created marker for a subject whose OIDC/SAML assertion has already been verified.
 * It is never built from a browser header or request payload.
 */
public final class VerifiedIamAuthentication extends AbstractAuthenticationToken {
    private final String iamUserId;
    private final String source;

    VerifiedIamAuthentication(String iamUserId, String source, Collection<? extends GrantedAuthority> authorities) {
        super(authorities); this.iamUserId = iamUserId; this.source = source; super.setAuthenticated(true);
    }
    @Override public Object getCredentials() { return ""; }
    @Override public Object getPrincipal() { return iamUserId; }
    @Override public String getName() { return iamUserId; }
    public String source() { return source; }
    @Override public void setAuthenticated(boolean authenticated) { if (!authenticated) { super.setAuthenticated(false); return; } throw new IllegalArgumentException("Use the server-side authentication factory"); }
}
