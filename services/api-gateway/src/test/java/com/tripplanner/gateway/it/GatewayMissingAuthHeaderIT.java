// Source: 01-RESEARCH.md "Validation Architecture" line 1278 (SC#2 row — AnonymousAccessIT renamed);
//         01-CONTEXT.md D-14 mandatory security test #1 (AnonymousCannotAccessAuthedEndpoints);
//         01-PATTERNS.md line 83.
//
// Convention C26-P1 (defense-in-depth IT). Threat: T-01-04 mitigation gateway-side leg
// (untrusted Authorization header surface — without one, gateway short-circuits).
//
// SC#2 (D-14 test #1): /api/trips/** with NO Authorization header → 401 application/problem+json
//   with code=auth.unauthorized; downstream stub receives ZERO requests (gateway short-circuits
//   before NettyRoutingFilter).
//
// ProblemDetail code JSON path note:
//   ProblemDetailAuthEntryPoint uses Spring Boot's auto-configured ObjectMapper with ProblemDetailJacksonMixin.
//   Extension properties serialize at $.code (flattened to root level).
//   The auth.unauthorized code IS correct per D-07.
//
// WebTestClient wiring note (Rule 1 fix — same pattern as GatewayRoutingIT):
//   @AutoConfigureWebTestClient replaced with @LocalServerPort + WebTestClient.bindToServer()
//   to avoid FailureAfterResponseCompletedException from ReactorContextTestExecutionListener.
package com.tripplanner.gateway.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tripplanner.gateway.it.support.GatewayItProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
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
@Tag("integration")
class GatewayMissingAuthHeaderIT {

    @LocalServerPort
    int port;

    WebTestClient webTestClient;

    @InjectWireMock("trip-service-stub")
    WireMockServer tripStub;

    @InjectWireMock("destination-service-stub")
    WireMockServer destinationStub;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
        tripStub.resetRequests();
        destinationStub.resetRequests();
        tripStub.stubFor(any(anyUrl()).willReturn(ok().withBody("{}").withHeader("Content-Type", "application/json")));
        destinationStub.stubFor(any(anyUrl()).willReturn(ok().withBody("{}").withHeader("Content-Type", "application/json")));
    }

    /**
     * SC#2 / D-14 test #1 — no Authorization header on authenticated route.
     * Gateway MUST return 401 application/problem+json with code=auth.unauthorized.
     * Downstream stub MUST receive ZERO requests (gateway short-circuits).
     */
    @Test
    void no_authorization_header_on_authed_route_returns_401() {
        webTestClient.get()
            .uri("/api/trips/anything")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("auth.unauthorized");

        // Gateway must short-circuit — downstream never reached.
        assertThat(tripStub.getAllServeEvents()).isEmpty();
    }

    /**
     * D-03 public-allowlist sanity check — public routes are NOT blocked by missing auth.
     */
    @Test
    void no_authorization_header_on_public_route_does_NOT_401() {
        webTestClient.get()
            .uri("/api/destinations/list")
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * Health endpoint must remain accessible without JWT (public allowlist / /__health/** rule).
     */
    @Test
    void health_endpoint_does_NOT_require_auth() {
        webTestClient.get()
            .uri("/__health")
            .exchange()
            .expectStatus().isOk();
    }
}
