// D-11: Eureka has NO Flyway, NO datasource. It's a registry, not a data store.
// Source: 00-RESEARCH.md Pattern 1 + Pattern 4; 00-PATTERNS.md Bucket C (lines 391-413).
// No `implementation(project(":libs:observability"))` — eureka-server is not in the trace path
// in Phase 0 (00-PATTERNS.md line 425, 00-CONTEXT.md "Reusable assets" line 255). Logback config
// in src/main/resources/logback-spring.xml is a documented pass-through (Option A in Plan 00-05
// Task 5.2). Phase 10 may switch to Option B by adding the lib dep + uncommenting the include.
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.cloud.starter.netflix.eureka.server)
    testImplementation(libs.spring.boot.starter.test)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
    }
}
