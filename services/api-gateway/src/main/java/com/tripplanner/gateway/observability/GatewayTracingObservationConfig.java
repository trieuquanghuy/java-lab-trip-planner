// Spring Cloud Gateway 4.3.4 + Micrometer Tracing trace-propagation fix (Rule 2 — missing critical
// functionality).
//
// Problem: GatewayPropagatingSenderTracingObservationHandler is created by
// GatewayMetricsAutoConfiguration.ObservabilityConfiguration.GatewayTracingConfiguration and IS a
// Spring bean. However, it is NOT registered with the ObservationRegistry because of a bean-creation
// ordering issue:
//
//   1. ObservationAutoConfiguration runs and creates ObservationRegistry.
//   2. ObservationRegistryConfigurer.configure() is called at bean-creation time.
//   3. observationHandlers.orderedStream() tries to collect all ObservationHandler beans.
//   4. GatewayPropagatingSenderTracingObservationHandler depends on Tracer + Propagator which in
//      turn depend on the ObservationRegistry — CIRCULAR. Spring skips the handler silently.
//   5. Result: ObservedRequestHttpHeadersFilter starts observations but no handler injects
//      the W3C "traceparent" header into outgoing Reactor Netty requests.
//
// Fix: listen for ContextRefreshedEvent (fired after ALL beans are created and the context is
// fully initialized), then explicitly register the handler with the ObservationRegistry.
// ContextRefreshedEvent fires after SmartInitializingSingleton in Spring's lifecycle,
// ensuring no circular dependency issues.
//
// Additional fix (tests only, via GatewayTracingTestConfig): OpenTelemetryTracingAutoConfiguration
// .otelContextPropagators() collects TextMapPropagator beans via ObjectProvider. Due to
// auto-config bean ordering, the TextMapPropagator bean from PropagationWithBaggage is NOT
// available when otelContextPropagators is instantiated — producing NoopTextMapPropagator.
// GatewayTracingTestConfig provides an explicit ContextPropagators bean that wins via
// @ConditionalOnMissingBean, giving OtelPropagator a real W3CTraceContextPropagator.
//
// Source: 01-CONTEXT.md D-14 (SC#6 trace continuity test); 01-RESEARCH.md lines 1125-1153
//         (TraceContinuityIT validation requirement).
package com.tripplanner.gateway.observability;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayPropagatingSenderTracingObservationHandler;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Registers GatewayPropagatingSenderTracingObservationHandler with the ObservationRegistry
 * after the Spring context is fully refreshed. This avoids the circular-dependency ordering
 * issue that prevents the handler from being picked up by Spring Boot's
 * ObservationRegistryConfigurer during context startup.
 *
 * The ContextRefreshedEvent fires after all singletons are instantiated and all
 * SmartInitializingSingleton callbacks complete — guaranteeing no circular dependency.
 */
@Configuration
public class GatewayTracingObservationConfig implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private GatewayPropagatingSenderTracingObservationHandler gatewayTracingHandler;

    private volatile boolean registered = false;

    /**
     * Called after the Spring context is fully refreshed.
     * Explicitly adds the gateway tracing handler to the ObservationRegistry so that
     * ObservedRequestHttpHeadersFilter can inject W3C traceparent headers into downstream
     * Reactor Netty requests (SC#6 trace continuity).
     *
     * Uses a volatile boolean guard because ContextRefreshedEvent fires on every context
     * refresh (including child contexts), and we only want to register once.
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!registered && gatewayTracingHandler != null) {
            observationRegistry.observationConfig().observationHandler(gatewayTracingHandler);
            registered = true;
        }
    }
}
