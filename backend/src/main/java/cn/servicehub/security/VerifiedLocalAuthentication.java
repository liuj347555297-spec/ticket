package cn.servicehub.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Server-created marker backed by a password verification, never by a request identity header. */
public final class VerifiedLocalAuthentication extends AbstractAuthenticationToken {
    private final String accountId;
    private final long sessionVersion;
    VerifiedLocalAuthentication(String accountId,long sessionVersion,Collection<? extends GrantedAuthority> authorities){super(authorities);this.accountId=accountId;this.sessionVersion=sessionVersion;super.setAuthenticated(true);}
    @Override public Object getCredentials(){return "";} @Override public Object getPrincipal(){return accountId;} @Override public String getName(){return accountId;}
    public long sessionVersion(){return sessionVersion;}
    @Override public void setAuthenticated(boolean authenticated){if(!authenticated){super.setAuthenticated(false);return;}throw new IllegalArgumentException("Use the server-side authentication factory");}
}
