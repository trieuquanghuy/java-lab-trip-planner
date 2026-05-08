// Source: 00-CONTEXT.md D-28 — subprojects use `:services:<svc>` and `:libs:<lib>`
// Phase 0 declares all 8 subprojects; their build.gradle.kts files arrive in later waves
// (Wave 2: libs, Wave 3: eureka, Wave 4: gateway, Wave 5: auth/trip/destination).
// Until then, Gradle will not be asked to configure missing subprojects unless an
// individual `:path:task` is invoked, so `./gradlew tasks` runs cleanly on the root.
rootProject.name = "trip-planner"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    ":libs:observability",
    ":libs:error-handling",
    ":libs:api-contracts",
    ":libs:jwt-common",            // NEW Phase 1 — explicit LIFT of Phase 0 D-07 per 01-CONTEXT.md Open Question 1
    ":services:eureka-server",
    ":services:api-gateway",
    ":services:auth-service",
    ":services:trip-service",
    ":services:destination-service",
)
