package cn.servicehub.config;

import cn.servicehub.web.ApiErrorWriter;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, IamSsoProperties.class,
    cn.servicehub.localauth.LocalAuthProperties.class,
    cn.servicehub.ticket.application.TicketPaginationProperties.class,
    cn.servicehub.workflow.team.SupportQueueIdempotencyProperties.class})
public class SecurityConfiguration {

    /**
     * Prevents Spring Boot from creating a generated local username/password. The platform never
     * creates local accounts; an IAM authentication provider is introduced only with the approved
     * OIDC contract.
     */
    @Bean
    AuthenticationProvider iamAuthenticationProviderPlaceholder() {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                return null;
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return false;
            }
        };
    }

    /**
     * IAM OIDC is opt-in and only enabled after its client registration is supplied by deployment.
     * This chain remains deny-by-default: no controller or actuator endpoint is anonymously exposed.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter apiErrorWriter,
                                            org.springframework.beans.factory.ObjectProvider<LocalDevelopmentAuthenticationFilter> localDevelopmentAuthenticationFilter,
                                            org.springframework.beans.factory.ObjectProvider<cn.servicehub.security.OidcIamAuthenticationSuccessHandler> oidcSuccessHandler) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setHeaderName("X-CSRF-TOKEN");
        http
            .cors(Customizer.withDefaults())
            // The callback endpoint has no browser session and validates a signed, short-lived
            // HMAC envelope itself. Every browser mutation continues to require CSRF.
            // SPA submits the raw token from the XSRF-TOKEN cookie in the X-CSRF-TOKEN header.
            // The explicit handler keeps this double-submit contract compatible with a JSON SPA.
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/v1/integrations/alerts/**"))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation().migrateSession())
            .requestCache(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/alerts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("ACTUATOR_VIEW")
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(apiErrorWriter::writeUnauthenticated)
                .accessDeniedHandler(apiErrorWriter::writeForbidden))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        var oidcHandler = oidcSuccessHandler.getIfAvailable();
        if (oidcHandler != null) http.oauth2Login(login -> login.successHandler(oidcHandler));
        localDevelopmentAuthenticationFilter.ifAvailable(filter -> http.addFilterBefore(filter, AnonymousAuthenticationFilter.class));
        http.addFilterAfter(new CsrfCookieBootstrapFilter(), CsrfFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        var allowedHeaders = new java.util.ArrayList<>(List.of("Content-Type", "X-CSRF-TOKEN", "X-Request-Id", "Idempotency-Key", "If-Match"));
        if (properties.devHeaderEnabled()) allowedHeaders.add("X-ServiceHub-Dev-Identity");
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new cn.servicehub.security.LocalAccountPasswordEncoder();
    }
}
