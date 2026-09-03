package com.project.Backend_BookMyHotel.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cache.type=simple",
        "stripe.secret.key=sk_test_context_only",
        "stripe.webhook.secret=whsec_test_context_only"
})
class FlywayCleanDatabaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    // JdbcTemplate is provided by Spring Boot after the Testcontainers DataSource is registered.
    // IntelliJ cannot always infer that dynamically-created test bean.
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // The tables below are created by Flyway inside the disposable PostgreSQL container.
    // They intentionally do not exist in IntelliJ's statically configured data source.
    @SuppressWarnings("SqlResolve")
    @Test
    void cleanDatabaseUsesBaselineThenAppliesLaterMigrations() {
        Integer successfulRequiredMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('23', '24')
                  AND success = TRUE
                """, Integer.class);

        Integer applicationTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);

        Integer seededGlobalPackages = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM off_season_packages
                WHERE scope = 'GLOBAL'
                """, Integer.class);

        assertThat(successfulRequiredMigrations).isEqualTo(2);
        assertThat(applicationTableCount).isEqualTo(19);
        assertThat(seededGlobalPackages).isEqualTo(2);

        System.out.println("PASS: clean PostgreSQL database applied Flyway B23 and V24.");
        System.out.println("PASS: Hibernate validated all 19 application tables.");
        System.out.println("PASS: V24 inserted 2 global off-season packages.");
    }
}
