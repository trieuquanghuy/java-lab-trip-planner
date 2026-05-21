// Source: 01-RESEARCH.md "Recommended Project Structure" + Pitfall I (WR-02 carryover);
//         01-PATTERNS.md Bucket B lines 422-491; 01-CONTEXT.md D-04 (downstream uses
//         SecurityContextHolder; gateway uses Spring Security WebFlux).
//
// Convention C31-P1: discriminate REACTIVE vs SERVLET via @ConditionalOnWebApplication
//   (NOT @ConditionalOnClass — that's the Phase 0 WR-02 mistake we're correcting in this plan).
// Filter ordering rule (Pitfall I + libs/observability ServletConfig): MdcEnrichmentFilter
//   runs at Integer.MIN_VALUE + 100; ServletJwtCommonFilter runs at Integer.MIN_VALUE + 200
//   so traceId/spanId are already in MDC when this filter writes userId.
package com.tripplanner.jwt;

import com.tripplanner.jwt.reactive.ReactiveJwtAuthenticationManager;
import com.tripplanner.jwt.reactive.ServerBearerTokenConverter;
import com.tripplanner.jwt.servlet.ServletJwtCommonFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtVerifier.class)
    public JwtVerifier jwtVerifier(JwtProperties props) {
        return new JwtVerifier(props.getSecret());
    }

    @Bean
    @ConditionalOnMissingBean(JwtIssuer.class)
    public JwtIssuer jwtIssuer(JwtProperties props) {
        return new JwtIssuer(props.getSecret());
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfig {

        @Bean
        public FilterRegistrationBean<ServletJwtCommonFilter> servletJwtFilter(JwtVerifier verifier,
                com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
            // Inject Spring Boot's auto-configured ObjectMapper so ProblemDetailJacksonMixin is
            // registered — flattens ProblemDetail extension properties (e.g. "code") to JSON root.
            FilterRegistrationBean<ServletJwtCommonFilter> bean =
                    new FilterRegistrationBean<>(new ServletJwtCommonFilter(verifier, objectMapper));
            // Disable standalone servlet registration — this filter MUST only run inside Spring
            // Security's chain (via addFilterBefore in SecurityConfig). Running it as a standalone
            // servlet filter causes SecurityContextHolderFilter.setDeferredContext() to overwrite
            // the authentication we set, resulting in spurious 401s (Spring Security 6.x behavior).
            bean.setEnabled(false);
            bean.setOrder(Integer.MIN_VALUE + 200);
            return bean;
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveConfig {

        @Bean
        public ServerBearerTokenConverter serverBearerTokenConverter() {
            return new ServerBearerTokenConverter();
        }

        @Bean
        public ReactiveJwtAuthenticationManager reactiveJwtAuthenticationManager(JwtVerifier verifier) {
            return new ReactiveJwtAuthenticationManager(verifier);
        }
    }
}
