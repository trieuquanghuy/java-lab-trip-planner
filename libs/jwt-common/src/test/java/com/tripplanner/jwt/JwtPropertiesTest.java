package com.tripplanner.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    @Test
    void getterAndSetter_work() {
        JwtProperties props = new JwtProperties();
        assertThat(props.getSecret()).isNull();

        props.setSecret("my-secret-value");
        assertThat(props.getSecret()).isEqualTo("my-secret-value");
    }
}
