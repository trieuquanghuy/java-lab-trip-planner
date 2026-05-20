package com.tripplanner.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAutoConfigurationTest {

    private final JwtAutoConfiguration config = new JwtAutoConfiguration();

    @Test
    void jwtVerifier_createdWithValidSecret() {
        JwtProperties props = new JwtProperties();
        props.setSecret(JwtFixtures.TEST_SECRET);

        JwtVerifier verifier = config.jwtVerifier(props);

        assertThat(verifier).isNotNull();
    }

    @Test
    void jwtIssuer_createdWithValidSecret() {
        JwtProperties props = new JwtProperties();
        props.setSecret(JwtFixtures.TEST_SECRET);

        JwtIssuer issuer = config.jwtIssuer(props);

        assertThat(issuer).isNotNull();
    }

    @Test
    void jwtVerifier_throwsOnNullSecret() {
        JwtProperties props = new JwtProperties();
        // secret is null by default

        assertThatThrownBy(() -> config.jwtVerifier(props))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtIssuer_throwsOnNullSecret() {
        JwtProperties props = new JwtProperties();

        assertThatThrownBy(() -> config.jwtIssuer(props))
                .isInstanceOf(IllegalStateException.class);
    }
}
