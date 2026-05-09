// Source: 02-06-PLAN.md Task 6.1(e); 02-CONTEXT.md D-06 (5 attempts/15min, 6th trips); D-07 (clear on success).
//
// Slice IT — runs LoginRateLimiter against a real Testcontainer Redis (Lua execution requires real Redis;
// MockRedisTemplate would not exercise the INCR+EXPIRE atomicity guarantee that prevents the immortal-counter race).
//
// Validates the Lua atomicity contract:
//   - Counter rises monotonically across recordFailure() calls.
//   - exceeded() returns false for counts 0..4, true for count >= 5 (D-06: 6th attempt trips).
//   - clear() resets the counter (D-07: success path).
package com.tripplanner.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = LoginRateLimiterTest.TestApp.class)
@Testcontainers
@ActiveProfiles("ratelimit-slice")
class LoginRateLimiterTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Autowired LoginRateLimiter limiter;

    @Test
    void recordFailure_increments_and_exceeded_trips_at_5() {
        String ip = "203.0.113.10";
        String email = "rl@example.com";
        // Clean slate (per-test isolation across the class).
        limiter.clear(ip, email);

        // Counts 0..4 → exceeded() is false. recordFailure() pushes count to 1..5.
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.exceeded(ip, email))
                    .as("after %d failures, exceeded() must still be false (D-06 6th-attempt-trips)", i)
                    .isFalse();
            limiter.recordFailure(ip, email);
        }
        // Count is now 5; the next read trips (this is what the 6th login attempt would see).
        assertThat(limiter.exceeded(ip, email))
                .as("after 5 failures, exceeded() must trip on the 6th read (D-06)")
                .isTrue();

        // D-07: clear() resets the counter.
        limiter.clear(ip, email);
        assertThat(limiter.exceeded(ip, email))
                .as("clear() must reset the counter (D-07)")
                .isFalse();
    }

    /**
     * Minimal Spring context — Spring Boot autoconfig brings up StringRedisTemplate from the
     * @ServiceConnection-wired Redis container, and component scan picks up LoginRateLimiter
     * (it lives in this package — com.tripplanner.auth.service).
     */
    @SpringBootApplication
    static class TestApp {}
}
