package cn.servicehub.security;

import java.util.regex.Pattern;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** The only factory for the verified-IAM authentication marker used by SSO adapters. */
@Component
public class VerifiedIamAuthenticationFactory {
    private static final Pattern IAM_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private final PlatformAuthorizationResolver authorizations;
    public VerifiedIamAuthenticationFactory(PlatformAuthorizationResolver authorizations) { this.authorizations = authorizations; }
    public VerifiedIamAuthentication create(String iamUserId, String source) {
        if (iamUserId == null || !IAM_ID.matcher(iamUserId).matches()) throw new IllegalArgumentException("IAM subject is invalid");
        CurrentUser current = authorizations.resolve(iamUserId, source);
        return new VerifiedIamAuthentication(iamUserId, source, current.authorities().stream().map(SimpleGrantedAuthority::new).toList());
    }
}
