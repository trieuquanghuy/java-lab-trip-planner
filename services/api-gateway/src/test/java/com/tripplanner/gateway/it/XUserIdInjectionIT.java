// Source: 01-PATTERNS.md line 85 (file path); 01-RESEARCH.md Pattern 3 lines 471-545
//         (XUserIdInjectionGlobalFilter contract — strip + inject in authed branch, strip-only in public);
//         01-03-PLAN.md task 2 (gateway filter implementation).
//
// Threat: T-01-04 (X-User-Id spoofing — gateway-side defense). Counterpart to plan 01-04's
//         DirectServiceAccessWithoutGatewayReturns401IT (downstream-side defense).
//
// Convention C26-P1 (defense-in-depth IT).
//
// Pitfall 1 / T-01-04 gateway-side keystone:
//   Authenticated branch: client-supplied X-User-Id is STRIPPED, JWT sub is INJECTED.
//   Public branch: client-supplied X-User-Id is STRIPPED, nothing is INJECTED.
//   X-Request-Id is preserved if client supplied it; generated as UUID if absent.
//
// WebTestClient wiring note (Rule 1 fix — same pattern as GatewayRoutingIT):
//   @AutoConfigureWebTestClient replaced with @LocalServerPort + WebTestClient.bindToServer()
//   to avoid FailureAfterResponseCompletedException from ReactorContextTestExecutionListener
//   (spring-security-test) when processing authenticated requests that flow through
//   XUserIdInjectionGlobalFilter → ReactiveSecurityContextHolder.getContext().
package com.tripplanner.gateway.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.tripplanner.gateway.it.support.GatewayItProperties;
import com.tripplanner.jwt.JwtFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
class XUserIdInjectionIT {

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
     * Pitfall 1 / T-01-04 gateway-side keystone — authenticated branch.
     * Client sends X-User-Id: spoofed-id + X-User-Email: spoofed@evil.com with a valid JWT (sub=real-user-id).
     * Downstream stub MUST receive X-User-Id: real-user-id (from JWT sub), NOT the spoofed value.
     */
    @Test
    void authed_request_strips_client_xUserId_and_injects_from_jwt_sub() {
        String jwt = JwtFixtures.mintValid("real-user-id", "real@e.com");

        String responseXRequestId = webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + jwt)
            .header("X-User-Id", "spoofed-id")
            .header("X-User-Email", "spoofed@evil.com")
            .exchange()
            .expectStatus().isOk()
            .returnResult(Void.class)
            .getResponseHeaders()
            .getFirst("X-Request-Id");

        assertThat(tripStub.getAllServeEvents()).hasSize(1);
        LoggedRequest downstreamRequest = tripStub.getAllServeEvents().get(0).getRequest();

        // INJECTED: JWT sub replaces spoofed value.
        assertThat(downstreamRequest.getHeader("X-User-Id")).isEqualTo("real-user-id");
        // INJECTED: JWT email replaces spoofed value.
        assertThat(downstreamRequest.getHeader("X-User-Email")).isEqualTo("real@e.com");

        // X-Request-Id propagated end-to-end (generated since client didn't supply one).
        String downstreamRequestId = downstreamRequest.getHeader("X-Request-Id");
        assertThat(downstreamRequestId).isNotBlank();
        // The response X-Request-Id matches what the downstream received.
        assertThat(responseXRequestId).isEqualTo(downstreamRequestId);
    }

    /**
     * Pitfall 1 / T-01-04 — public route strip-only branch.
     * Client sends X-User-Id: spoofed-id on a public route (no JWT).
     * Downstream stub MUST NOT receive any X-User-Id header.
     */
    @Test
    void public_route_strips_client_xUserId_and_does_NOT_inject() {
        webTestClient.get()
            .uri("/api/destinations/list")
            .header("X-User-Id", "spoofed-id")
            .exchange()
            .expectStatus().isOk();

        assertThat(destinationStub.getAllServeEvents()).hasSize(1);
        LoggedRequest downstreamRequest = destinationStub.getAllServeEvents().get(0).getRequest();

        // Header was stripped; downstream must NOT see any X-User-Id.
        assertThat(downstreamRequest.getHeaders().getHeader("X-User-Id").isPresent()).isFalse();
    }

    /**
     * D-18 — client-supplied X-Request-Id is preserved end-to-end.
     */
    @Test
    void xRequestId_propagated_when_client_supplies_one() {
        webTestClient.get()
            .uri("/api/destinations/list")
            .header("X-Request-Id", "abc-123")
            .exchange()
            .expectStatus().isOk();

        assertThat(destinationStub.getAllServeEvents()).hasSize(1);
        assertThat(destinationStub.getAllServeEvents().get(0).getRequest().getHeader("X-Request-Id")).isEqualTo("abc-123");
    }

    /**
     * D-18 — gateway generates a UUID X-Request-Id when client omits it.
     */
    @Test
    void xRequestId_generated_when_client_omits() {
        String responseXRequestId = webTestClient.get()
            .uri("/api/destinations/list")
            .exchange()
            .expectStatus().isOk()
            .returnResult(Void.class)
            .getResponseHeaders()
            .getFirst("X-Request-Id");

        assertThat(destinationStub.getAllServeEvents()).hasSize(1);
        String downstreamRequestId = destinationStub.getAllServeEvents().get(0).getRequest().getHeader("X-Request-Id");

        assertThat(downstreamRequestId).isNotBlank();
        // Must parse as a valid UUID.
        assertDoesNotThrow(() -> UUID.fromString(downstreamRequestId),
            "Generated X-Request-Id must be a valid UUID; was: " + downstreamRequestId);
        // Response matches what downstream received.
        assertThat(responseXRequestId).isEqualTo(downstreamRequestId);
    }
}
