// Source: 01-RESEARCH.md "Validation Architecture" line 1282 (SC#5 row);
//         01-RESEARCH.md Pattern 5 lines 670-800 (RequestRateLimiter math: replenishRate=30,
//         requestedTokens=900, burstCapacity=30 → first 30 requests pass, 31st returns 429);
//         01-CONTEXT.md D-05 (Phase 1 ships IP-only 30/15min; full IP+email is Phase 2);
//         01-CONTEXT.md D-14 mandatory security test #8 (LoginRateLimitTriggers, partial — IP leg only);
//         01-PATTERNS.md line 87.
//
// Threat: T-01-05 (Phase 1 leg — IP-only rate limit; full mitigation requires Phase 2 IP+email).
//         IP-only is bypassable via IP rotation; this is acknowledged Phase-1 trade-off (D-05).
//
// Convention C26-P1 (defense-in-depth IT).
//
// Rate-limiter override for test tractability:
//   Production replenishRate=30/burstCapacity=30/requestedTokens=900 enforces 30/15min steady state.
//   Test uses replenishRate=1/burstCapacity=30/requestedTokens=1 for tractable runtime assertion
//   that the WIRING works (31st request is rejected with 429). Production math is verified by
//   plan 01-03's application.yml grep gate — two separate validation concerns.
//   These overrides are defined in application-gateway-it-ratelimit.yml.
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SC#5 (D-14 test #8 partial — IP leg only): proves the rate-limiter rejects the 31st request
 * from the same IP within the burst window, returning 429 application/problem+json with
 * code=auth.rate_limited. Uses test-triple replenishRate=1/burst=30/requestedTokens=1 (defined
 * in application-gateway-it-ratelimit.yml) for tractable runtime; production 30/15min math is
 * verified separately by the application.yml grep gate in plan 01-03.
 *
 * Isolation: Testcontainers redis:7-alpine via @ServiceConnection ensures this test does NOT
 * interact with any compose-file Redis from plan 01-06. Each test run starts with a fresh bucket.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        GatewayItProperties.JWT_SECRET_PROPERTY,
        GatewayItProperties.EUREKA_DISABLED_PROPERTY,
        GatewayItProperties.TRACING_SAMPLING_PROPERTY
    }
)
@ActiveProfiles("gateway-it-ratelimit")
@Testcontainers
@EnableWireMock({
    @ConfigureWireMock(name = "auth-service-stub",
        portProperties = {GatewayItProperties.AUTH_STUB_PORT_PROPERTY}),
    @ConfigureWireMock(name = "trip-service-stub",
        portProperties = {GatewayItProperties.TRIP_STUB_PORT_PROPERTY}),
    @ConfigureWireMock(name = "destination-service-stub",
        portProperties = {GatewayItProperties.DEST_STUB_PORT_PROPERTY})
})
@Tag("integration")
class LoginRateLimiterIT {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @LocalServerPort
    int port;

    WebTestClient webTestClient;

    @InjectWireMock("auth-service-stub")
    WireMockServer authStub;

    @InjectWireMock("destination-service-stub")
    WireMockServer destinationStub;

    @BeforeEach
    void setUp() throws Exception {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
        authStub.resetRequests();
        destinationStub.resetRequests();
        authStub.stubFor(any(anyUrl()).willReturn(
            ok().withBody("{}").withHeader("Content-Type", "application/json")));
        destinationStub.stubFor(any(anyUrl()).willReturn(
            ok().withBody("{}").withHeader("Content-Type", "application/json")));

        // Flush all Redis rate-limiter keys between tests so each test starts with a fresh bucket.
        // Without this, request_rate_limiter.{key}.tokens / .timestamp keys from the previous test
        // leave the bucket empty, causing the 1st request of the next test to hit 429 immediately.
        redis.execInContainer("redis-cli", "FLUSHALL");
    }

    /**
     * SC#5 / D-14 test #8 — first 30 POSTs to /api/auth/login pass through, 31st is rejected.
     * Test triple: replenishRate=1/burstCapacity=30/requestedTokens=1 per gateway-it-ratelimit profile.
     */
    @Test
    void thirty_logins_pass_then_thirty_first_returns_429_with_problem_detail() {
        // First 30 requests must pass (burst bucket allows 30 tokens × cost=1 each).
        for (int i = 0; i < 30; i++) {
            webTestClient.post()
                .uri("/api/auth/login")
                .bodyValue(Map.of("email", "u@e.com", "password", "x"))
                .exchange()
                .expectStatus().isOk();
        }

        // 31st request finds the bucket empty → 429.
        webTestClient.post()
            .uri("/api/auth/login")
            .bodyValue(Map.of("email", "u@e.com", "password", "x"))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.code").isEqualTo("auth.rate_limited")
                // BL-01 negative-assertion regression gate (Plan 02-06 Task 6.3).
                .jsonPath("$.properties.code").doesNotExist();

        // 30 passed through; the 31st was rejected by the gateway before reaching the stub.
        assertThat(authStub.getAllServeEvents()).hasSize(30);
    }

    /**
     * SC#5 isolation — signup route has a separate Redis bucket from login.
     * Even after the login bucket is exhausted, /api/auth/signup still passes
     * (signup bucket starts fresh with its own replenishRate/burstCapacity).
     */
    @Test
    void signup_route_has_separate_bucket_from_login() {
        // Exhaust the login bucket.
        for (int i = 0; i < 30; i++) {
            webTestClient.post()
                .uri("/api/auth/login")
                .bodyValue(Map.of("email", "u@e.com", "password", "x"))
                .exchange()
                .expectStatus().isOk();
        }

        // Signup has its own bucket — first request must pass.
        webTestClient.post()
            .uri("/api/auth/signup")
            .bodyValue(Map.of("email", "new@e.com", "password", "pass123"))
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * Health endpoint must be unaffected by rate limiting even after login bucket is exhausted.
     * Health routes have no RequestRateLimiter filter.
     */
    @Test
    void health_endpoint_unaffected_by_rate_limit() {
        // Exhaust the login bucket first.
        for (int i = 0; i < 30; i++) {
            webTestClient.post()
                .uri("/api/auth/login")
                .bodyValue(Map.of("email", "u@e.com", "password", "x"))
                .exchange()
                .expectStatus().isOk();
        }

        // Health route has no rate limiter — must respond 200.
        webTestClient.get()
            .uri("/__health")
            .exchange()
            .expectStatus().isOk();
    }
}
