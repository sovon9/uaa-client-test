package com.sovon9.uaa_resource_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  WHAT IS THIS SERVICE?
 * ═══════════════════════════════════════════════════════════════════════════
 *  This is an OAuth2 RESOURCE SERVER — a service that exposes protected REST
 *  endpoints (e.g. /employee, /products) and trusts tokens issued by our
 *  Authorization Server (auth-service running on http://localhost:9000).
 *
 *  FLOW OVERVIEW:
 *  ┌─────────────┐   1. GET /oauth2/token    ┌──────────────────┐
 *  │   Client    │ ─────────────────────────► │  auth-service    │
 *  │ (Postman /  │ ◄───────────────────────── │  (port 9000)     │
 *  │  frontend)  │   2. JWT access_token      └──────────────────┘
 *  └─────────────┘
 *         │
 *         │  3. GET /employee/1
 *         │     Authorization: Bearer <JWT>
 *         ▼
 *  ┌──────────────────┐
 *  │  uaa-resource-   │  4. Validates JWT signature using auth-server's
 *  │  server (this)   │     public key (fetched from issuer-uri/.well-known)
 *  │  (port 8080)     │  5. Extracts scope + roles from JWT claims
 *  └──────────────────┘  6. Grants or denies access based on authorities
 *
 *  KEY PROPERTIES (application.properties):
 *    spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000
 *    → Spring fetches the public RSA key from http://localhost:9000/oauth2/jwks
 *      on startup and uses it to verify every incoming JWT signature.
 *      No shared secret, no DB lookup — pure public-key cryptography.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@EnableWebSecurity
@Configuration
@EnableMethodSecurity   // enables @PreAuthorize / @PostAuthorize on controller methods
public class SecurityConfig {

    /**
     * ───────────────────────────────────────────────────────────────────────
     *  SECURITY FILTER CHAIN
     * ───────────────────────────────────────────────────────────────────────
     *  Defines the HTTP security rules for every incoming request.
     *
     *  Rules applied in order:
     *
     *  1. authorizeHttpRequests → anyRequest().authenticated()
     *     Every endpoint in this service requires a valid JWT Bearer token.
     *     No public endpoints (add .requestMatchers("/public/**").permitAll()
     *     here if needed in the future).
     *
     *  2. oauth2ResourceServer → jwt → jwtAuthenticationConverter(...)
     *     Tells Spring this server accepts JWT Bearer tokens (not opaque tokens,
     *     not sessions). On every request:
     *       a. Extracts the "Authorization: Bearer <token>" header
     *       b. Verifies the JWT signature using the auth-server's public key
     *       c. Checks the token is not expired (exp claim)
     *       d. Passes the JWT through jwtAuthenticationConverter() to extract
     *          authorities (scopes + roles) — see that method below
     *
     *  3. exceptionHandling → authenticationEntryPoint
     *     Without this, Spring would return a 302 redirect to a login page
     *     when a request has no/invalid token — which makes no sense for a
     *     REST API. This overrides it to always return 401 Unauthorized JSON.
     *
     *  WHAT HAPPENS WHEN A REQUEST ARRIVES:
     *    ✅ Valid JWT with correct authorities → 200 OK, endpoint executes
     *    ❌ No token / expired token          → 401 Unauthorized
     *    ❌ Valid token but wrong role/scope   → 403 Forbidden
     * ───────────────────────────────────────────────────────────────────────
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(request -> request.anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), authException.getMessage()))
                )
                .build();
    }

    /**
     * ───────────────────────────────────────────────────────────────────────
     *  JWT AUTHENTICATION CONVERTER
     * ───────────────────────────────────────────────────────────────────────
     *  Purpose: Translate JWT claims → Spring Security GrantedAuthority list.
     *
     *  WHY IS THIS NEEDED?
     *  By default, Spring only reads the "scope" (or "scp") claim from the JWT
     *  and maps each value to a "SCOPE_xxx" authority. For example:
     *    JWT scope claim: ["demo.read"]  →  authority: SCOPE_demo.read
     *
     *  BUT our auth-service also injects a custom "roles" claim into the JWT:
     *    JWT roles claim: ["ROLE_ADMIN"]  →  authority: ROLE_ADMIN
     *
     *  Without this converter, Spring ignores the "roles" claim entirely, which
     *  means @PreAuthorize("hasRole('ADMIN')") would always fail even for admins.
     *
     *  WHAT THIS CONVERTER DOES (step by step):
     *
     *  Step A — scopesConverter.convert(jwt)
     *    Reads the "scope" claim and produces SCOPE_ prefixed authorities:
     *    e.g.  scope: ["demo.read"]  →  [SCOPE_demo.read]
     *    Used for: @PreAuthorize("hasAuthority('SCOPE_demo.read')")
     *
     *  Step B — jwt.getClaimAsStringList("roles")
     *    Directly reads our custom "roles" claim from the JWT payload.
     *    e.g.  roles: ["ROLE_ADMIN"]  →  ["ROLE_ADMIN"] as strings
     *
     *  Step C — map to SimpleGrantedAuthority
     *    Converts each role string to a GrantedAuthority object.
     *    e.g.  "ROLE_ADMIN"  →  SimpleGrantedAuthority("ROLE_ADMIN")
     *    Used for: @PreAuthorize("hasRole('ADMIN')")
     *             (hasRole auto-prepends ROLE_, so ADMIN matches ROLE_ADMIN)
     *
     *  FINAL AUTHORITY LIST EXAMPLE (for admin user using demo-app client):
     *    JWT claims: { scope: ["demo.read"], roles: ["ROLE_ADMIN"] }
     *    Authorities: [SCOPE_demo.read, ROLE_ADMIN]
     *
     *  NOTE ON GRANT TYPES:
     *    authorization_code flow → real user logged in → "roles" claim present
     *    client_credentials flow → no user (machine)   → "roles" claim absent,
     *                                                     only SCOPE_ authorities
     * ───────────────────────────────────────────────────────────────────────
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Reads the "scope" claim and adds "SCOPE_" prefix to each value
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter customConverter = new JwtAuthenticationConverter();
        customConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Step A: Get all the default SCOPE_ authorities from the scope claim
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopesConverter.convert(jwt));

            // Step B: Extract our custom "roles" claim from the JWT
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                // Step C: Convert each role string into a GrantedAuthority and add it to the list
                authorities.addAll(roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()));
            }

            return authorities;
        });

        return customConverter;
    }

}
