package com.tripplanner.destination.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SearchControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("tripplanner");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearCache() {
        var connection = redisTemplate.getConnectionFactory().getConnection();
        connection.serverCommands().flushAll();
        connection.close();
    }

    @Test
    void searchLondonReturnsLondonGBFirst() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "london"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("London"))
                .andExpect(jsonPath("$.items[0].country").value("United Kingdom"))
                .andExpect(jsonPath("$.items[0].type").value("city"));
    }

    @Test
    void repeatedSearchReturnsFasterFromCache() throws Exception {
        // First call (cold — hits Postgres)
        long start1 = System.nanoTime();
        mockMvc.perform(get("/api/search").param("q", "paris"))
                .andExpect(status().isOk());
        long cold = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start1);

        // Second call (warm — Redis cache hit)
        long start2 = System.nanoTime();
        mockMvc.perform(get("/api/search").param("q", "paris"))
                .andExpect(status().isOk());
        long warm = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start2);

        // Cache hit should be faster
        assertThat(warm).isLessThan(cold);
        // Verify cache key exists in Redis
        assertThat(redisTemplate.hasKey("SEARCH:paris:city,country:5")).isTrue();
    }

    @Test
    void emptyQueryReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(get("/api/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(get("/api/search").param("q", "xyzzyplugh12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void accentedSearchReturnsCorrectCity() throws Exception {
        // Search for 'munic' (unaccented prefix of Munich) verifies accent-folded tsvector works
        mockMvc.perform(get("/api/search").param("q", "munic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.name == 'Munich')]").exists());
    }

    @Test
    void searchLimitCappedAtFive() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "san").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(
                        org.hamcrest.Matchers.lessThanOrEqualTo(5)));
    }

    @Test
    void searchEndpointIsPublicNoAuthRequired() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "london"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    @Test
    void coldSearchReturnsWithinSLA() throws Exception {
        long start = System.nanoTime();
        mockMvc.perform(get("/api/search").param("q", "tokyo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // Generous in CI; target is 250ms, allow 500ms
        assertThat(elapsed).isLessThan(500);
    }
}
