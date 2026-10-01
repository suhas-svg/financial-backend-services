package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.exception.TooManyAttemptsException;
import com.suhasan.finance.account_service.service.AuthThrottleService.Policy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the throttle's upsert SQL against the real Flyway schema on PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD) // methods share one table
class AuthThrottleServicePostgresTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("account_auth_throttle")
            .withUsername("test")
            .withPassword("test");

    private static NamedParameterJdbcTemplate jdbc;

    private MutableClock clock;
    private AuthThrottleService throttle;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM auth_throttle_counters", new MapSqlParameterSource());
        clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
        throttle = throttle(true);
    }

    @Test
    void locksUsernameAfterMaxFailuresAndReportsRetryAfter() {
        for (int i = 0; i < 4; i++) {
            throttle.recordLoginFailure("alice", "10.0.0." + i);
        }
        assertThatCode(() -> throttle.assertLoginAllowed("alice", "10.0.0.99")).doesNotThrowAnyException();

        throttle.recordLoginFailure("alice", "10.0.0.5");

        assertThatThrownBy(() -> throttle.assertLoginAllowed("alice", "10.0.0.99"))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).getRetryAfterSeconds())
                        .isBetween(900L, 901L));
        // Username matching is case- and whitespace-insensitive.
        assertThatThrownBy(() -> throttle.assertLoginAllowed("  ALICE ", "10.0.0.99"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.assertLoginAllowed("bob", "10.0.0.99")).doesNotThrowAnyException();
    }

    @Test
    void lockoutExpiresAndCountingRestarts() {
        for (int i = 0; i < 5; i++) {
            throttle.recordLoginFailure("alice", "10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThatCode(() -> throttle.assertLoginAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();

        // A single failure after expiry starts a new window rather than re-locking.
        throttle.recordLoginFailure("alice", "10.0.0.1");
        assertThatCode(() -> throttle.assertLoginAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        for (int i = 0; i < 4; i++) {
            throttle.recordLoginFailure("alice", "10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(16));
        throttle.recordLoginFailure("alice", "10.0.0.1");

        assertThatCode(() -> throttle.assertLoginAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsUsernameFailures() {
        for (int i = 0; i < 4; i++) {
            throttle.recordLoginFailure("alice", "10.0.0.1");
        }
        throttle.recordLoginSuccess("alice");
        throttle.recordLoginFailure("alice", "10.0.0.1");

        assertThatCode(() -> throttle.assertLoginAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void clientIpIsLockedAcrossUsernames() {
        for (int i = 0; i < 10; i++) {
            throttle.recordLoginFailure("user-" + i, "203.0.113.7");
        }

        assertThatThrownBy(() -> throttle.assertLoginAllowed("someone-new", "203.0.113.7"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.assertLoginAllowed("someone-new", "203.0.113.8")).doesNotThrowAnyException();
    }

    @Test
    void registrationIsLimitedPerIp() {
        for (int i = 0; i < 3; i++) {
            throttle.checkAndRecordRegistration("198.51.100.1");
        }

        assertThatThrownBy(() -> throttle.checkAndRecordRegistration("198.51.100.1"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.checkAndRecordRegistration("198.51.100.2")).doesNotThrowAnyException();
    }

    @Test
    void usernamesAreNotStoredInPlainText() {
        throttle.recordLoginFailure("alice", "10.0.0.1");

        Integer plain = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_throttle_counters WHERE throttle_key LIKE '%alice%'",
                new MapSqlParameterSource(), Integer.class);
        assertThat(plain).isZero();
    }

    @Test
    void purgeRemovesOnlyStaleUnlockedCounters() {
        throttle.recordLoginFailure("old", "10.0.0.1");
        clock.advance(Duration.ofHours(3).plusMinutes(1)); // past the 3h retention
        throttle.recordLoginFailure("recent", "10.0.0.2");

        throttle.purgeExpiredCounters();

        Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM auth_throttle_counters",
                new MapSqlParameterSource(), Integer.class);
        assertThat(remaining).isEqualTo(2); // login-user and login-ip rows for "recent"
    }

    @Test
    void disabledThrottleNeverLocksOrWrites() {
        AuthThrottleService disabled = throttle(false);
        for (int i = 0; i < 20; i++) {
            disabled.recordLoginFailure("alice", "10.0.0.1");
        }

        assertThatCode(() -> disabled.assertLoginAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM auth_throttle_counters",
                new MapSqlParameterSource(), Integer.class);
        assertThat(rows).isZero();
    }

    private AuthThrottleService throttle(boolean enabled) {
        return new AuthThrottleService(jdbc, new SimpleMeterRegistry(), clock, enabled,
                new Policy("login-user", 5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new Policy("login-ip", 10, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new Policy("register-ip", 3, Duration.ofMinutes(60), Duration.ofMinutes(60)));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
