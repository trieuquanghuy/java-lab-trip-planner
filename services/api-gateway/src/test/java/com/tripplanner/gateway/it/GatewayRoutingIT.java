// Source: 01-RESEARCH.md "Validation Architecture" lines 1275-1277 (SC#1 routing rows);
//         01-CONTEXT.md D-08 (static URI routing) + D-15 (every Phase 1 SC has an IT);
//         01-PATTERNS.md line 82 (file path).
//
// Convention C26-P1 (defense-in-depth integration test — exercises the full reactive filter chain).
// Convention C9-P1 (static URI routing — overridden in tests via @ActiveProfiles + test YAML).
// Threat: T-01-01 mitigation (Path-predicate misrouting) — asserts each Phase 1 route forwards to
//         the documented downstream service.
//
// SC#1 (D-15): proves /api/auth/** → auth-service stub, /api/trips/** → trip-service stub,
//              /api/search/** and /api/destinations/** → destination-service stub.
//
// Note on WebTestClient wiring (@LocalServerPort vs @AutoConfigureWebTestClient):
//   @AutoConfigureWebTestClient with RANDOM_PORT creates a WebTestClient that uses
//   HttpHandlerConnector (in-process, not real HTTP) when spring-security-test is on
//   the classpath and ReactorContextTestExecutionListener is active. For authenticated
//   GET requests routed through AuthorizationWebFilter + XUserIdInjectionGlobalFilter
//   (which calls ReactiveSecurityContextHolder.getContext()), the listener's after-test
//   cleanup triggers a second subscription to the already-completed Netty FluxReceive
//   (FailureAfterResponseCompletedException). Wiring WebTestClient directly to the
//   local server URL (via @LocalServerPort) bypasses this issue because the HTTP
//   exchange happens over real TCP and is fully complete before cleanup runs.
package com.tripplanner.gateway.it;

import com.github.tomakehurst.wiremock.WireMockServer;
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

import java.util.Map;

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
class GatewayRoutingIT {

    @LocalServerPort
    int port;

    WebTestClient webTestClient;

    @InjectWireMock("auth-service-stub")
    WireMockServer authStub;

    @InjectWireMock("trip-service-stub")
    WireMockServer tripStub;

    @InjectWireMock("destination-service-stub")
    WireMockServer destinationStub;

    @BeforeEach
    void setUp() {
        // Wire to real HTTP port to avoid ReactorContextTestExecutionListener interference
        // (see class-level comment for details).
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();

        authStub.resetRequests();
        tripStub.resetRequests();
        destinationStub.resetRequests();
        // Stub any request to return 200 OK — we're testing routing, not business logic.
        authStub.stubFor(any(anyUrl()).willReturn(ok().withBody("{\"stub\":\"auth\"}").withHeader("Content-Type", "application/json")));
        tripStub.stubFor(any(anyUrl()).willReturn(ok().withBody("{\"stub\":\"trip\"}").withHeader("Content-Type", "application/json")));
        destinationStub.stubFor(any(anyUrl()).willReturn(ok().withBody("{\"stub\":\"destination\"}").withHeader("Content-Type", "application/json")));
    }

    /**
     * SC#1 — /api/auth/login → auth-service stub only; trip/destination stubs untouched.
     */
    @Test
    void routes_api_auth_login_to_auth_service() {
        webTestClient.post()
            .uri("/api/auth/login")
            .bodyValue(Map.of("email", "u@e.com", "password", "x"))
            .exchange()
            .expectStatus().isOk();

        assertThat(authStub.getAllServeEvents()).hasSize(1);
        assertThat(authStub.getAllServeEvents().get(0).getRequest().getUrl()).isEqualTo("/api/auth/login");
        assertThat(tripStub.getAllServeEvents()).isEmpty();
        assertThat(destinationStub.getAllServeEvents()).isEmpty();
    }

    /**
     * SC#1 — /api/trips/** → trip-service stub only; auth/destination stubs untouched.
     */
    @Test
    void routes_api_trips_anything_to_trip_service() {
        String jwt = JwtFixtures.mintValid("u-1", "u@e.com");

        webTestClient.get()
            .uri("/api/trips/anything-suffix-here")
            .header("Authorization", "Bearer " + jwt)
            .exchange()
            .expectStatus().isOk();

        assertThat(tripStub.getAllServeEvents()).hasSize(1);
        assertThat(tripStub.getAllServeEvents().get(0).getRequest().getUrl()).isEqualTo("/api/trips/anything-suffix-here");
        assertThat(authStub.getAllServeEvents()).isEmpty();
        assertThat(destinationStub.getAllServeEvents()).isEmpty();
    }

    /**
     * SC#1 — /api/search/** → destination-service stub only; auth/trip stubs untouched.
     * Public route — no JWT needed.
     */
    @Test
    void routes_api_search_anything_to_destination_service() {
        webTestClient.get()
            .uri("/api/search/anything-suffix-here")
            .exchange()
            .expectStatus().isOk();

        assertThat(destinationStub.getAllServeEvents()).hasSize(1);
        assertThat(destinationStub.getAllServeEvents().get(0).getRequest().getUrl()).isEqualTo("/api/search/anything-suffix-here");
        assertThat(authStub.getAllServeEvents()).isEmpty();
        assertThat(tripStub.getAllServeEvents()).isEmpty();
    }
}
