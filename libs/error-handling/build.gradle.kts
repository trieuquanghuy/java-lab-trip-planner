// D-05: minimal stub. No GlobalExceptionHandler or @ControllerAdvice — those are added per service in Phase 1+ when real endpoints exist.
// Source: 00-CONTEXT.md D-05, 00-PATTERNS.md Bucket B (libs/error-handling/build.gradle.kts).
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api("org.springframework:spring-web")  // for ProblemDetail
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}
