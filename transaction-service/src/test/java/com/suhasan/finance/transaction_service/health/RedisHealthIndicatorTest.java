package com.suhasan.finance.transaction_service.health;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.RedisStringCommands;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisHealthIndicatorTest {
    @Test
    void reportsUpAfterPingAndRoundTrip() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisServerCommands server = mock(RedisServerCommands.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        RedisKeyCommands keys = mock(RedisKeyCommands.class);
        Properties info = new Properties();
        info.setProperty("redis_version", "7");
        info.setProperty("redis_mode", "standalone");
        info.setProperty("used_memory_human", "1M");
        info.setProperty("connected_clients", "2");
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        when(connection.serverCommands()).thenReturn(server);
        when(server.info()).thenReturn(info);
        when(connection.stringCommands()).thenReturn(strings);
        when(connection.keyCommands()).thenReturn(keys);
        when(strings.get(any(byte[].class))).thenReturn("test".getBytes(StandardCharsets.UTF_8));

        Health health = new RedisHealthIndicator(factory).health();
        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).containsEntry("version", "7").containsEntry("ping", "PONG");
    }

    @Test
    void reportsPingRoundTripAndConnectionFailures() {
        RedisConnectionFactory pingFactory = mock(RedisConnectionFactory.class);
        RedisConnection pingConnection = mock(RedisConnection.class);
        when(pingFactory.getConnection()).thenReturn(pingConnection);
        when(pingConnection.ping()).thenReturn("NO");
        assertThat(new RedisHealthIndicator(pingFactory).health().getStatus()).isEqualTo(Health.Status.DOWN);

        RedisConnectionFactory roundTripFactory = mock(RedisConnectionFactory.class);
        RedisConnection roundTrip = mock(RedisConnection.class);
        RedisServerCommands server = mock(RedisServerCommands.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        RedisKeyCommands keys = mock(RedisKeyCommands.class);
        when(roundTripFactory.getConnection()).thenReturn(roundTrip);
        when(roundTrip.ping()).thenReturn("PONG");
        when(roundTrip.serverCommands()).thenReturn(server);
        when(server.info()).thenReturn(null);
        when(roundTrip.stringCommands()).thenReturn(strings);
        when(roundTrip.keyCommands()).thenReturn(keys);
        when(strings.get(any(byte[].class))).thenReturn(null);
        assertThat(new RedisHealthIndicator(roundTripFactory).health().getStatus()).isEqualTo(Health.Status.DOWN);

        RedisConnectionFactory failed = mock(RedisConnectionFactory.class);
        when(failed.getConnection()).thenThrow(new IllegalStateException("offline"));
        Health health = new RedisHealthIndicator(failed).health();
        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails().get("error")).isEqualTo("offline");
    }
}
