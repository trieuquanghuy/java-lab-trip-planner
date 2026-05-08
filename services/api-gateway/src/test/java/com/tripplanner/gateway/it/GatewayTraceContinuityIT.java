// Source: 01-RESEARCH.md "Validation Architecture" line 1283 (SC#6 row);
//         01-RESEARCH.md lines 1125-1153 (TraceContinuityIT exemplar);
//         01-CONTEXT.md D-19 (sampling.probability=1.0) + D-17 (MDC enrichment after auth);
//         01-PATTERNS.md line 88.
//         01-VALIDATION.md (the manual Zipkin UI check stays manual; this IT asserts MDC + traceparent only).
//
// Convention C26-P1 (defense-in-depth IT). Convention C29-P1 (gateway does NOT write userId to MDC —
//         only traceId/spanId/requestId; this test verifies the traceId field, not userId).
//
// SC#6 (D-14 trace continuity): a single traceId appears in BOTH:
//   (a) the gateway's MDC (captured via Logback ListAppender on the SCG logger hierarchy), AND
//   (b) the WireMock-recorded traceparent header on the downstream request.
// This proves Micrometer Tracing → OTel → W3C traceparent propagation through NettyRoutingFilter.
//
// WebTestClient wiring note (same pattern as GatewayRoutingIT):
//   @LocalServerPort + WebTestClient.bindToServer() avoids FailureAfterResponseCompletedException.
//
// Trace propagation fix note (Rule 2 deviation — GatewayTracingObservationConfig):
//   GatewayPropagatingSenderTracingObservationHandler is created by SCG auto-config but NOT
//   automatically registered with the ObservationRegistry due to a circular bean dependency
//   during ObservationRegistryConfigurer.configure(). GatewayTracingObservationConfig
//   (services/api-gateway/.../observability/) registers it via SmartInitializingSingleton
//   after all beans are initialized so that ObservedRequestHttpHeadersFilter can inject
//   W3C traceparent headers into outgoing Reactor Netty requests.
package com.tripplanner.gateway.it;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.tripplanner.gateway.it.support.GatewayItProperties;
import com.tripplanner.gateway.it.support.GatewayTracingTestConfig;
import com.tripplanner.jwt.JwtFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        GatewayItProperties.JWT_SECRET_PROPERTY,
        GatewayItProperties.EUREKA_DISABLED_PROPERTY,
        GatewayItProperties.TRACING_SAMPLING_PROPERTY
    }
)
@ActiveProfiles(GatewayItProperties.GATEWAY_IT_PROFILE)
@EnableWireMock({
    @ConfigureWireMock(name = "auth-service-stub",
        portProperties = {GatewayItProperties.AUTH_STUB_PORT_PROPERTY}),
    @ConfigureWireMock(name = "trip-service-stub",
        portProperties = {GatewayItProperties.TRIP_STUB_PORT_PROPERTY}),
    @ConfigureWireMock(name = "destination-service-stub",
        portProperties = {GatewayItProperties.DEST_STUB_PORT_PROPERTY})
})
@Import(GatewayTracingTestConfig.class)
@Tag("integration")
class GatewayTraceContinuityIT {

    private static final String TRACEPARENT_REGEX = "^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$";

    @LocalServerPort
    int port;

    WebTestClient webTestClient;

    @InjectWireMock("trip-service-stub")
    WireMockServer tripStub;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger scgLogger;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
        tripStub.resetRequests();
        tripStub.stubFor(any(anyUrl()).willReturn(
            ok().withBody("{}").withHeader("Content-Type", "application/json")));

        // Attach a Logback ListAppender to the Spring Cloud Gateway logger hierarchy.
        // Logback resolves child loggers' events upward by default (additivity=true),
        // so attaching at the root "org.springframework.cloud.gateway" captures all SCG events.
        //
        // SCG filter/handler classes (NettyRoutingFilter, FilteringWebHandler, etc.) log at
        // DEBUG/TRACE level. Set the SCG logger to DEBUG so events flow to the appender.
        // The original level is restored in @AfterEach.
        scgLogger = (Logger) LoggerFactory.getLogger("org.springframework.cloud.gateway");
        scgLogger.setLevel(Level.DEBUG);
        logAppender = new ListAppender<>();
        logAppender.start();
        scgLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (scgLogger != null && logAppender != null) {
            scgLogger.detachAppender(logAppender);
            logAppender.stop();
            scgLogger.setLevel(null);  // restore to inherited level
        }
    }

    /**
     * SC#6 — single traceId end-to-end:
     * The gateway's MDC traceId matches the W3C traceparent trace_id in the downstream request header.
     */
    @Test
    void single_traceId_appears_in_gateway_mdc_and_downstream_traceparent_header() {
        String jwt = JwtFixtures.mintValid("u-1", "u@e.com");

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + jwt)
            .exchange()
            .expectStatus().isOk();

        // 1. Assert downstream stub received the request with a well-formed traceparent.
        assertThat(tripStub.getAllServeEvents()).hasSize(1);
        String traceparent = tripStub.getAllServeEvents().get(0).getRequest().getHeader("traceparent");
        assertThat(traceparent)
            .as("downstream request must carry W3C traceparent header")
            .isNotBlank()
            .matches(TRACEPARENT_REGEX);

        // Extract the trace_id segment (32-char hex, second field in 00-traceId-parentId-flags).
        String downstreamTraceId = traceparent.split("-")[1];

        // 2. Assert at least one gateway log event was captured with a traceId in MDC.
        assertThat(logAppender.list)
            .as("no log events captured from org.springframework.cloud.gateway — check appender attachment")
            .isNotEmpty();

        // Find the first event with a non-blank traceId MDC field.
        ILoggingEvent traceEvent = logAppender.list.stream()
            .filter(e -> e.getMDCPropertyMap().containsKey("traceId")
                && !e.getMDCPropertyMap().get("traceId").isBlank())
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No gateway log event with traceId in MDC found. Events captured: " +
                logAppender.list.size() + ". This may mean Micrometer Tracing MDC enrichment " +
                "is not active or the appender attachment point is wrong."));

        String gatewayMdcTraceId = traceEvent.getMDCPropertyMap().get("traceId");

        // 3. The MDC traceId must match the traceparent trace_id sent to the downstream service.
        // Micrometer Tracing stores the traceId as 32-char lowercase hex; W3C traceparent uses same.
        assertThat(gatewayMdcTraceId)
            .as("gateway MDC traceId must match W3C traceparent trace_id sent downstream")
            .isEqualToIgnoringCase(downstreamTraceId);
    }
}
