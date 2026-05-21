// Source: 01-RESEARCH.md Pattern 4 (lines 547-663, especially companion ServletSecurityConfig at lines 644-663);
//         01-CONTEXT.md D-02 (JWT validated twice — gateway + downstream defense-in-depth);
//         01-CONTEXT.md D-04 (downstream populates SecurityContextHolder; @AuthenticationPrincipal UserContext);
//         01-PATTERNS.md Bucket D (lines 771-801).
//
// SecurityFilterChain composition:
//   - permitAll: /__health, /__health/**, /actuator/health, /actuator/health/**, /actuator/info
//                (preserves Phase 0 D-01 / Convention C10 anonymous health endpoint)
//   - authenticated(): anyRequest()
//   - ServletJwtCommonFilter wired BEFORE UsernamePasswordAuthenticationFilter (T-01-11 mitigation —
//     explicit ordering, not relying on FilterRegistrationBean order alone)
//   - csrf disabled (stateless JSON API)
//   - SessionCreationPolicy.STATELESS (no JSESSIONID cookie)
//   - exceptionHandling.authenticationEntryPoint(RestAuthenticationEntryPoint) — RFC 7807 401s
//
// Pitfall 1 keystone (Pitfall C, 01-RESEARCH.md lines 914-927): this filter MUST run on every
// authenticated path, regardless of whether the call came through the gateway. Phase 1's
// DirectServiceAccessWithoutGatewayReturns401IT (Task 4.3) is the regression gate.
package com.tripplanner.trip.security;

import com.tripplanner.jwt.servlet.ServletJwtCommonFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class ServletSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   FilterRegistrationBean<ServletJwtCommonFilter> jwtFilterReg,
                                                   RestAuthenticationEntryPoint entryPoint) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/__health", "/__health/**",
                                "/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
