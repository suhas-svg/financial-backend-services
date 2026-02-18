package com.suhasan.finance.transaction_service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Cache Performance Benchmark")
@SuppressWarnings({"resource", "null"})
public class CachePerformanceBenchmark {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.cache.type", () -> "redis");
    }

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int CACHE_OPERATIONS = 10000;
    private static final int CONCURRENT_THREADS = 20;

    @BeforeEach
    void setUp() {
        // Clear Redis cache
        clearRedisCache();
    }

    @Test
    @DisplayName("Basic Cache Operations Performance")
    void testBasicCacheOperationsPerformance() {
        System.out.println("=== BASIC CACHE OPERATIONS PERFORMANCE ===");

        // Test SET operations
        long setStartTime = System.currentTimeMillis();
        for (int i = 0; i < CACHE_OPERATIONS; i++) {
            String key = "transaction:test:" + i;
            String value = "transaction-data-" + i;
            stringRedisTemplate.opsForValue().set(key, value);
        }
        long setTime = System.currentTimeMillis() - setStartTime;
        double setOpsPerSecond = (double) CACHE_OPERATIONS / (setTime / 1000.0);

        System.out.println("SET operations: " + setTime + "ms (" + 
                String.format("%.2f", setOpsPerSecond) + " ops/sec)");

        // Test GET operations
        long getStartTime = System.currentTimeMillis();
        for (int i = 0; i < CACHE_OPERATIONS; i++) {
            String key = "transaction:test:" + i;
            stringRedisTemplate.opsForValue().get(key);
        }
        long getTime = System.currentTimeMillis() - getStartTime;
        double getOpsPerSecond = (double) CACHE_OPERATIONS / (getTime / 1000.0);

        System.out.println("GET operations: " + getTime + "ms (" + 
                String.format("%.2f", getOpsPerSecond) + " ops/sec)");

        // Test DEL operations
        long delStartTime = System.currentTimeMillis();
        for (int i = 0; i < CACHE_OPERATIONS; i++) {
            String key = "transaction:test:" + i;
            stringRedisTemplate.delete(key);
        }
        long delTime = System.currentTimeMillis() - delStartTime;
        double delOpsPerSecond = (double) CACHE_OPERATIONS / (delTime / 1000.0);

        System.out.println("DEL operations: " + delTime + "ms (" + 
                String.format("%.2f", delOpsPerSecond) + " ops/sec)");

        // Assertions
        assertTrue(setOpsPerSecond > 1000, "SET operations should be fast (> 1000 ops/sec)");
        assertTrue(getOpsPerSecond > 5000, "GET operations should be very fast (> 5000 ops/sec)");
        assertTrue(delOpsPerSecond > 1000, "DEL operations should be fast (> 1000 ops/sec)");
    }

    @Test
    @DisplayName("Complex Object Caching Performance")
    void testComplexObjectCachingPerformance() throws Exception {
        System.out.println("=== COMPLEX OBJECT CACHING PERFORMANCE ===");

        // Create complex transaction objects
        List<TransactionResponse> transactions = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            TransactionResponse transaction = TransactionResponse.builder()
                    .transactionId("tx-" + i)
                    .fromAccountId("account-" + i)
                    .toAccountId("account-" + (i + 1000))
                    .amount(BigDecimal.valueOf(100.00 + i))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.COMPLETED)
                    .description("Performance test transaction " + i)
                    .createdAt(LocalDateTime.now())
                    .processedAt(LocalDateTime.now())
                    .build();
            transactions.add(transaction);
        }

        // Test serialization and caching performance
        long serializationStart = System.currentTimeMillis();
        for (int i = 0; i < transactions.size(); i++) {
            String key = "transaction:object:" + i;
            String jsonValue = objectMapper.writeValueAsString(transactions.get(i));
            stringRedisTemplate.opsForValue().set(key, jsonValue);
        }
        long serializationTime = System.currentTimeMillis() - serializationStart;

        System.out.println("Complex object serialization and caching: " + serializationTime + "ms");

        // Test deserialization and retrieval performance
        long deserializationStart = System.currentTimeMillis();
        List<TransactionResponse> retrievedTransactions = new ArrayList<>();
        for (int i = 0; i < transactions.size(); i++) {
            String key = "transaction:object:" + i;
            String jsonValue = stringRedisTemplate.opsForValue().get(key);
            if (jsonValue != null) {
                TransactionResponse transaction = objectMapper.readValue(jsonValue, TransactionResponse.class);
                retrievedTransactions.add(transaction);
            }
        }
        long deserializationTime = System.currentTimeMillis() - deserializationStart;

        System.out.println("Complex object retrieval and deserialization: " + deserializationTime + "ms");
        System.out.println("Retrieved " + retrievedTransactions.size() + " objects");

        // Verify data integrity
        assertEquals(transactions.size(), retrievedTransactions.size(), 
                "All objects should be retrieved successfully");

        // Performance assertions
        assertTrue(serializationTime < 5000, "Serialization should complete in reasonable time");
        assertTrue(deserializationTime < 5000, "Deserialization should complete in reasonable time");
    }

    @Test
    @DisplayName("Cache Hit Rate Analysis")
    void testCacheHitRateAnalysis() {
        System.out.println("=== CACHE HIT RATE ANALYSIS ===");

        // Populate cache with test data
        int cacheSize = 1000;
        for (int i = 0; i < cacheSize; i++) {
            String key = "transaction:history:account-" + i;
            List<String> transactionIds = Arrays.asList("tx-" + i + "-1", "tx-" + i + "-2", "tx-" + i + "-3");
            redisTemplate.opsForList().rightPushAll(key, transactionIds.toArray());
        }

        // Simulate realistic access patterns
        int totalRequests = 10000;
        AtomicInteger hits = new AtomicInteger(0);
        AtomicInteger misses = new AtomicInteger(0);
        AtomicLong hitTime = new AtomicLong(0);
        AtomicLong missTime = new AtomicLong(0);

        Random random = new Random();
        
        for (int i = 0; i < totalRequests; i++) {
            long start = System.currentTimeMillis();
            
            // 80% of requests access existing data (Pareto principle)
            String key;
            if (random.nextDouble() < 0.8) {
                // Access existing data (should be cache hit)
                key = "transaction:history:account-" + random.nextInt(cacheSize);
            } else {
                // Access non-existing data (should be cache miss)
                key = "transaction:history:account-" + (cacheSize + random.nextInt(1000));
            }
            
            List<Object> result = redisTemplate.opsForList().range(key, 0, -1);
            long responseTime = System.currentTimeMillis() - start;
            
            if (result != null && !result.isEmpty()) {
                hits.incrementAndGet();
                hitTime.addAndGet(responseTime);
            } else {
                misses.incrementAndGet();
                missTime.addAndGet(responseTime);
            }
        }

        double hitRate = (double) hits.get() / totalRequests;
        double avgHitTime = hits.get() > 0 ? (double) hitTime.get() / hits.get() : 0;
        double avgMissTime = misses.get() > 0 ? (double) missTime.get() / misses.get() : 0;

        System.out.println("Total requests: " + totalRequests);
        System.out.println("Cache hits: " + hits.get());
        System.out.println("Cache misses: " + misses.get());
        System.out.println("Hit rate: " + String.format("%.2f%%", hitRate * 100));
        System.out.println("Average hit time: " + String.format("%.2fms", avgHitTime));
        System.out.println("Average miss time: " + String.format("%.2fms", avgMissTime));

        // Assertions
        assertTrue(hitRate > 0.75, "Hit rate should be high for realistic access patterns");
        assertTrue(avgHitTime < 5, "Cache hits should be very fast");
        assertTrue(avgMissTime < 10, "Cache misses should still be reasonably fast");
    }

    @Test
    @DisplayName("Concurrent Cache Access Performance")
    void testConcurrentCacheAccessPerformance() throws InterruptedException {
        System.out.println("=== CONCURRENT CACHE ACCESS PERFORMANCE ===");

        // Pre-populate cache
        for (int i = 0; i < 1000; i++) {
            stringRedisTemplate.opsForValue().set("concurrent:test:" + i, "value-" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);
        
        AtomicInteger totalOperations = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger errors = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Random random = new Random();
                    long threadStart = System.currentTimeMillis();
                    
                    for (int j = 0; j < 500; j++) { // 500 operations per thread
                        try {
                            if (j % 4 == 0) {
                                // Write operation (25%)
                                String key = "concurrent:test:" + random.nextInt(1000);
                                String value = "updated-value-" + threadId + "-" + j;
                                stringRedisTemplate.opsForValue().set(key, value);
                            } else {
                                // Read operation (75%)
                                String key = "concurrent:test:" + random.nextInt(1000);
                                stringRedisTemplate.opsForValue().get(key);
                            }
                            totalOperations.incrementAndGet();
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                    
                    long threadTime = System.currentTimeMillis() - threadStart;
                    totalTime.addAndGet(threadTime);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(2, TimeUnit.MINUTES);
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long totalTestTime = endTime - startTime;

        double operationsPerSecond = (double) totalOperations.get() / (totalTestTime / 1000.0);
        double avgThreadTime = (double) totalTime.get() / CONCURRENT_THREADS;
        double errorRate = (double) errors.get() / totalOperations.get();

        System.out.println("Total test time: " + totalTestTime + "ms");
        System.out.println("Total operations: " + totalOperations.get());
        System.out.println("Operations per second: " + String.format("%.2f", operationsPerSecond));
        System.out.println("Average thread time: " + String.format("%.2fms", avgThreadTime));
        System.out.println("Errors: " + errors.get());
        System.out.println("Error rate: " + String.format("%.2f%%", errorRate * 100));

        // Assertions
        assertTrue(completed, "Concurrent test should complete within timeout");
        assertTrue(operationsPerSecond > 1000, "Should handle high concurrent load");
        assertTrue(errorRate < 0.01, "Error rate should be very low");
    }

    @Test
    @DisplayName("Memory Usage and Eviction Performance")
    void testMemoryUsageAndEvictionPerformance() {
        System.out.println("=== MEMORY USAGE AND EVICTION PERFORMANCE ===");

        // Fill cache beyond memory limit to test eviction
        int largeDatasetSize = 50000; // This should exceed the 256MB limit
        String largeValue = "x".repeat(1000); // 1KB per value

        long fillStart = System.currentTimeMillis();
        for (int i = 0; i < largeDatasetSize; i++) {
            String key = "memory:test:" + i;
            stringRedisTemplate.opsForValue().set(key, largeValue + i);
            
            // Check memory usage periodically
            if (i % 10000 == 0) {
                System.out.println("Inserted " + i + " records...");
            }
        }
        long fillTime = System.currentTimeMillis() - fillStart;

        System.out.println("Filled cache with " + largeDatasetSize + " records in " + fillTime + "ms");

        // Test access patterns after eviction
        int accessTests = 1000;
        int foundCount = 0;
        long accessStart = System.currentTimeMillis();
        
        for (int i = 0; i < accessTests; i++) {
            String key = "memory:test:" + i;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                foundCount++;
            }
        }
        
        long accessTime = System.currentTimeMillis() - accessStart;
        double foundRate = (double) foundCount / accessTests;

        System.out.println("Access test completed in " + accessTime + "ms");
        System.out.println("Found " + foundCount + " out of " + accessTests + " early records");
        System.out.println("Early record retention rate: " + String.format("%.2f%%", foundRate * 100));

        // Test recent data retention
        int recentTests = 1000;
        int recentFound = 0;
        for (int i = largeDatasetSize - recentTests; i < largeDatasetSize; i++) {
            String key = "memory:test:" + i;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                recentFound++;
            }
        }
        
        double recentRetentionRate = (double) recentFound / recentTests;
        System.out.println("Recent record retention rate: " + String.format("%.2f%%", recentRetentionRate * 100));

        // Assertions
        assertTrue(recentRetentionRate > 0.8, "Recent records should be retained at high rate");
        assertTrue(accessTime < 1000, "Access should remain fast even after eviction");
    }

    @Test
    @DisplayName("Cache Expiration Performance")
    void testCacheExpirationPerformance() throws InterruptedException {
        System.out.println("=== CACHE EXPIRATION PERFORMANCE ===");

        int expirationTestSize = 1000;
        
        // Set keys with different expiration times
        long setStart = System.currentTimeMillis();
        for (int i = 0; i < expirationTestSize; i++) {
            String key = "expiration:test:" + i;
            String value = "expiring-value-" + i;
            
            if (i % 3 == 0) {
                // 1 second expiration
                stringRedisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(1));
            } else if (i % 3 == 1) {
                // 5 second expiration
                stringRedisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(5));
            } else {
                // 10 second expiration
                stringRedisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(10));
            }
        }
        long setTime = System.currentTimeMillis() - setStart;
        
        System.out.println("Set " + expirationTestSize + " keys with expiration in " + setTime + "ms");

        // Test immediate access
        int immediateFound = 0;
        for (int i = 0; i < expirationTestSize; i++) {
            String key = "expiration:test:" + i;
            if (stringRedisTemplate.opsForValue().get(key) != null) {
                immediateFound++;
            }
        }
        System.out.println("Immediately found: " + immediateFound + " keys");

        // Wait and test after 2 seconds
        Thread.sleep(2000);
        int afterTwoSeconds = 0;
        for (int i = 0; i < expirationTestSize; i++) {
            String key = "expiration:test:" + i;
            if (stringRedisTemplate.opsForValue().get(key) != null) {
                afterTwoSeconds++;
            }
        }
        System.out.println("After 2 seconds found: " + afterTwoSeconds + " keys");

        // Wait and test after 6 seconds total
        Thread.sleep(4000);
        int afterSixSeconds = 0;
        for (int i = 0; i < expirationTestSize; i++) {
            String key = "expiration:test:" + i;
            if (stringRedisTemplate.opsForValue().get(key) != null) {
                afterSixSeconds++;
            }
        }
        System.out.println("After 6 seconds found: " + afterSixSeconds + " keys");

        // Assertions
        assertEquals(expirationTestSize, immediateFound, "All keys should be found immediately");
        assertTrue(afterTwoSeconds < immediateFound, "Some keys should expire after 2 seconds");
        assertTrue(afterSixSeconds < afterTwoSeconds, "More keys should expire after 6 seconds");
    }

    private void clearRedisCache() {
        if (redisTemplate.getConnectionFactory() == null) {
            return;
        }
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
