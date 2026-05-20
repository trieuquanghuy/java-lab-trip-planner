package com.tripplanner.jwt.reactive;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ServerBearerTokenConverterTest {

    private final ServerBearerTokenConverter converter = new ServerBearerTokenConverter();

    @Test
    void validBearerHeader_returnsAuthentication() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some-token-value")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .assertNext(auth -> {
                    assertThat(auth.isAuthenticated()).isFalse();
                    assertThat(auth.getCredentials()).isEqualTo("some-token-value");
                    assertThat(auth.getPrincipal()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void caseInsensitiveBearer_returnsAuthentication() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "bearer my-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .assertNext(auth -> assertThat(auth.getCredentials()).isEqualTo("my-token"))
                .verifyComplete();
    }

    @Test
    void noAuthorizationHeader_returnsEmpty() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/trips").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .verifyComplete(); // empty Mono
    }

    @Test
    void basicAuthHeader_returnsEmpty() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void headerTooShort_returnsEmpty() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bear")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void bearerWithEmptyToken_returnsEmpty() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer    ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void bearerTokenWithWhitespace_trimmed() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer   my-token  ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .assertNext(auth -> assertThat(auth.getCredentials()).isEqualTo("my-token"))
                .verifyComplete();
    }
}
