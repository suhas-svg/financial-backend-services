package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.exception.TooManyAttemptsException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Database-backed throttling for unauthenticated auth endpoints, shared by every
 * replica. Each scope counts attempts in a fixed window; reaching the limit locks
 * the scope until the lockout expires, and locked scopes are refused before any
 * password hashing runs.
 *
 * <ul>
 *   <li>login per username: failed logins, reset on success;</li>
 *   <li>login per client IP: failed logins across usernames (credential stuffing);</li>
 *   <li>registration per client IP: every attempt.</li>
 * </ul>
 */
@Service
public class AuthThrottleService {

    record Policy(String scope, int maxAttempts, Duration window, Duration lockout) {
    }

    private static final String RECORD_ATTEMPT_SQL = """
            INSERT INTO auth_throttle_counters AS c
                   (throttle_key, failure_count, window_started_at, locked_until, updated_at)
            VALUES (:key, 1, CAST(:now AS timestamptz),
                    CASE WHEN 1 >= CAST(:max AS integer) THEN CAST(:lockedUntil AS timestamptz) END,
                    CAST(:now AS timestamptz))
            ON CONFLICT (throttle_key) DO UPDATE SET
                failure_count = CASE WHEN c.window_started_at < CAST(:windowStart AS timestamptz)
                                       OR c.locked_until <= CAST(:now AS timestamptz)
                                     THEN 1 ELSE c.failure_count + 1 END,
                window_started_at = CASE WHEN c.window_started_at < CAST(:windowStart AS timestamptz)
                                           OR c.locked_until <= CAST(:now AS timestamptz)
                                         THEN CAST(:now AS timestamptz) ELSE c.window_started_at END,
                locked_until = CASE WHEN (CASE WHEN c.window_started_at < CAST(:windowStart AS timestamptz)
                                                 OR c.locked_until <= CAST(:now AS timestamptz)
                                               THEN 1 ELSE c.failure_count + 1 END) >= CAST(:max AS integer)
                                    THEN CAST(:lockedUntil AS timestamptz) ELSE NULL END,
                updated_at = CAST(:now AS timestamptz)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final Policy loginUser;
    private final Policy loginIp;
    private final Policy registerIp;
    private final Duration retention;
    private final Counter throttledCounter;

    @Autowired
    public AuthThrottleService(
            final NamedParameterJdbcTemplate jdbc,
            final MeterRegistry registry,
            @Value("${security.auth-throttle.enabled:true}") final boolean enabled,
            @Value("${security.auth-throttle.login-user.max-failures:5}") final int loginUserMax,
            @Value("${security.auth-throttle.login-user.window-minutes:15}") final long loginUserWindow,
            @Value("${security.auth-throttle.login-user.lockout-minutes:15}") final long loginUserLockout,
            @Value("${security.auth-throttle.login-ip.max-failures:50}") final int loginIpMax,
            @Value("${security.auth-throttle.login-ip.window-minutes:15}") final long loginIpWindow,
            @Value("${security.auth-throttle.login-ip.lockout-minutes:15}") final long loginIpLockout,
            @Value("${security.auth-throttle.register-ip.max-attempts:10}") final int registerIpMax,
            @Value("${security.auth-throttle.register-ip.window-minutes:60}") final long registerIpWindow,
            @Value("${security.auth-throttle.register-ip.lockout-minutes:60}") final long registerIpLockout) {
        this(jdbc, registry, Clock.systemUTC(), enabled,
                new Policy("login-user", loginUserMax, Duration.ofMinutes(loginUserWindow), Duration.ofMinutes(loginUserLockout)),
                new Policy("login-ip", loginIpMax, Duration.ofMinutes(loginIpWindow), Duration.ofMinutes(loginIpLockout)),
                new Policy("register-ip", registerIpMax, Duration.ofMinutes(registerIpWindow), Duration.ofMinutes(registerIpLockout)));
    }

    AuthThrottleService(
            final NamedParameterJdbcTemplate jdbc,
            final MeterRegistry registry,
            final Clock clock,
            final boolean enabled,
            final Policy loginUser,
            final Policy loginIp,
            final Policy registerIp) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = enabled;
        this.loginUser = loginUser;
        this.loginIp = loginIp;
        this.registerIp = registerIp;
        this.retention = List.of(loginUser, loginIp, registerIp).stream()
                .map(policy -> policy.window().plus(policy.lockout()))
                .max(Duration::compareTo)
                .orElseThrow()
                .plusHours(1);
        this.throttledCounter = registry.counter("auth_throttled_total");
    }

    /** Refuses the login before credentials are checked if the username or IP is locked. */
    public void assertLoginAllowed(final String username, final String clientIp) {
        assertNotLocked(List.of(key(loginUser, usernameSubject(username)), key(loginIp, clientIp)));
    }

    public void recordLoginFailure(final String username, final String clientIp) {
        recordAttempt(loginUser, usernameSubject(username));
        recordAttempt(loginIp, clientIp);
    }

    /** A successful login clears the username's failures; the IP budget keeps counting. */
    public void recordLoginSuccess(final String username) {
        if (!enabled) {
            return;
        }
        jdbc.update("DELETE FROM auth_throttle_counters WHERE throttle_key = :key",
                new MapSqlParameterSource("key", key(loginUser, usernameSubject(username))));
    }

    /** Counts a registration attempt, refusing it if the IP is already locked. */
    public void checkAndRecordRegistration(final String clientIp) {
        assertNotLocked(List.of(key(registerIp, clientIp)));
        recordAttempt(registerIp, clientIp);
    }

    @Scheduled(fixedDelayString = "${security.auth-throttle.cleanup-delay-ms:3600000}",
            initialDelayString = "${security.auth-throttle.cleanup-initial-delay-ms:600000}")
    public void purgeExpiredCounters() {
        if (!enabled) {
            return;
        }
        final Instant now = clock.instant();
        jdbc.update("""
                DELETE FROM auth_throttle_counters
                 WHERE updated_at < CAST(:cutoff AS timestamptz)
                   AND (locked_until IS NULL OR locked_until <= CAST(:now AS timestamptz))
                """, new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(now.minus(retention)))
                .addValue("now", Timestamp.from(now)));
    }

    private void assertNotLocked(final List<String> keys) {
        if (!enabled) {
            return;
        }
        final Instant now = clock.instant();
        final Timestamp lockedUntil = jdbc.queryForObject("""
                SELECT MAX(locked_until) FROM auth_throttle_counters
                 WHERE throttle_key IN (:keys) AND locked_until > CAST(:now AS timestamptz)
                """, new MapSqlParameterSource()
                .addValue("keys", keys)
                .addValue("now", Timestamp.from(now)), Timestamp.class);
        if (lockedUntil != null) {
            throttledCounter.increment();
            final long seconds = Duration.between(now, lockedUntil.toInstant()).toSeconds();
            throw new TooManyAttemptsException(Math.max(1, seconds + 1));
        }
    }

    private void recordAttempt(final Policy policy, final String subject) {
        if (!enabled) {
            return;
        }
        final Instant now = clock.instant();
        jdbc.update(RECORD_ATTEMPT_SQL, new MapSqlParameterSource()
                .addValue("key", key(policy, subject))
                .addValue("now", Timestamp.from(now))
                .addValue("windowStart", Timestamp.from(now.minus(policy.window())))
                .addValue("lockedUntil", Timestamp.from(now.plus(policy.lockout())))
                .addValue("max", policy.maxAttempts()));
    }

    private static String key(final Policy policy, final String subject) {
        return policy.scope() + ":" + subject;
    }

    /** Usernames are hashed so the throttle table never stores them. */
    private static String usernameSubject(final String username) {
        final String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
