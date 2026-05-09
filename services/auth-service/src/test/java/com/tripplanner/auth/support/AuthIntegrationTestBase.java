// Source: 02-06-PLAN.md Task 6.1(c); 02-PATTERNS.md §AuthIntegrationTestBase; 02-VALIDATION.md Wave 0.
//
// Reusable IT base — Postgres + Redis @ServiceConnection (Spring Boot 3.5 auto-wiring) + GreenMail.
//
// Postgres MUST be a real Testcontainer Postgres (NOT H2): RefreshTokenService.rotate uses
// SELECT FOR UPDATE which H2 does not fully support (02-RESEARCH.md §State of the Art).
//
// SINGLETON container pattern (started ONCE per JVM, never stopped) — avoids the
// Spring TestContext cache vs JUnit @Testcontainers afterAll lifecycle conflict where
// IT class N+1 reuses the cached context (with the old port) but JUnit stopped the
// container after IT class N. We therefore omit @Testcontainers + @Container and
// start the containers in a static initializer; Testcontainers ships with a JVM
// shutdown hook (`Ryuk` reaper) so containers are reaped on JVM exit.
//
// GreenMail uses ServerSetupTest.SMTP (fixed port 3025) which application-test.yml
// pins via spring.mail.port=3025 — no @DynamicPropertySource race condition.
//
// Schema bootstrap: the Testcontainer Postgres image does NOT carry infra/postgres/init.sql.
// We add classpath:db/test-migration to spring.flyway.locations (in application-test.yml)
// and ship V0__create_auth_schema.sql under src/test/resources/db/test-migration/.
package com.tripplanner.auth.support;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public abstract class AuthIntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("tripplanner");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        // Singleton-start; Ryuk reaper handles teardown on JVM shutdown.
        PG.start();
        REDIS.start();
    }

    @RegisterExtension
    protected static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);
}
