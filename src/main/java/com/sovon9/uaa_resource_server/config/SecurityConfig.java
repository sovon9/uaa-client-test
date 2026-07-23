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

@EnableWebSecurity
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(request->request.anyRequest().authenticated())
                .oauth2ResourceServer(oauth->oauth.jwt(jwt->jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(config->config.authenticationEntryPoint(((request, response, authException) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), authException.getMessage()))))
                .build();
    }

    /**
     *
     * @return
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // 1. This default converter extracts the "scope" or "scp" claim and adds the "SCOPE_" prefix
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter customConverter = new JwtAuthenticationConverter();
        customConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Step A: Get all the default SCOPE_ authorities
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
