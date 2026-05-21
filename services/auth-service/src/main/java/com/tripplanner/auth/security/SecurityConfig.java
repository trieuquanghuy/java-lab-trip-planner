// Source: services/trip-service/.../security/ServletSecurityConfig.java (Phase 1 analog — third instance of
//         the pattern: trip-service / destination-service mirror; auth-service is the third).
//         02-RESEARCH.md §Code Examples lines 1027-1073;
//         02-CONTEXT.md D-19 (BCrypt cost 12 production), D-12 (refresh-cookie scope semantics in controller).
//
// SecurityFilterChain composition:
//   - permitAll: /__health, /__health/**, /actuator/health, /actuator/health/**, /actuator/info,
//                AND Phase 2 public auth endpoints: /api/auth/{signup,verify,login,refresh}
//   - authenticated(): anyRequest() — covers /api/auth/logout (D-11) and any future authenticated path
//   - ServletJwtCommonFilter wired BEFORE UsernamePasswordAuthenticationFilter (T-01-11 mitigation —
//     explicit ordering; FilterRegistrationBean order alone is not enough)
//   - csrf disabled (stateless JSON API; refresh-cookie SameSite=Strict mitigates CSRF per docs/05 §9.3)
//   - SessionCreationPolicy.STATELESS (no JSESSIONID cookie)
//   - exceptionHandling.authenticationEntryPoint(RestAuthenticationEntryPoint) — RFC 7807 401s
//
// BCryptPasswordEncoder bean: production cost 12 (D-19). Test profile (Plan 05's TestSecurityConfig)
// supplies a `@Bean @Primary` cost-4 override to keep IT runs fast.
package com.tripplanner.auth.security;

import com.tripplanner.jwt.servlet.ServletJwtCommonFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** D-19: bcrypt cost 12 in production / dev / docker profiles. Test profile overrides via TestSecurityConfig. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   FilterRegistrationBean<ServletJwtCommonFilter> jwtFilterReg,
                                                   RestAuthenticationEntryPoint entryPoint) throws Exception {
        // Plan 02-06 Rule 1 fix: disable global servlet-level registration of the JWT filter
        // and let the SecurityFilterChain own it via addFilterBefore. Without this, the filter
        // ran TWICE (once at servlet container level, once inside Spring Security) and Spring
        // Security's SecurityContextHolderFilter overwrote the SecurityContext set by the
        // first invocation — yielding 401 'Authentication required' for valid bearer tokens
        // on /api/auth/logout. Trip-service / destination-service did not catch this because
        // their Phase 1 ITs only exercised the no-JWT path, not the valid-JWT-passes path.
        jwtFilterReg.setEnabled(false);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/__health", "/__health/**",
                                "/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus",
                                // Phase 2 PUBLIC auth endpoints (D-12 — gateway also permits these in Phase 1)
                                "/api/auth/signup", "/api/auth/verify",
                                "/api/auth/login", "/api/auth/refresh"
                        ).permitAll()
                        // /api/auth/logout requires JWT (D-11). Open Q1 RESOLVED: NO /me endpoint.
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
