// Source: 01-RESEARCH.md "Integration test — DirectServiceAccessWithoutGatewayReturns401" (lines 1078-1123) (mirror of trip-service version per 01-PATTERNS.md Bucket D 'identical config text');
//         01-CONTEXT.md D-13 (test stack — @SpringBootTest(webEnvironment=RANDOM_PORT) + MockMvc);
//         01-CONTEXT.md D-14 (Phase 1 owns this test — SC#4 / Pitfall 1 keystone);
//         01-PATTERNS.md Bucket D (lines 771-801).
//
// Pitfall 1 keystone (Pitfall C, 01-RESEARCH.md lines 914-927): "If that test is missing, the
//   phase is incomplete." This file IS the regression gate that closes Phase 1's SC#4.
//
// Convention C26-P1: every authenticated downstream path requires JWT — the test crafts the
//   exact threat model (T-01-04) the gateway is designed to prevent: a direct hit on
//   localhost:<port>/api/destinations/_ping carrying ONLY a forged X-User-Id header. Without
//   ServletJwtCommonFilter wired into the SecurityFilterChain, this would resolve a principal
//   from the spoofed header. With Pattern 4, it returns 401.
//
// The test also asserts /__health remains anonymously accessible — guards against accidental
// security regression that would lock out load balancer / smoke.sh probes (Phase 0 D-01).
package com.tripplanner.destination.security;

import com.tripplanner.jwt.JwtFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// [Rule 3 auto-fix] spring.flyway.enabled=false + spring.jpa.hibernate.ddl-auto=none +
// spring.datasource.url EmbeddedDatabase disables Flyway/JPA/PostgreSQL auto-config.
// This security IT only needs the web layer, servlet filter chain, and JWT auto-configuration.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.jwt.secret=phase-1-jwt-fixture-secret-32bytes!!",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:destinationtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DirectServiceAccessWithoutGatewayReturns401IT {

    @Autowired
    MockMvc mvc;

    @Test
    void direct_call_with_xUserId_and_no_jwt_returns_401() throws Exception {
        // Pitfall 1 keystone: a request that bypasses the gateway and hits this service directly
        // with a crafted X-User-Id header MUST be rejected. The gateway's strip+inject filter is
        // the first line of defense; this servlet filter is the second.
        // Uses a non-public path (anyRequest().authenticated()) to verify the filter rejects.
        mvc.perform(get("/api/internal/protected").header("X-User-Id", "spoofed-uuid"))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.code").value("auth.unauthorized"));
    }

    @Test
    void direct_call_with_no_headers_at_all_returns_401() throws Exception {
        mvc.perform(get("/api/internal/protected"))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.code").value("auth.unauthorized"));
    }

    @Test
    void health_endpoint_remains_anonymous_accessible() throws Exception {
        // Phase 0 D-01 / Convention C10 preservation guard: load balancer + smoke.sh need
        // anonymous access to /__health. Catches accidental security regressions that would
        // make the SecurityFilterChain accidentally lock down the health probe.
        mvc.perform(get("/__health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.service").value("destination-service"))
           .andExpect(jsonPath("$.phase").value(0));
    }
}
