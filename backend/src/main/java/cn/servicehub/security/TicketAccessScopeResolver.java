package cn.servicehub.security;

import cn.servicehub.config.SecurityProperties;
import cn.servicehub.ticket.domain.TicketAccessScope;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Parses only server-resolved authorities into the first production ticket-scope package. */
@Component
public class TicketAccessScopeResolver {
    private static final Set<String> SCOPED_TICKET_ROLES = Set.of(
        "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_SERVICE_MANAGER",
        "ROLE_PLATFORM_ADMIN", "ROLE_AUDITOR");
    private final SecurityProperties properties;
    private final Environment environment;
    public TicketAccessScopeResolver(SecurityProperties properties, Environment environment) {
        this.properties = properties; this.environment = environment;
    }

    public TicketAccessScope resolve(CurrentUser user) {
        boolean directAllowed = properties.allowDirectTestIdentities() && !environment.acceptsProfiles(Profiles.of("prod"));
        if (!user.trustedAuthorizationContext() && !directAllowed) {
            throw new AccessDeniedException("A trusted ticket authorization context is required");
        }
        Set<String> roles = new LinkedHashSet<>(); Set<String> organizations = new LinkedHashSet<>();
        Set<String> catalogs = new LinkedHashSet<>(); Set<String> systems = new LinkedHashSet<>(); Set<String> cis = new LinkedHashSet<>();
        boolean failClosed = false;
        for (String authority : user.authorities()) {
            if (authority.startsWith("ROLE_")) { roles.add(authority); continue; }
            if (!authority.startsWith("DATA_SCOPE_")) continue;
            int separator = authority.indexOf(':', "DATA_SCOPE_".length());
            if (separator < 0 || separator == authority.length() - 1) { failClosed = true; continue; }
            String type = authority.substring("DATA_SCOPE_".length(), separator);
            String value = authority.substring(separator + 1);
            if (!value.matches("^[A-Za-z0-9._:-]{1,128}$")) { failClosed = true; continue; }
            switch (type) {
                case "ORGANIZATION" -> organizations.add(value);
                case "SERVICE", "SERVICE_CATALOG" -> catalogs.add(value);
                case "SERVICE_SYSTEM" -> systems.add(value);
                case "CONFIGURATION_ITEM" -> cis.add(value);
                case "QUEUE" -> failClosed = true;
                default -> failClosed = true;
            }
        }
        boolean scopedRole = roles.stream().anyMatch(SCOPED_TICKET_ROLES::contains);
        boolean legacyBypass = !user.trustedAuthorizationContext() && directAllowed;
        return new TicketAccessScope(user.iamUserId(), roles, organizations, catalogs, systems, cis,
            scopedRole, failClosed, legacyBypass);
    }
}
