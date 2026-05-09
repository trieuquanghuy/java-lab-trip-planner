// Source: 01-RESEARCH.md "Validation Architecture" line 1279 (SC#3 row — InvalidTokenIT renamed);
//         01-CONTEXT.md D-14 mandatory security test #2 (InvalidTokenIsRejected);
//         01-PATTERNS.md line 84.
//
// Threats covered:
//   T-01-02 (algorithm confusion) — alg=none and alg=RS256 tokens parse-fail at JwtVerifier.
//   T-01-04 (X-User-Id header tampering, gateway-side leg) — invalid tokens never reach downstream.
//
// Convention C27-P1 (modern jjwt API only — verifier rejects unknown alg by construction).
//
// SC#3 + T-01-02: 6 cases of invalid/forged JWT all return 401 application/problem+json with the
// correct error code; downstream stub receives ZERO requests in every case.
//
// ProblemDetail code JSON path note:
//   ProblemDetailAuthEntryPoint uses Spring Boot's auto-configured ObjectMapper with ProblemDetailJacksonMixin.
//   Extension properties serialize at $.code (flattened to root level).
//
// WebTestClient wiring note (Rule 1 fix — same pattern as GatewayRoutingIT):
//   @AutoConfigureWebTestClient replaced with @LocalServerPort + WebTestClient.bindToServer()
//   to avoid FailureAfterResponseCompletedException from ReactorContextTestExecutionListener.
package com.tripplanner.gateway.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tripplanner.gateway.it.support.GatewayItProperties;
import com.tripplanner.jwt.JwtFixtures;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;

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
class GatewayForgedJwtIT {

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
        tripStub.stubFor(any(anyUrl()).willReturn(ok().withBody("{}").withHeader("Content-Type", "application/json")));
    }

    /**
     * SC#3 — forged signature (signed with a different key) → 401 auth.invalid_token.
     * Downstream stub MUST receive ZERO requests.
     */
    @Test
    void forged_signature_returns_401_invalid_token() {
        String bad = JwtFixtures.mintWrongSig("u-1", "u@e.com");

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + bad)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("auth.invalid_token");

        assertThat(tripStub.getAllServeEvents()).isEmpty();
    }

    /**
     * SC#3 — expired token → 401 auth.token_expired.
     * Downstream stub MUST receive ZERO requests.
     */
    @Test
    void expired_token_returns_401_token_expired() {
        String exp = JwtFixtures.mintExpired("u-1", "u@e.com");

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + exp)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("auth.token_expired");

        assertThat(tripStub.getAllServeEvents()).isEmpty();
    }

    /**
     * SC#3 — malformed token (not a JWT at all) → 401 auth.invalid_token.
     * Downstream stub MUST receive ZERO requests.
     */
    @Test
    void malformed_token_returns_401_invalid_token() {
        String mal = JwtFixtures.mintMalformed();

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + mal)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("auth.invalid_token");

        assertThat(tripStub.getAllServeEvents()).isEmpty();
    }

    /**
     * T-01-02 — alg=none (unsigned) token → 401 auth.invalid_token.
     * JwtVerifier modern API (Convention C27-P1) calls Jwts.parser().verifyWith(secretKey)
     * which throws UnsupportedJwtException for unsigned tokens → JwtAuthenticationException
     * → 401 auth.invalid_token. Downstream stub MUST receive ZERO requests.
     */
    @Test
    void alg_none_token_returns_401_invalid_token() {
        // Jwts.builder() without .signWith() produces an unsigned (alg=none) JWT.
        String algNone = Jwts.builder()
            .subject("u-1")
            .claim("email", "u@e.com")
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .compact();

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + algNone)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("auth.invalid_token");

        assertThat(tripStub.getAllServeEvents()).isEmpty();
    }

    /**
     * T-01-02 — RS256-signed token presented to an HS256-only verifier → 401 auth.invalid_token.
     * JwtVerifier's verifyWith(SecretKey) only accepts HMAC-signed tokens; an RSA-signed token
     * causes signature verification to fail (algorithm mismatch) → 401 auth.invalid_token.
     * Downstream stub MUST receive ZERO requests.
     */
    @Test
    void wrong_alg_rs256_token_returns_401_invalid_token() {
        // Generate a fresh 2048-bit RSA keypair and sign with it.
        KeyPair rsaKeyPair = Jwts.SIG.RS256.keyPair().build();
        String rs256Token = Jwts.builder()
            .subject("u-1")
            .claim("email", "u@e.com")
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
            .compact();

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + rs256Token)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("auth.invalid_token");

        assertThat(tripStub.getAllServeEvents()).isEmpty();
    }

    /**
     * T-01-02 / SC#3 — token with missing 'sub' claim → 401 auth.invalid_token.
     * JwtVerifier throws JwtAuthenticationException("token missing 'sub' claim") when the
     * subject is absent → 401 auth.invalid_token. Downstream stub MUST receive ZERO requests.
     */
    @Test
    void missing_sub_claim_returns_401_invalid_token() {
        SecretKey key = Keys.hmacShaKeyFor(JwtFixtures.TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String noSub = Jwts.builder()
            .claim("email", "u@e.com")
            .claim("ver", true)
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(key)
            .compact();

        webTestClient.get()
            .uri("/api/trips/anything")
            .header("Authorization", "Bearer " + noSub)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("auth.invalid_token");

        assertThat(tripStub.getAllServeEvents()).isEmpty();
    }
}
