// Source: 00-RESEARCH.md lines 1094-1118 + 00-PATTERNS.md Bucket B (reactive bean adaptation),
// 00-CONTEXT.md D-04.
//
// Two nested @Configuration classes gate filter registration on web application type:
//   - ServletConfig fires in SERVLET web apps (auth, trip, destination)
//   - ReactiveConfig fires in REACTIVE web apps (api-gateway)
// This is the SB-idiomatic way to support both stack types from a single shared lib.
//
// Pitfall 7 hard rule (Convention C7): do NOT register Spring's HTTP observation filter
// manually anywhere in the lib or in any service — Spring Boot 3.2+ auto-configures it
// via WebHttpHandlerBuilder.
//
// 2026-05-08 WR-02 fix (Phase 1 Wave 1 carryover-clear, per 01-CONTEXT.md original Open Question 1, RESOLVED):
//   reactive/servlet discrimination uses @ConditionalOnWebApplication, not @ConditionalOnClass.
//   See .planning/phases/00-monorepo-scaffolding/00-REVIEW.md WR-02 for the original report.
package com.tripplanner.observability;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfig {
        @Bean
        public FilterRegistrationBean<MdcEnrichmentFilter> mdcEnrichmentFilter(Tracer tracer) {
            FilterRegistrationBean<MdcEnrichmentFilter> bean =
                    new FilterRegistrationBean<>(new MdcEnrichmentFilter(tracer));
            // Run early but after Spring's tracing filter so currentSpan() is populated.
            bean.setOrder(Integer.MIN_VALUE + 100);
            return bean;
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveConfig {
        @Bean
        public ReactiveMdcEnrichmentFilter reactiveMdcEnrichmentFilter(Tracer tracer) {
            return new ReactiveMdcEnrichmentFilter(tracer);
        }
    }
}
