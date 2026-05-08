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

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)   // catalogued; first integration test arrives in Phase 1+
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
    }
}
