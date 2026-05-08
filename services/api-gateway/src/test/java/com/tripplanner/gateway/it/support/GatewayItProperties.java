// Source: 01-CONTEXT.md D-11 (test JWT secret override) + D-13 (test stack) + D-19 (tracing sampling);
//         01-RESEARCH.md lines 1078-1153 (DirectServiceAccessIT + TraceContinuityIT property exemplars);
//         01-PATTERNS.md Bucket C lines 524-568 (test deps in services/api-gateway/build.gradle.kts).
//
// Convention C35-P1 (header citation). This support class is consumed by every gateway IT in plan 01-05.
// Convention C26-P1 (defense-in-depth integration tests) — these properties drive the @SpringBootTest
// boot of the gateway with WireMock-stubbed downstream services so each IT exercises the FULL filter chain.
//
// ROUTE INDEX NOTE (auto-fix Rule 1): The application.yml contains 12 routes total:
//   Indices 0-3:  health routes (health-gateway, health-auth, health-trip, health-destination)
//   Indices 4-8:  auth routes  (auth-login, auth-signup, auth-verify, auth-refresh, auth-other)
//   Indices 9-10: destination routes (search, destinations)
//   Index  11:    trip route   (trips)
// The plan documented indices 0-7 assuming no health routes, but production application.yml has
// 4 health routes first. This helper uses the correct offsets (4-11) for Phase 1 routes.
//
// No production source is touched — this helper lives entirely under src/test/.
package com.tripplanner.gateway.it.support;

import com.tripplanner.jwt.JwtFixtures;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class GatewayItProperties {

    /** JWT signing secret matching libs/jwt-common's testFixtures (D-11). */
    public static final String JWT_SECRET_PROPERTY = "auth.jwt.secret=" + JwtFixtures.TEST_SECRET;

    /** Always sample 100% in tests so SC#6 trace continuity is deterministic (D-19). */
    public static final String TRACING_SAMPLING_PROPERTY = "management.tracing.sampling.probability=1.0";

    /** Disable Eureka client at boot — gateway routing in tests is static, no service discovery. */
    public static final String EUREKA_DISABLED_PROPERTY = "eureka.client.enabled=false";

    /** Random server port — Spring Boot picks one (D-13: webEnvironment = RANDOM_PORT). */
    public static final String RANDOM_SERVER_PORT_PROPERTY = "server.port=0";

    private GatewayItProperties() {}

    /**
     * Rewrites the eight Phase-1 gateway routes from production URIs (http://auth-service:8081,
     * http://trip-service:8082, http://destination-service:8083) to point at WireMock stub ports.
     * <p>
     * Spring Cloud Gateway reads {@code spring.cloud.gateway.server.webflux.routes[index].uri}.
     * Indices are 0-based across ALL routes in application.yml:
     * <ul>
     *   <li>0-3:  health routes (not overridden — health tests use WireMock too if needed, but
     *             are not part of Phase 1 security ITs)</li>
     *   <li>4:    auth-login    → authStubPort</li>
     *   <li>5:    auth-signup   → authStubPort</li>
     *   <li>6:    auth-verify   → authStubPort</li>
     *   <li>7:    auth-refresh  → authStubPort</li>
     *   <li>8:    auth-other    → authStubPort</li>
     *   <li>9:    search        → destinationStubPort</li>
     *   <li>10:   destinations  → destinationStubPort</li>
     *   <li>11:   trips         → tripStubPort</li>
     * </ul>
     * <p>
     * Each IT calls this from a {@code @DynamicPropertySource} method after WireMock stub ports are
     * known. The route-id order is invariant — verified by application.yml grep in plan 01-03
     * acceptance criteria.
     */
    public static void applyWireMockUriOverrides(
            DynamicPropertyRegistry registry,
            int authStubPort,
            int tripStubPort,
            int destinationStubPort) {
        String authUri        = "http://localhost:" + authStubPort;
        String tripUri        = "http://localhost:" + tripStubPort;
        String destinationUri = "http://localhost:" + destinationStubPort;

        // Auth routes (indices 4..8 per application.yml — health routes occupy 0..3)
        registry.add("spring.cloud.gateway.server.webflux.routes[4].uri", () -> authUri);
        registry.add("spring.cloud.gateway.server.webflux.routes[5].uri", () -> authUri);
        registry.add("spring.cloud.gateway.server.webflux.routes[6].uri", () -> authUri);
        registry.add("spring.cloud.gateway.server.webflux.routes[7].uri", () -> authUri);
        registry.add("spring.cloud.gateway.server.webflux.routes[8].uri", () -> authUri);
        // Search + destinations (indices 9..10) → destination-service stub
        registry.add("spring.cloud.gateway.server.webflux.routes[9].uri",  () -> destinationUri);
        registry.add("spring.cloud.gateway.server.webflux.routes[10].uri", () -> destinationUri);
        // Trips (index 11) → trip-service stub
        registry.add("spring.cloud.gateway.server.webflux.routes[11].uri", () -> tripUri);
    }
}
