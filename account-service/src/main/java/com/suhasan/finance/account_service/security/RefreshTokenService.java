package com.suhasan.finance.account_service.security;

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
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rotating, revocable refresh tokens for browser sessions (see V103).
 *
 * <p>Each refresh atomically claims the presented token and issues a successor in
 * the same family. A token that was already rotated is still accepted inside a short
 * overlap window and gets its own successor: this covers several tabs refreshing at
 * once and responses lost in flight (a reload or closed tab mid-refresh), where the
 * browser never stored the first successor. Outside that window a rotated token is
 * treated as stolen and the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    /** A newly issued refresh token; {@code value} is only ever returned to the client. */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    /** Outcome of a successful refresh: the user and the token to send back. */
    public record Refresh(String username, IssuedToken next) {
    }

    private record StoredToken(UUID familyId, String username, Instant expiresAt, Instant familyExpiresAt,
                               Instant rotatedAt, Instant revokedAt) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final Duration idleLifetime;
    private final Duration absoluteLifetime;
    private final Duration rotationGrace;
    private final Counter reuseCounter;

    @Autowired
    public RefreshTokenService(
            final NamedParameterJdbcTemplate jdbc,
            final MeterRegistry registry,
            @Value("${security.refresh-token.idle-minutes:30}") final long idleMinutes,
            @Value("${security.refresh-token.absolute-hours:12}") final long absoluteHours,
            @Value("${security.refresh-token.rotation-grace-seconds:20}") final long graceSeconds) {
        this(jdbc, registry, Clock.systemUTC(),
                Duration.ofMinutes(idleMinutes), Duration.ofHours(absoluteHours), Duration.ofSeconds(graceSeconds));
    }

    RefreshTokenService(
            final NamedParameterJdbcTemplate jdbc,
            final MeterRegistry registry,
            final Clock clock,
            final Duration idleLifetime,
            final Duration absoluteLifetime,
            final Duration rotationGrace) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.idleLifetime = idleLifetime;
        this.absoluteLifetime = absoluteLifetime;
        this.rotationGrace = rotationGrace;
        this.reuseCounter = registry.counter("auth_refresh_token_reuse_total");
    }

    /** Starts a new session family for a user who just authenticated. */
    public IssuedToken issue(final String username) {
        final Instant now = clock.instant();
        final Instant familyExpiresAt = now.plus(absoluteLifetime);
        return insert(UUID.randomUUID(), username, now, familyExpiresAt);
    }

    /** Rotates {@code presented}, or returns empty if it is unknown, expired, revoked or reused. */
    public Optional<Refresh> refresh(final String presented) {
        final String hash = hash(presented);
        final Instant now = clock.instant();
        final List<StoredToken> claimed = jdbc.query("""
                UPDATE refresh_tokens SET rotated_at = CAST(:now AS timestamptz)
                 WHERE token_hash = :hash
                   AND rotated_at IS NULL
                   AND revoked_at IS NULL
                   AND expires_at > CAST(:now AS timestamptz)
                   AND family_expires_at > CAST(:now AS timestamptz)
                RETURNING family_id, username, expires_at, family_expires_at, rotated_at, revoked_at
                """, params(hash, now), (rs, i) -> mapToken(rs));
        if (!claimed.isEmpty()) {
            final StoredToken token = claimed.get(0);
            return Optional.of(new Refresh(token.username(),
                    insert(token.familyId(), token.username(), now, token.familyExpiresAt())));
        }

        final Optional<StoredToken> existing = jdbc.query("""
                SELECT family_id, username, expires_at, family_expires_at, rotated_at, revoked_at
                  FROM refresh_tokens WHERE token_hash = :hash
                """, params(hash, now), (rs, i) -> mapToken(rs)).stream().findFirst();
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        final StoredToken token = existing.get();
        if (token.revokedAt() != null || !token.familyExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (token.rotatedAt() != null) {
            if (token.rotatedAt().isAfter(now.minus(rotationGrace))) {
                return Optional.of(new Refresh(token.username(),
                        insert(token.familyId(), token.username(), now, token.familyExpiresAt())));
            }
            reuseCounter.increment();
            revokeFamily(token.familyId(), "REUSE_DETECTED", now);
        }
        return Optional.empty();
    }

    /** Ends the session the token belongs to (logout). Unknown tokens are ignored. */
    public void revoke(final String presented) {
        final Instant now = clock.instant();
        jdbc.query("SELECT family_id FROM refresh_tokens WHERE token_hash = :hash",
                        params(hash(presented), now), (rs, i) -> rs.getObject(1, UUID.class))
                .stream().findFirst()
                .ifPresent(familyId -> revokeFamily(familyId, "LOGOUT", now));
    }

    @Scheduled(fixedDelayString = "${security.refresh-token.cleanup-delay-ms:3600000}",
            initialDelayString = "${security.refresh-token.cleanup-initial-delay-ms:600000}")
    public void purgeExpired() {
        // Keep a day of history after a family ends so reuse can still be detected.
        jdbc.update("DELETE FROM refresh_tokens WHERE family_expires_at < CAST(:cutoff AS timestamptz)",
                new MapSqlParameterSource("cutoff", Timestamp.from(clock.instant().minus(Duration.ofDays(1)))));
    }

    private IssuedToken insert(final UUID familyId, final String username, final Instant now,
                               final Instant familyExpiresAt) {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        final String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        final Instant idleExpiry = now.plus(idleLifetime);
        final Instant expiresAt = idleExpiry.isBefore(familyExpiresAt) ? idleExpiry : familyExpiresAt;
        jdbc.update("""
                INSERT INTO refresh_tokens
                       (token_id, family_id, username, token_hash, issued_at, expires_at, family_expires_at)
                VALUES (:id, :family, :username, :hash, CAST(:now AS timestamptz),
                        CAST(:expiresAt AS timestamptz), CAST(:familyExpiresAt AS timestamptz))
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("family", familyId)
                .addValue("username", username)
                .addValue("hash", hash(value))
                .addValue("now", Timestamp.from(now))
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("familyExpiresAt", Timestamp.from(familyExpiresAt)));
        return new IssuedToken(value, expiresAt);
    }

    private void revokeFamily(final UUID familyId, final String reason, final Instant now) {
        jdbc.update("""
                UPDATE refresh_tokens SET revoked_at = CAST(:now AS timestamptz), revoke_reason = :reason
                 WHERE family_id = :family AND revoked_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("reason", reason)
                .addValue("family", familyId));
    }

    private static MapSqlParameterSource params(final String hash, final Instant now) {
        return new MapSqlParameterSource()
                .addValue("hash", hash)
                .addValue("now", Timestamp.from(now));
    }

    private static StoredToken mapToken(final java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoredToken(
                rs.getObject("family_id", UUID.class),
                rs.getString("username"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("family_expires_at").toInstant(),
                instantOrNull(rs.getTimestamp("rotated_at")),
                instantOrNull(rs.getTimestamp("revoked_at")));
    }

    private static Instant instantOrNull(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String hash(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
