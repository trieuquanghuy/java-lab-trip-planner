// Source: 00-CONTEXT.md D-04, 00-PATTERNS.md Bucket B, 00-RESEARCH.md Pattern 1.
//
// Pitfall 7: micrometer-tracing-bom pinned ONCE here. Consumers get it transitively.
// Do NOT register ServerHttpObservationFilter manually anywhere.
//
// This is a java-library — do NOT apply org.springframework.boot (that plugin is for
// applications; it would try to package this lib as an executable jar with a main class).
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // `api` so consumers see actuator types (HealthIndicator, etc.) on their compile classpath.
    api(libs.spring.boot.starter.actuator)

    // Pulls micrometer-tracing-bridge-otel + opentelemetry-exporter-zipkin + logstash-logback-encoder
    // via the catalog's [bundles].observability accessor (single source of truth — Convention C6).
    api(libs.bundles.observability)

    // For @AutoConfiguration / @ConditionalOnClass annotations.
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Servlet API needed by MdcEnrichmentFilter; compileOnly so reactive-only consumers
    // (api-gateway / WebFlux) don't pull a servlet API they don't use.
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // WebFlux needed by ReactiveMdcEnrichmentFilter; compileOnly so servlet-only consumers
    // (auth-service / trip-service / destination-service) don't pull WebFlux they don't use.
    compileOnly("org.springframework:spring-webflux")

    // Test dependencies
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework:spring-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito:mockito-core")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
        // Pitfall 7 / Convention C6: tracing BOM pinned ONCE here.
        mavenBom("io.micrometer:micrometer-tracing-bom:${libs.versions.micrometerTracing.get()}")
    }
}
