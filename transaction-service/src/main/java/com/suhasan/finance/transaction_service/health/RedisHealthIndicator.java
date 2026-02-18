package com.suhasan.finance.transaction_service.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Health indicator for Redis cache connectivity
 * Only enabled when Redis is configured and available
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(name = "spring.data.redis.host")
@SuppressWarnings("null")
public class RedisHealthIndicator implements HealthIndicator {
    
    private final RedisConnectionFactory redisConnectionFactory;
    
    @Override
    public Health health() {
        try {
            return checkRedisHealth();
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("cache", "Redis");
        }
    }
    
    private Health checkRedisHealth() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            // Test basic connectivity with PING command
            String pong = connection.ping();
            
            if (!"PONG".equals(pong)) {
                return Health.down()
                        .withDetail("error", "Redis PING command failed")
                        .withDetail("cache", "Redis")
                        .withDetail("response", pong);
            }
            
            // Get Redis server info
            Properties info = connection.serverCommands().info();
            if (info == null) {
                info = new Properties();
            }
            String redisVersion = info.getProperty("redis_version");
            String redisMode = info.getProperty("redis_mode");
            String usedMemory = info.getProperty("used_memory_human");
            String connectedClients = info.getProperty("connected_clients");
            
            // Test basic operations
            String testKey = "health:check:" + System.currentTimeMillis();
            String testValue = "test";
            byte[] keyBytes = testKey.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = testValue.getBytes(StandardCharsets.UTF_8);
            
            // SET operation
            connection.stringCommands().set(keyBytes, valueBytes);
            
            // GET operation
            byte[] retrievedValue = connection.stringCommands().get(keyBytes);
            
            // DELETE operation
            connection.keyCommands().del(keyBytes);
            
            if (retrievedValue == null || !testValue.equals(new String(retrievedValue, StandardCharsets.UTF_8))) {
                return Health.down()
                        .withDetail("error", "Redis SET/GET operations failed")
                        .withDetail("cache", "Redis");
            }
            
            return Health.up()
                    .withDetail("cache", "Redis")
                    .withDetail("version", redisVersion)
                    .withDetail("mode", redisMode)
                    .withDetail("usedMemory", usedMemory)
                    .withDetail("connectedClients", connectedClients)
                    .withDetail("ping", "PONG");
                    
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("cache", "Redis");
        }
    }
}
