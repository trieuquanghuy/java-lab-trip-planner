// Pitfall A: flyway-database-postgresql is REQUIRED at runtime — Flyway 10 modularized PG support.
// Without it the service errors at startup with 'Unsupported Database: PostgreSQL 16.x'.
//
// Pitfall 7 / Convention C6: micrometer-tracing-bom is pinned ONCE in libs/observability — DO NOT
// import it here. The BOM flows transitively via the dependencyManagement on libs/observability.
//
// D-30 / Convention C17: spring-cloud-dependencies BOM is the 2025.0.x (Northfields) train.
//
// Source: 00-RESEARCH.md lines 430-457 (verbatim); 00-PATTERNS.md Bucket E (lines 504-547).
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":libs:observability"))
    implementation(project(":libs:error-handling"))
    implementation(project(":libs:api-contracts"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)        // Pitfall A — MANDATORY for PG 16 support
    runtimeOnly(libs.postgresql.jdbc)
    implementation(libs.bundles.observability)

    // Phase 2 (D-19/D-20/D-22 + RESEARCH §Standard Stack — versions managed by SB 3.5.14 BOM)
    implementation(project(":libs:jwt-common"))                   // JwtVerifier + JwtIssuer + JwtFixtures
    implementation(libs.spring.boot.starter.security)             // SecurityFilterChain, BCryptPasswordEncoder
    implementation(libs.spring.boot.starter.mail)                 // JavaMailSender (D-03 plain-text)
    implementation(libs.spring.boot.starter.validation)           // @Email/@NotBlank/@Size (D-18)
    implementation(libs.spring.boot.starter.data.redis)           // StringRedisTemplate servlet variant (D-06)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)   // catalogued; first integration test arrives in Phase 1+
    testImplementation(libs.spring.security.test)
    testImplementation(testFixtures(project(":libs:jwt-common")))     // JwtFixtures.TEST_SECRET
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.greenmail.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")     // Convention from Phase 1 01-02 SUMMARY
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
    }
}

// Source: 02-06-PLAN.md Task 6.1(f). Wires `-PincludeTags=security` to scope the test suite to
// @Tag("security") integration tests only (the NFR-05 merge gate). Without -P, the full suite runs.
tasks.test {
    useJUnitPlatform {
        val includeTags = providers.gradleProperty("includeTags").orNull
        if (includeTags != null) {
            includeTags(*includeTags.split(",").toTypedArray())
        }
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
