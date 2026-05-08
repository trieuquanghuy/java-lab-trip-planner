// D-06: Empty Gradle module so Phase 1 can add UserContext later
// without modifying settings.gradle.kts or any service's build.gradle.kts.
plugins {
    `java-library`
}
// No dependencies. No source files. Phase 1 lands com.tripplanner.contracts.UserContext here.
