// Source: 00-CONTEXT.md D-29 — root build: plugins, allprojects + subprojects {} block
// Enforces Java 21 toolchain (D-29), JUnit Platform, and Jacoco across every subproject
// without requiring each one to repeat the boilerplate.
plugins {
    java
    id("org.owasp.dependencycheck") version "10.0.4"
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
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JacocoReport> {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("jacocoTestReport"))
        violationRules {
            rule {
                limit {
                    minimum = "0.70".toBigDecimal()
                }
            }
        }
        // Exclude config/entity classes from coverage minimum
        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("classes/java/main")) {
                exclude(
                    "**/config/**",
                    "**/entity/**",
                    "**/dto/**",
                    "**/exception/**",
                    "**/*AutoConfiguration*"
                )
            }
        )
    }
}

// OWASP Dependency-Check: fail on HIGH/CRITICAL CVEs (CVSS >= 7)
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    analyzers.assemblyEnabled = false
}
