package com.merchanthub;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests: spins up a real Postgres via Testcontainers and
 * points both Flyway and the runtime datasource at it. The scheduler is disabled
 * so it doesn't interfere with assertions.
 *
 * <p>Note: here the app connects as the superuser, so these tests exercise the
 * APPLICATION-layer isolation. The independent DB-layer RLS net is verified by
 * the restricted-role wiring in docker-compose / db/init.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("merchanthub")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("merchanthub.sync-interval-ms", () -> "0");
        registry.add("merchanthub.dev-auth-enabled", () -> "true");
    }
}
