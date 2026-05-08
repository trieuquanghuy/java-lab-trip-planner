// Spring Security WebFlux SecurityWebFilterChain — public-route allowlist + JWT-authenticated default.
//
// Source: 01-RESEARCH.md Pattern 2 (lines 346-469); 01-CONTEXT.md D-03 (public-route allowlist),
//         D-07 (401 RFC 7807); 01-PATTERNS.md Bucket C lines 570-602.
//
// T-01-01 (token replay across services): gateway validates JWT once via ReactiveJwtAuthenticationManager;
// downstream services re-validate via ServletJwtCommonFilter (defense-in-depth, D-02).
// T-01-04 (X-User-Id spoofing): NoOpServerSecurityContextRepository keeps the gateway stateless;
// downstream identity is established via XUserIdInjectionGlobalFilter (sibling file in this package).
// CVE-2025-41235 / Convention C33-P1: do NOT enable spring.cloud.gateway.server.webflux.trusted-proxies
// (handled in application.yml, not here).
package com.tripplanner.gateway.security;

import com.tripplanner.jwt.reactive.ReactiveJwtAuthenticationManager;
import com.tripplanner.jwt.reactive.ServerBearerTokenConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class WebFluxSecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtAuthenticationManager authManager,
            ServerBearerTokenConverter bearerConverter,
            ProblemDetailAuthEntryPoint entryPoint
    ) {
        // Stateless gateway — no session, no SecurityContext persistence between requests.
        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authManager);
        jwtFilter.setServerAuthenticationConverter(bearerConverter);
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .cors(c -> c.configurationSource(corsSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)           // bearer tokens, not cookies
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/__health/**", "/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/auth/login", "/api/auth/signup",
                                "/api/auth/verify", "/api/auth/refresh").permitAll()
                        .pathMatchers("/api/search/**").permitAll()
                        .pathMatchers("/api/destinations/**").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .build();
    }

    private UrlBasedCorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:5173"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        cfg.setExposedHeaders(List.of("X-Request-Id"));
        cfg.setAllowCredentials(true);       // allowCredentials=true required for cookie-based refresh flow
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
