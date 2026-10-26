package com.smartgym.membership.db;

import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void appliesAllMigrationsAndStartsWithHibernateValidation() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5", "6", "7",
                        "8", "9", "10", "11", "12", "13", "100");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_plan WHERE code IN ('MONTHLY', 'ANNUAL')",
                Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gym_policy", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'business_profiles'",
                Integer.class))
                .isZero();
    }

    @Test
    void rejectsDeletingPaymentRecords() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID memberId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO member (id, keycloak_user_id, username, display_name) VALUES (?, ?, ?, ?)",
                memberId, "test-member-" + memberId, "test-member", "Test Member");
        jdbc.update(
                "INSERT INTO subscription_plan (id, code, name, description, duration_days, price_cents, currency) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                planId, "TEST-" + planId, "Test", "Test plan", 30, 1000L, "EUR");
        jdbc.update(
                "INSERT INTO subscription (id, member_id, plan_id, starts_at, ends_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 days')",
                subscriptionId, memberId, planId);
        jdbc.update(
                "INSERT INTO payment (id, member_id, subscription_id, amount_cents, currency, method, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                paymentId, memberId, subscriptionId, 1000L, "EUR", "CARD", "migration-test");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM payment WHERE id = ?", paymentId))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM payment WHERE id = ?", String.class, paymentId))
                .isEqualTo("RECORDED");
    }

    @Test
    void allowsPaymentsWithoutSubscription() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO member (id, keycloak_user_id, username, display_name) VALUES (?, ?, ?, ?)",
                memberId, "optional-subscription-" + memberId, "optional-subscription", "Optional Subscription");
        jdbc.update(
                "INSERT INTO payment (id, member_id, amount_cents, currency, method, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                paymentId, memberId, 1000L, "TND", "CASH", "migration-test");

        assertThat(jdbc.queryForObject(
                "SELECT subscription_id FROM payment WHERE id = ?", UUID.class, paymentId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT paid_at FROM payment WHERE id = ?", java.time.OffsetDateTime.class, paymentId))
                .isNotNull();
    }

    @Test
    void enforcesOneActiveSubscriptionPerMember() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID memberId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO member (id, keycloak_user_id, username, display_name) VALUES (?, ?, ?, ?)",
                memberId, "unique-member-" + memberId, "unique-member", "Unique Member");
        jdbc.update(
                "INSERT INTO subscription_plan (id, code, name, description, duration_days, price_cents, currency) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                planId, "UNIQUE-" + planId, "Unique", "Test plan", 30, 1000L, "EUR");
        insertActiveSubscription(jdbc, memberId, planId);

        assertThatThrownBy(() -> insertActiveSubscription(jdbc, memberId, planId))
                .isInstanceOf(DataAccessException.class);
    }

    private static void insertActiveSubscription(JdbcTemplate jdbc, UUID memberId, UUID planId) {
        jdbc.update(
                "INSERT INTO subscription (id, member_id, plan_id, starts_at, ends_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 days')",
                UUID.randomUUID(), memberId, planId);
    }
}
