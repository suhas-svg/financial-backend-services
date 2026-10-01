package com.suhasan.finance.account_service.security;

import com.suhasan.finance.account_service.security.RefreshTokenService.IssuedToken;
import com.suhasan.finance.account_service.security.RefreshTokenService.Refresh;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs refresh-token rotation SQL against the real Flyway schema on PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD) // methods share one table
class RefreshTokenServicePostgresTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("account_refresh_tokens")
            .withUsername("test")
            .withPassword("test");

    private static NamedParameterJdbcTemplate jdbc;

    private MutableClock clock;
    private RefreshTokenService service;

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
        jdbc.update("DELETE FROM refresh_tokens", new MapSqlParameterSource());
        clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
        service = new RefreshTokenService(jdbc, new SimpleMeterRegistry(), clock,
                Duration.ofMinutes(30), Duration.ofHours(12), Duration.ofSeconds(20));
    }

    @Test
    void refreshRotatesTheToken() {
        IssuedToken first = service.issue("alice");

        Refresh refresh = service.refresh(first.value()).orElseThrow();

        assertThat(refresh.username()).isEqualTo("alice");
        IssuedToken second = refresh.next();
        assertThat(second.value()).isNotEqualTo(first.value());
        assertThat(service.refresh(second.value())).isPresent();
    }

    @Test
    void rotatedTokenWithinOverlapGetsItsOwnSuccessor() {
        IssuedToken first = service.issue("alice");
        // Two tabs refresh at once, or the first response was lost in a reload.
        IssuedToken winner = service.refresh(first.value()).orElseThrow().next();

        clock.advance(Duration.ofSeconds(5));
        Refresh retry = service.refresh(first.value()).orElseThrow();

        assertThat(retry.username()).isEqualTo("alice");
        assertThat(retry.next().value()).isNotEqualTo(winner.value());
        // Whichever cookie the browser ended up with keeps the session alive.
        assertThat(service.refresh(retry.next().value())).isPresent();
        assertThat(service.refresh(winner.value())).isPresent();
    }

    @Test
    void lostRotationResponseDoesNotEndTheSession() {
        IssuedToken held = service.issue("alice");
        service.refresh(held.value()); // successor never reached the browser
        clock.advance(Duration.ofSeconds(10));

        IssuedToken recovered = service.refresh(held.value()).orElseThrow().next();
        clock.advance(Duration.ofMinutes(10));

        assertThat(service.refresh(recovered.value())).isPresent();
    }

    @Test
    void reuseAfterGraceRevokesTheWholeFamily() {
        IssuedToken stolen = service.issue("alice");
        IssuedToken legitimate = service.refresh(stolen.value()).orElseThrow().next();

        clock.advance(Duration.ofMinutes(1));

        assertThat(service.refresh(stolen.value())).isEmpty();
        assertThat(service.refresh(legitimate.value())).isEmpty();
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                new MapSqlParameterSource(), Integer.class);
        assertThat(active).isZero();
    }

    @Test
    void idleTokensExpire() {
        IssuedToken token = service.issue("alice");

        clock.advance(Duration.ofMinutes(31));

        assertThat(service.refresh(token.value())).isEmpty();
    }

    @Test
    void sessionsEndAtTheAbsoluteLifetimeEvenWhenActive() {
        String current = service.issue("alice").value();
        for (int i = 0; i < 30; i++) { // 30 x 29 min > 12 h
            clock.advance(Duration.ofMinutes(29));
            Optional<Refresh> refresh = service.refresh(current);
            if (refresh.isEmpty()) {
                assertThat(Duration.between(Instant.parse("2026-10-01T10:00:00Z"), clock.instant()))
                        .isGreaterThanOrEqualTo(Duration.ofHours(12));
                return;
            }
            IssuedToken next = refresh.get().next();
            assertThat(next.expiresAt()).isBeforeOrEqualTo(Instant.parse("2026-10-01T22:00:00Z"));
            current = next.value();
        }
        throw new AssertionError("session outlived its absolute lifetime");
    }

    @Test
    void logoutRevokesTheSession() {
        IssuedToken first = service.issue("alice");
        IssuedToken second = service.refresh(first.value()).orElseThrow().next();

        service.revoke(first.value());

        assertThat(service.refresh(second.value())).isEmpty();
    }

    @Test
    void sessionsAreIndependent() {
        IssuedToken laptop = service.issue("alice");
        IssuedToken phone = service.issue("alice");

        service.revoke(laptop.value());

        assertThat(service.refresh(phone.value())).isPresent();
    }

    @Test
    void unknownTokensAndRawValuesAreNotStored() {
        IssuedToken token = service.issue("alice");

        assertThat(service.refresh("not-a-token")).isEmpty();
        Integer plain = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = :value",
                new MapSqlParameterSource("value", token.value()), Integer.class);
        assertThat(plain).isZero();
    }

    @Test
    void purgeKeepsRecentFamiliesForReuseDetection() {
        service.issue("old");
        clock.advance(Duration.ofHours(37));
        service.issue("recent");

        service.purgeExpired();

        Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens",
                new MapSqlParameterSource(), Integer.class);
        assertThat(remaining).isEqualTo(1);
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
