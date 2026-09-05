package cn.servicehub.config;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Local-only development identity. It exists exclusively in the local-dev profile and accepts
 * connections from the loopback interface only; production profiles continue to require IAM.
 */
@Component
@Profile("local-dev")
@ConditionalOnProperty(prefix = "servicehub.security", name = "dev-header-enabled", havingValue = "true")
public class LocalDevelopmentAuthenticationFilter extends OncePerRequestFilter {
    /** This header selects one fixed fixture only; it never transports an IAM id or role claim. */
    static final String IDENTITY_HEADER = "X-ServiceHub-Dev-Identity";
    private static final List<SimpleGrantedAuthority> ADMIN_AUTHORITIES = List.of(
        new SimpleGrantedAuthority("ROLE_REQUESTER"), new SimpleGrantedAuthority("ROLE_FIRST_LINE_SUPPORT"),
        new SimpleGrantedAuthority("ROLE_SECOND_LINE_SUPPORT"), new SimpleGrantedAuthority("ROLE_SERVICE_MANAGER"),
        new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"), new SimpleGrantedAuthority("ROLE_AUDITOR"),
        new SimpleGrantedAuthority("ROLE_ACTUATOR_VIEW"));
    private static final Map<String, LocalIdentity> IDENTITIES = Map.of(
        "requester", new LocalIdentity("iam-u-local-requester", List.of("ROLE_REQUESTER")),
        "first-line", new LocalIdentity("iam-u-local-first-line", List.of("ROLE_FIRST_LINE_SUPPORT")),
        "service-manager", new LocalIdentity("iam-u-local-service-manager", List.of("ROLE_SERVICE_MANAGER", "ROLE_FIRST_LINE_SUPPORT")),
        "admin", new LocalIdentity("iam-u-local-admin", ADMIN_AUTHORITIES.stream().map(SimpleGrantedAuthority::getAuthority).toList()));

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        try {
            return !InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (Exception ignored) {
            return true;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }
        String requestedProfile = request.getHeader(IDENTITY_HEADER);
        LocalIdentity identity = IDENTITIES.get(requestedProfile == null || requestedProfile.isBlank() ? "admin" : requestedProfile);
        if (identity == null) {
            // Do not fall back to a privileged fixture when a caller supplies an unknown selector.
            chain.doFilter(request, response);
            return;
        }
        var authorities = identity.authorities().stream().map(SimpleGrantedAuthority::new).toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(identity.iamUserId(), "N/A", authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private record LocalIdentity(String iamUserId, List<String> authorities) { }
}
