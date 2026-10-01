package com.suhasan.finance.account_service.security.keys;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteJwksTest {

    private static final String URI = "http://account-service/.well-known/jwks.json";

    private final AtomicReference<String> served = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicInteger fetches = new AtomicInteger();
    private final MutableClock clock = new MutableClock();

    private static String jwks(RsaSigningKeys keys) throws Exception {
        return new ObjectMapper().writeValueAsString(keys.jwks());
    }

    private static RsaSigningKeys keys(java.security.KeyPair pair, String kid) {
        return new RsaSigningKeys("test", TestKeys.privatePem(pair.getPrivate()), kid, null, null, false);
    }

    @SuppressWarnings("unchecked")
    private RemoteJwks remote() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            fetches.incrementAndGet();
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(status.get());
            when(response.body()).thenReturn(served.get());
            return response;
        });
        return new RemoteJwks(URI, null, http, clock);
    }

    @Test
    void resolvesPublishedKeysByIdAndCachesThem() throws Exception {
        served.set(jwks(keys(TestKeys.USER, "k1")));
        RemoteJwks remote = remote();

        RSAPublicKey key = remote.resolve("k1").orElseThrow();
        assertThat(key.getModulus()).isEqualTo(((RSAPublicKey) TestKeys.USER.getPublic()).getModulus());
        remote.resolve("k1");
        assertThat(fetches).hasValue(1);
    }

    @Test
    void anUnknownKeyIdRefetchesSoARotationIsPickedUp() throws Exception {
        served.set(jwks(keys(TestKeys.USER, "k1")));
        RemoteJwks remote = remote();
        remote.resolve("k1");

        served.set(jwks(keys(TestKeys.OTHER, "k2")));
        clock.advance(RemoteJwks.MIN_REFRESH_INTERVAL.plusSeconds(1));

        assertThat(remote.resolve("k2")).isPresent();
        assertThat(fetches).hasValue(2);
    }

    @Test
    void forgedKeyIdsCannotHammerTheIssuer() throws Exception {
        served.set(jwks(keys(TestKeys.USER, "k1")));
        RemoteJwks remote = remote();
        remote.resolve("k1");

        for (int i = 0; i < 50; i++) {
            assertThat(remote.resolve("forged-" + i)).isEmpty();
        }
        assertThat(fetches).hasValue(1);
    }

    @Test
    void keepsTheLastGoodKeysWhileTheIssuerIsDown() throws Exception {
        served.set(jwks(keys(TestKeys.USER, "k1")));
        RemoteJwks remote = remote();
        remote.resolve("k1");

        status.set(503);
        clock.advance(RemoteJwks.CACHE_TTL.plusMinutes(1));

        assertThat(remote.resolve("k1")).isPresent();
        assertThat(fetches).hasValue(2);
    }

    @Test
    void aStaticKeyNeedsNoIssuer() {
        RemoteJwks remote = new RemoteJwks(null, TestKeys.publicPem(TestKeys.INTERNAL.getPublic()));

        assertThat(remote.resolve("anything")).isPresent();
    }

    @Test
    void refusesToStartWithNothingToVerifyAgainst() {
        assertThatThrownBy(() -> new RemoteJwks("", "")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionStyleConfigurationRefusesAGeneratedKey() {
        assertThatThrownBy(() -> new RsaSigningKeys("user access token", "", "", null, null, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new RsaSigningKeys("user access token", "", "", null, null, true).keyId()).isNotBlank();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
