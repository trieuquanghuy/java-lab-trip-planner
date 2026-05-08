// D-11: gateway has NO Flyway, NO datasource. D-30: spring-cloud-dependencies BOM is 2025.0.x (Northfields).
//
// Source: 00-PATTERNS.md Bucket D (lines 432-459); 00-RESEARCH.md Pattern 1 (build shape) + Pattern 3 (static-URI gateway).
//
// Reactive WebFlux stack: the Spring Cloud Gateway starter pulls in the WebFlux starter transitively.
// Do NOT add the servlet web starter (org.springframework.boot:spring-boot-starter / web variant) —
// Spring Cloud Gateway crashes at startup with "Spring MVC found on classpath, which is incompatible
// with Spring Cloud Gateway".
//
// Pitfall 7 / Convention C6: micrometer-tracing-bom is pinned ONCE in libs/observability — DO NOT import it
// here. The BOM flows transitively via the dependencyManagement on libs/observability.
//
// Phase 1 additions: spring-boot-starter-security (Spring Security WebFlux per 01-CONTEXT.md D-03)
// and spring-boot-starter-data-redis-reactive (RedisRateLimiter per D-06). Both are reactive
// variants — adding spring-boot-starter-security pulls Spring Security WebFlux because the
// gateway's only servlet API on classpath is via spring-cloud-starter-gateway's transitive deps,
// and Spring Boot's auto-config picks the reactive Security stack when DispatcherHandler is the
// primary web entry point.
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":libs:observability"))
    implementation(project(":libs:error-handling"))
    implementation(project(":libs:api-contracts"))
    implementation(project(":libs:jwt-common"))                          // NEW Phase 1

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)                    // NEW Phase 1 (Spring Security WebFlux)
    implementation(libs.spring.boot.starter.data.redis.reactive)         // NEW Phase 1 (RedisRateLimiter backing store, per D-06)
    implementation(libs.spring.cloud.starter.gateway)            // reactive gateway (NOT the WebMVC variant)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.bundles.observability)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)                        // NEW Phase 1
    testImplementation(libs.spring.boot.testcontainers)                  // NEW Phase 1 (Redis testcontainer)
    testImplementation(libs.testcontainers.junit.jupiter)                // NEW Phase 1
    testImplementation(testFixtures(project(":libs:jwt-common")))        // NEW Phase 1 (JwtFixtures from Wave 0b)
    testImplementation(libs.wiremock.spring.boot)                        // NEW Phase 1 (downstream stubs in routing tests)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
    }
}
