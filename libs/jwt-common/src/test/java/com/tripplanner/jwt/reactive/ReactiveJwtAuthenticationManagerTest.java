package com.tripplanner.jwt.reactive;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.jwt.JwtFixtures;
import com.tripplanner.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveJwtAuthenticationManagerTest {

    private ReactiveJwtAuthenticationManager manager;

    @BeforeEach
    void setUp() {
        JwtVerifier verifier = new JwtVerifier(JwtFixtures.TEST_SECRET);
        manager = new ReactiveJwtAuthenticationManager(verifier);
    }

    @Test
    void validToken_returnsAuthenticatedToken() {
        String token = JwtFixtures.mintValid("user-1", "user@example.com");
        Authentication input = new ServerBearerTokenConverter.BearerTokenAuthentication(token);

        Mono<Authentication> result = manager.authenticate(input);

        StepVerifier.create(result)
                .assertNext(auth -> {
                    assertThat(auth.isAuthenticated()).isTrue();
                    assertThat(auth.getPrincipal()).isInstanceOf(UserContext.class);
                    UserContext ctx = (UserContext) auth.getPrincipal();
                    assertThat(ctx.userId()).isEqualTo("user-1");
                    assertThat(ctx.email()).isEqualTo("user@example.com");
                    assertThat(ctx.verified()).isTrue();
                    assertThat(auth.getCredentials()).isEqualTo(token);
                })
                .verifyComplete();
    }

    @Test
    void expiredToken_returnsBadCredentialsError() {
        String token = JwtFixtures.mintExpired("user-1", "user@example.com");
        Authentication input = new ServerBearerTokenConverter.BearerTokenAuthentication(token);

        Mono<Authentication> result = manager.authenticate(input);

        StepVerifier.create(result)
                .expectErrorMatches(e -> e instanceof BadCredentialsException
                        && e.getMessage().contains("expired"))
                .verify();
    }

    @Test
    void invalidToken_returnsBadCredentialsError() {
        String token = JwtFixtures.mintWrongSig("user-1", "user@example.com");
        Authentication input = new ServerBearerTokenConverter.BearerTokenAuthentication(token);

        Mono<Authentication> result = manager.authenticate(input);

        StepVerifier.create(result)
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void malformedToken_returnsBadCredentialsError() {
        String token = JwtFixtures.mintMalformed();
        Authentication input = new ServerBearerTokenConverter.BearerTokenAuthentication(token);

        Mono<Authentication> result = manager.authenticate(input);

        StepVerifier.create(result)
                .expectError(BadCredentialsException.class)
                .verify();
    }
}
