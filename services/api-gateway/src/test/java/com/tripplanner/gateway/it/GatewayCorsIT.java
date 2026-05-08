// Source: 01-PATTERNS.md line 86 (file path);
//         01-CONTEXT.md D-03 (CORS allowlist http://localhost:5173, allowCredentials=true);
//         01-03-PLAN.md task 1 (WebFluxSecurityConfig.corsSource()).
//
// Threat: T-01-10 (CORS misconfig — must NOT echo wildcard origins;
//         must reject non-allowlisted origins on preflight).
//
// Convention C26-P1 (defense-in-depth IT).
//
// T-01-10 regression gate: confirms WebFluxSecurityConfig.corsSource() allows exactly
// http://localhost:5173 with credentials and rejects all other origins on preflight.
//
// WebTestClient wiring note (same pattern as GatewayRoutingIT):
//   @LocalServerPort + WebTestClient.bindToServer() avoids FailureAfterResponseCompletedException.
package com.tripplanner.gateway.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tripplanner.gateway.it.support.GatewayItProperties;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;

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
class GatewayCorsIT {

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
        tripStub.stubFor(any(anyUrl()).willReturn(
            ok().withBody("{}").withHeader("Content-Type", "application/json")));
        destinationStub.stubFor(any(anyUrl()).willReturn(
            ok().withBody("{}").withHeader("Content-Type", "application/json")));
    }

    /**
     * T-01-10 — allowlisted origin gets correct CORS response headers on preflight.
     * Origin: http://localhost:5173 is the only allowed origin per WebFluxSecurityConfig.corsSource().
     */
    @Test
    void preflight_from_allowlisted_origin_succeeds() {
        webTestClient.options()
            .uri("/api/trips/anything")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "authorization")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
            .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")
            .expectHeader().exists("Access-Control-Allow-Methods");
    }

    /**
     * T-01-10 — disallowed origin must NOT get Access-Control-Allow-Origin in the response.
     * Spring Security WebFlux returns 403 Forbidden for CORS preflight from non-allowlisted origins.
     */
    @Test
    void preflight_from_disallowed_origin_is_rejected() {
        webTestClient.options()
            .uri("/api/trips/anything")
            .header("Origin", "http://evil.example.com")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectStatus().isForbidden()
            .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }

    /**
     * D-03 / T-01-10 — simple GET from allowlisted origin returns X-Request-Id in exposed headers.
     * WebFluxSecurityConfig.corsSource() exposes X-Request-Id so the SPA can read it for correlation.
     */
    @Test
    void simple_GET_from_allowlisted_origin_includes_xRequestId_in_exposed_headers() {
        String exposeHeaders = webTestClient.get()
            .uri("/api/destinations/list")
            .header("Origin", "http://localhost:5173")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
            .returnResult(Void.class)
            .getResponseHeaders()
            .getFirst("Access-Control-Expose-Headers");

        org.assertj.core.api.Assertions.assertThat(exposeHeaders)
            .as("Access-Control-Expose-Headers must include X-Request-Id")
            .isNotNull()
            .contains("X-Request-Id");
    }
}
