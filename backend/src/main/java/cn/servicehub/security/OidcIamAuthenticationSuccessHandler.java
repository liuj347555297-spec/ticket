package cn.servicehub.security;

import cn.servicehub.config.IamSsoProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Exchanges a validated OIDC login for a server-side IAM-subject session; roles never come from ID-token claims. */
@Component
@ConditionalOnProperty(prefix = "servicehub.iam-sso", name = "enabled", havingValue = "true")
public class OidcIamAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final IamSsoProperties properties;
    private final VerifiedIamAuthenticationFactory factory;
    public OidcIamAuthenticationSuccessHandler(IamSsoProperties properties, VerifiedIamAuthenticationFactory factory) {
        this.properties = properties; this.factory = factory; setDefaultTargetUrl("/"); setAlwaysUseDefaultTargetUrl(true);
    }
    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) throw new ServletException("OIDC authentication is required");
        Object value = oauth.getPrincipal().getAttributes().get(properties.iamUserIdAttribute());
        if (!(value instanceof String iamUserId)) throw new ServletException("OIDC assertion does not contain the configured IAM subject");
        VerifiedIamAuthentication verified = factory.create(iamUserId, "OIDC");
        SecurityContext context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(verified); SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);
        super.onAuthenticationSuccess(request, response, verified);
    }
}
