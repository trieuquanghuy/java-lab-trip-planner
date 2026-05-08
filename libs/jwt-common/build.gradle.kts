// Source: 01-CONTEXT.md D-01..D-04 (libs/jwt-common shape — Verifier + reactive filter + servlet filter);
//         01-RESEARCH.md "Recommended Project Structure" lines 220-249;
//         01-PATTERNS.md Bucket A (lines 158-215); 01-CONTEXT.md Open Question 1 LIFT of Phase 0 D-07.
//
// Pitfall I (WR-02 carryover): autoconfig discriminates with @ConditionalOnWebApplication, NOT
//   @ConditionalOnClass — applied in Wave 1's JwtAutoConfiguration.java.
// Convention C6-P1: micrometer-tracing-bom remains pinned ONCE in libs/observability/build.gradle.kts.
//   This module does NOT re-import that BOM.
//
// This is a java-library — do NOT apply org.springframework.boot (that plugin packages applications,
// not libs). java-test-fixtures plugin emits a separate testFixtures classifier so consumer service
// tests can `testImplementation(testFixtures(project(":libs:jwt-common")))` to pull JwtFixtures.
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
    `java-test-fixtures`
}

dependencies {
    // Inter-lib graph: UserContext (api-contracts) + ProblemDetailFactory/ErrorCode (error-handling)
    // are both `api` so downstream services see them on the compile classpath transitively.
    api(project(":libs:api-contracts"))
    api(project(":libs:error-handling"))

    // jjwt 0.13.0 — modern API only (Pitfall A / Convention C27-P1).
    api(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // For @AutoConfiguration / @ConditionalOnWebApplication.
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Security shared types — compileOnly so reactive-only and servlet-only consumers
    // can independently choose which Security stack to pull at runtime.
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.springframework.security:spring-security-config")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework:spring-webflux")

    testImplementation(libs.spring.boot.starter.test)

    // testFixtures classpath — JwtFixtures (Wave 0b) only needs jjwt to mint test tokens.
    testFixturesApi(libs.jjwt.api)
    testFixturesImplementation(libs.jjwt.impl)
    testFixturesImplementation(libs.jjwt.jackson)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}
