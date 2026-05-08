// Source: 00-CONTEXT.md D-29 — root build: plugins, allprojects + subprojects {} block
// Enforces Java 21 toolchain (D-29), JUnit Platform, and Jacoco across every subproject
// without requiring each one to repeat the boilerplate.
plugins {
    java
}

allprojects {
    group = "com.tripplanner"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
