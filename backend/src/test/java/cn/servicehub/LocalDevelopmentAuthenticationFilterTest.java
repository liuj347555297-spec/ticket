package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.servicehub.config.LocalDevelopmentAuthenticationFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Guards the local-only identity selector against arbitrary IAM-id or role injection. */
class LocalDevelopmentAuthenticationFilterTest {

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void mapsOnlyFixedLocalProfilesToFixedPrincipals() throws Exception {
        Authentication requester = apply("requester");
        assertEquals("iam-u-local-requester", requester.getName());
        assertEquals(1, requester.getAuthorities().size());
        assertEquals("ROLE_REQUESTER", requester.getAuthorities().iterator().next().getAuthority());

        Authentication manager = apply("service-manager");
        assertEquals("iam-u-local-service-manager", manager.getName());
        assertFalse(manager.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_PLATFORM_ADMIN")));
    }

    @Test
    void rejectsUnknownSelectorInsteadOfFallingBackToAdministrator() throws Exception {
        assertNull(apply("iam-u-anyone|ROLE_PLATFORM_ADMIN"));
    }

    private Authentication apply(String selector) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-ServiceHub-Dev-Identity", selector);
        AtomicReference<Authentication> observed = new AtomicReference<>();
        new LocalDevelopmentAuthenticationFilter().doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
            observed.set(SecurityContextHolder.getContext().getAuthentication()));
        return observed.get();
    }
}
