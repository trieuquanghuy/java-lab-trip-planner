// Source: 02-06-PLAN.md Task 6.1(b); 02-PATTERNS.md §TestSecurityConfig; 02-CONTEXT.md D-19.
//
// D-19: Production SecurityConfig.passwordEncoder() returns BCryptPasswordEncoder(12) (~250ms per hash).
// Tests need cost-4 (~5ms) to keep IT runs fast. This @TestConfiguration is imported on
// AuthIntegrationTestBase via @Import(TestSecurityConfig.class), and the @Primary bean wins over
// the production cost-12 bean during component-scan resolution.
//
// IMPORTANT: This affects ONLY hashes encoded inside tests (e.g. signup creating a fresh User).
// The dummy DUMMY_BCRYPT12_HASH constant in AuthService.java is a literal cost-12 hash; the user-not-found
// timing-defense path will pay ~250ms even in tests. This is an accepted trade-off (documented in plan).
package com.tripplanner.auth.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** D-19 — bcrypt cost 4 in tests (~5ms per hash, vs ~250ms at production cost 12). */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public PasswordEncoder testPasswordEncoder() {
        return new BCryptPasswordEncoder(4);
    }
}
