// D-06: Empty Gradle module so Phase 1 can add UserContext later
// without modifying settings.gradle.kts or any service's build.gradle.kts.
// Phase 1 additions: UserContext record (D-04) + UserContextTest (01-VALIDATION.md Wave 0 contract).
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    testImplementation(libs.spring.boot.starter.test)
    // junit-platform-launcher required on testRuntimeClasspath for Gradle's useJUnitPlatform()
    // to work with JUnit Platform 1.12.x (platform-engine / platform-launcher version alignment).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}
