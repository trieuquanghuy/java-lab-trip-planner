// Source: 01-PATTERNS.md (file path — line 86 implied; 01-RESEARCH.md "Anti-Patterns" lines 865-876
//         section "Returning bare 429 with empty body" — this IT prevents that regression);
//         01-CONTEXT.md D-07 (RFC 7807 ProblemDetail for 401 + 429);
//         01-03-PLAN.md task 2 (RateLimitProblemDetailFilter); task 1 (ProblemDetailAuthEntryPoint).
//
// Convention C26-P1 (defense-in-depth IT).
//
// D-07 RFC 7807 contract: every gateway-emitted error response is application/problem+json with
// a non-null code extension field. Covers 401 missing JWT, 401 forged JWT, 401 expired JWT, and
// the T-01-12 regression gate (error body must not leak stack traces or internal class names).
//
// ProblemDetail code JSON path note:
//   ProblemDetailAuthEntryPoint uses Spring Boot's auto-configured ObjectMapper with ProblemDetailJacksonMixin.
//   Extension properties serialize at $.code (flattened to root level).
//   The 429 ProblemDetail rendering is asserted in LoginRateLimiterIT (Test 1), not duplicated here.
//
// WebTestClient wiring note (same pattern as GatewayRoutingIT):
//   @LocalServerPort + WebTestClient.bindToServer() avoids FailureAfterResponseCompletedException.
package com.tripplanner.gateway.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tripplanner.gateway.it.support.GatewayItProperties;
import com.tripplanner.jwt.JwtFixtures;
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
class GatewayProblemDetailRenderingIT {

    @LocalServerPort
    int port;

    WebTestClient webTestClient;

    @InjectWireMock("trip-service-stub")
    WireMockServer tripStub;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
        tripStub.resetRequests();
        tripStub.stubFor(any(anyUrl()).willReturn(
            ok().withBody("{}").withHeader("Content-Type", "application/json")));
    }

    /**
     * D-07 — missing JWT renders RFC 7807 application/problem+json with code=auth.unauthorized.
     */
    @Test
    void missing_jwt_renders_rfc7807_with_code_field() {
        webTestClient.get()
            .uri("/api/trips/anything")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isNotEmpty()
                .jsonPath("$.code").isEqualTo("auth.unauthorized");
    }

    /**
     * D-07 — forged JWT renders RFC 7807 application/problem+json with code=auth.invalid_token.
     */
    @Test
    void forged_jwt_renders_rfc7807_with_invalid_token_code() {
        String bad = JwtFixtures.mintWrongSig("u-1", "u@e.com");

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + bad)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isNotEmpty()
                .jsonPath("$.code").isEqualTo("auth.invalid_token");
    }

    /**
     * D-07 — expired JWT renders RFC 7807 application/problem+json with code=auth.token_expired.
     */
    @Test
    void expired_jwt_renders_rfc7807_with_token_expired_code() {
        String expired = JwtFixtures.mintExpired("u-1", "u@e.com");

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + expired)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isNotEmpty()
                .jsonPath("$.code").isEqualTo("auth.token_expired");
    }

    /**
     * T-01-12 regression gate — error body MUST NOT leak stack trace or internal implementation details.
     * ProblemDetailAuthEntryPoint emits only the ErrorCode + message; no exception chain.
     */
    @Test
    void error_body_does_NOT_leak_stack_trace_or_internal_details() {
        webTestClient.get()
            .uri("/api/trips/anything")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody(String.class)
            .consumeWith(result -> {
                String body = result.getResponseBody();
                assertThat(body)
                    .as("error body must not contain Exception class names")
                    .doesNotContain("Exception")
                    .doesNotContain("at com.tripplanner")
                    .doesNotContain("Caused by");
            });
    }
}
