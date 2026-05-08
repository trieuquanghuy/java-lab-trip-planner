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
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":libs:observability"))
    implementation(project(":libs:error-handling"))
    implementation(project(":libs:api-contracts"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.cloud.starter.gateway)            // reactive gateway (NOT the WebMVC variant)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.bundles.observability)

    testImplementation(libs.spring.boot.starter.test)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
    }
}
