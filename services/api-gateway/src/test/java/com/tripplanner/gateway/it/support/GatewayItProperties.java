// Source: 01-CONTEXT.md D-11 (test JWT secret override) + D-13 (test stack) + D-19 (tracing sampling);
//         01-RESEARCH.md lines 1078-1153 (DirectServiceAccessIT + TraceContinuityIT property exemplars);
//         01-PATTERNS.md Bucket C lines 524-568 (test deps in services/api-gateway/build.gradle.kts).
//
// Convention C35-P1 (header citation). This support class is consumed by every gateway IT in plan 01-05.
// Convention C26-P1 (defense-in-depth integration tests) — these properties drive the @SpringBootTest
// boot of the gateway with WireMock-stubbed downstream services so each IT exercises the FULL filter chain.
//
// ROUTE URI OVERRIDE APPROACH (deviation from plan's @DynamicPropertySource method):
//   Plan 01-05 originally specified using DynamicPropertyRegistry to set route URIs by index
//   (spring.cloud.gateway.server.webflux.routes[N].uri). This was incompatible with Spring Boot's
//   @ConfigurationProperties binder — partial list-index overrides are not supported; GatewayProperties
//   uses spring.cloud.gateway.server.webflux as its prefix (Spring Cloud 2025.0 migration) while
//   application.yml still uses the deprecated spring.cloud.gateway prefix (pending application.yml
//   migration to new prefix). Test properties caused UnboundConfigurationPropertiesException.
//
//   The correct approach (implemented here):
//   1. @ActiveProfiles("gateway-it") activates src/test/resources/application-gateway-it.yml
//   2. application-gateway-it.yml redefines all Phase 1 routes with URIs referencing
//      property placeholders: e.g. uri: http://localhost:${auth.stub.port}
//   3. @ConfigureWireMock(portProperties = {"auth.stub.port"}) registers the WireMock stub
//      port BEFORE @ConfigurationProperties binding (via WireMockContextCustomizer which
//      runs as a ContextCustomizer, before bean creation). Spring resolves ${auth.stub.port}
//      at binding time.
//
// No production source is touched — this helper lives entirely under src/test/.
package com.tripplanner.gateway.it.support;

import com.tripplanner.jwt.JwtFixtures;

public final class GatewayItProperties {

    /** JWT signing secret matching libs/jwt-common's testFixtures (D-11). */
    public static final String JWT_SECRET_PROPERTY = "auth.jwt.secret=" + JwtFixtures.TEST_SECRET;

    /** Always sample 100% in tests so SC#6 trace continuity is deterministic (D-19). */
    public static final String TRACING_SAMPLING_PROPERTY = "management.tracing.sampling.probability=1.0";

    /** Disable Eureka client at boot — gateway routing in tests is static, no service discovery. */
    public static final String EUREKA_DISABLED_PROPERTY = "eureka.client.enabled=false";

    /**
     * Spring profile that activates application-gateway-it.yml, which redefines all Phase 1
     * gateway routes to use WireMock stub port placeholders (${auth.stub.port},
     * ${trip.stub.port}, ${destination.stub.port}).
     *
     * Use @ActiveProfiles(GatewayItProperties.GATEWAY_IT_PROFILE) on each IT class.
     */
    public static final String GATEWAY_IT_PROFILE = "gateway-it";

    /**
     * WireMock stub port property names as registered by @ConfigureWireMock(portProperties={...}).
     * These property names MUST match the placeholder references in application-gateway-it.yml.
     */
    public static final String AUTH_STUB_PORT_PROPERTY    = "auth.stub.port";
    public static final String TRIP_STUB_PORT_PROPERTY    = "trip.stub.port";
    public static final String DEST_STUB_PORT_PROPERTY    = "destination.stub.port";

    private GatewayItProperties() {}
}
