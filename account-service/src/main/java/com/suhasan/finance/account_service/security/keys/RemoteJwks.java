package com.suhasan.finance.account_service.security.keys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public keys another service publishes at its JWKS endpoint, used to verify the tokens it signs.
 *
 * <p>Keys are cached for {@link #CACHE_TTL}. A token with an unknown key id triggers a refetch
 * (at most once per {@link #MIN_REFRESH_INTERVAL}, so forged ids cannot hammer the issuer), which
 * is how a key rotation is picked up. If the issuer is unreachable the last good keys stay in use.
 * A fixed PEM public key can be configured instead of a URL (tests, or issuers without JWKS).
 */
public final class RemoteJwks {

    static final Duration CACHE_TTL = Duration.ofMinutes(5);
    static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(RemoteJwks.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Optional<URI> jwksUri;
    private final Optional<RSAPublicKey> staticKey;
    private final HttpClient http;
    private final Clock clock;
    private final Map<String, RSAPublicKey> keys = new ConcurrentHashMap<>();
    private Instant fetchedAt = Instant.EPOCH;
    private Instant lastAttempt = Instant.EPOCH;

    public RemoteJwks(final String jwksUri, final String staticPublicKeyPem) {
        this(jwksUri, staticPublicKeyPem, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Clock.systemUTC());
    }

    RemoteJwks(final String jwksUri, final String staticPublicKeyPem, final HttpClient http, final Clock clock) {
        final boolean hasStatic = staticPublicKeyPem != null && !staticPublicKeyPem.isBlank();
        final boolean hasUri = jwksUri != null && !jwksUri.isBlank();
        if (!hasStatic && !hasUri) {
            throw new IllegalStateException("Configure a JWKS URL or a public key to verify tokens");
        }
        this.staticKey = hasStatic ? Optional.of(PemKeys.publicKey(staticPublicKeyPem)) : Optional.empty();
        this.jwksUri = hasUri ? Optional.of(URI.create(jwksUri)) : Optional.empty();
        this.http = http;
        this.clock = clock;
    }

    /** The key for a token header's {@code kid}; with a single published key a missing kid is accepted. */
    public synchronized Optional<RSAPublicKey> resolve(final String keyId) {
        if (staticKey.isPresent()) {
            return staticKey;
        }
        final Instant now = clock.instant();
        final boolean stale = now.isAfter(fetchedAt.plus(CACHE_TTL));
        final boolean unknown = keyId == null ? keys.size() != 1 : !keys.containsKey(keyId);
        if ((stale || unknown) && now.isAfter(lastAttempt.plus(MIN_REFRESH_INTERVAL))) {
            refresh(now);
        }
        if (keyId == null) {
            return keys.size() == 1 ? keys.values().stream().findFirst() : Optional.empty();
        }
        return Optional.ofNullable(keys.get(keyId));
    }

    private void refresh(final Instant now) {
        lastAttempt = now;
        try {
            final HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(jwksUri.orElseThrow()).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("JWKS returned HTTP " + response.statusCode());
            }
            final Map<String, RSAPublicKey> fresh = new ConcurrentHashMap<>();
            for (final JsonNode jwk : JSON.readTree(response.body()).path("keys")) {
                if ("RSA".equals(jwk.path("kty").asText()) && jwk.hasNonNull("kid")) {
                    fresh.put(jwk.get("kid").asText(), PemKeys.fromJwk(jwk.path("n").asText(), jwk.path("e").asText()));
                }
            }
            if (fresh.isEmpty()) {
                throw new IOException("JWKS contains no RSA keys");
            }
            keys.clear();
            keys.putAll(fresh);
            fetchedAt = now;
        } catch (IOException | IllegalArgumentException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Unable to refresh JWKS from {}; keeping {} cached key(s): {}", jwksUri.orElseThrow(), keys.size(), e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
