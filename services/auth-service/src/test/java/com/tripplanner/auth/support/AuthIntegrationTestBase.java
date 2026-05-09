// Source: 02-06-PLAN.md Task 6.1(c); 02-PATTERNS.md §AuthIntegrationTestBase; 02-VALIDATION.md Wave 0.
//
// Reusable IT base — Postgres + Redis @ServiceConnection (Spring Boot 3.5 auto-wiring) + GreenMail.
//
// Postgres MUST be a real Testcontainer Postgres (NOT H2): RefreshTokenService.rotate uses
// SELECT FOR UPDATE which H2 does not fully support (02-RESEARCH.md §State of the Art).
//
// GreenMail is wired as a JUnit 5 extension; @DynamicPropertySource overrides spring.mail.host/port
// to point Spring's JavaMailSender at the in-process SMTP (faster than Testcontainers MailHog and
// gives test code direct access to received messages via greenMail.getReceivedMessages()).
//
// Schema bootstrap: the Testcontainer Postgres image does NOT carry infra/postgres/init.sql so the
// `auth` schema doesn't pre-exist. We add classpath:db/test-migration to spring.flyway.locations
// (in application-test.yml) and ship V0__create_auth_schema.sql under src/test/resources/db/test-migration/.
package com.tripplanner.auth.support;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public abstract class AuthIntegrationTestBase {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("tripplanner");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @RegisterExtension
    protected static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void registerMailProps(DynamicPropertyRegistry registry) {
        // GreenMail picks an ephemeral port at startup; override spring.mail.host/port at runtime.
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
    }
}
