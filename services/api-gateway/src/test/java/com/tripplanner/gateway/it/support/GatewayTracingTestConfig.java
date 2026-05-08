// Source: 01-RESEARCH.md lines 1125-1153 (TraceContinuityIT validation requirement);
//         01-CONTEXT.md D-19 (trace continuity test requires real W3C propagation).
//
// Problem: OpenTelemetryTracingAutoConfiguration.otelContextPropagators(ObjectProvider<TextMapPropagator>)
// collects TextMapPropagator beans via ObjectProvider.orderedStream(). In the test context,
// PropagationWithBaggage / PropagationWithoutBaggage (the auto-configured TextMapPropagator
// providers) are not being picked up in time — otelContextPropagators is instantiated with an
// empty list, producing ContextPropagators.create(NoopTextMapPropagator).
//
// Fix: provide an explicit ContextPropagators bean in the test context. Spring Boot's
// otelContextPropagators bean has @ConditionalOnMissingBean, so this test bean takes precedence
// and provides ContextPropagators.create(W3CTraceContextPropagator) — the correct W3C propagator.
// The OtelPropagator(ContextPropagators, Tracer) constructor then captures
// W3CTraceContextPropagator instead of NoopTextMapPropagator.
//
// This bean only activates via @ActiveProfiles("gateway-it") or "gateway-it-ratelimit" so it
// has no effect on production or other test contexts.
//
// Convention C35-P1 (source citation). Convention C29-P1 (test config in support package).
package com.tripplanner.gateway.it.support;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only configuration that supplies a correctly-wired {@link ContextPropagators} bean
 * so that {@code OtelPropagator} injects the W3C {@code traceparent} header into downstream
 * requests (SC#6 trace continuity).
 *
 * Spring Boot's {@code otelContextPropagators} bean has {@code @ConditionalOnMissingBean}, so
 * this test bean wins and provides {@code W3CTraceContextPropagator} to the OTel bridge.
 */
@TestConfiguration
public class GatewayTracingTestConfig {

    @Bean
    public ContextPropagators w3cContextPropagators() {
        return ContextPropagators.create(W3CTraceContextPropagator.getInstance());
    }
}
