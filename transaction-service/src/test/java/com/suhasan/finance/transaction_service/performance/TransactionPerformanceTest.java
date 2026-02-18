package com.suhasan.finance.transaction_service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.dto.DepositRequest;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(
        classes = com.suhasan.finance.transaction_service.TransactionServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureWebMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisplayName("Transaction Performance Tests")
@SuppressWarnings({"resource", "null"})
public class TransactionPerformanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("transaction_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final int CONCURRENT_USERS = 50;
    private static final int TRANSACTIONS_PER_USER = 20;
    private static final long PERFORMANCE_THRESHOLD_MS = 1000; // 1 second max response time
    private static final double SUCCESS_RATE_THRESHOLD = 0.95; // 95% success rate

    @BeforeEach
    void setUp() {
        // Clear existing data
        transactionRepository.deleteAll();
        clearRedisCache();
    }

    @Test
    @DisplayName("Load Test - Concurrent Transaction Processing")
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testConcurrentTransactionProcessing() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        List<Long> responseTimes = new CopyOnWriteArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < TRANSACTIONS_PER_USER; j++) {
                        long requestStart = System.currentTimeMillis();
                        
                        TransferRequest request = TransferRequest.builder()
                                .fromAccountId("account-" + userId)
                                .toAccountId("account-" + (userId + 1000))
                                .amount(BigDecimal.valueOf(100.00))
                                .currency("USD")
                                .description("Load test transfer " + j)
                                .build();

                        try {
                            MvcResult result = mockMvc.perform(post("/api/transactions/transfer")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                                    .andReturn();

                            long responseTime = System.currentTimeMillis() - requestStart;
                            responseTimes.add(responseTime);
                            totalResponseTime.addAndGet(responseTime);

                            if (result.getResponse().getStatus() == 200) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            long responseTime = System.currentTimeMillis() - requestStart;
                            responseTimes.add(responseTime);
                            totalResponseTime.addAndGet(responseTime);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.MINUTES);
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Calculate performance metrics
        int totalTransactions = successCount.get() + failureCount.get();
        double successRate = (double) successCount.get() / totalTransactions;
        double avgResponseTime = (double) totalResponseTime.get() / totalTransactions;
        double throughput = (double) totalTransactions / (totalTime / 1000.0); // transactions per second

        // Calculate percentiles
        responseTimes.sort(Long::compareTo);
        long p50 = getPercentile(responseTimes, 0.5);
        long p95 = getPercentile(responseTimes, 0.95);
        long p99 = getPercentile(responseTimes, 0.99);

        // Log performance results
        System.out.println("=== CONCURRENT TRANSACTION PROCESSING PERFORMANCE RESULTS ===");
        System.out.println("Total Time: " + totalTime + "ms");
        System.out.println("Total Transactions: " + totalTransactions);
        System.out.println("Successful Transactions: " + successCount.get());
        System.out.println("Failed Transactions: " + failureCount.get());
        System.out.println("Success Rate: " + String.format("%.2f%%", successRate * 100));
        System.out.println("Average Response Time: " + String.format("%.2fms", avgResponseTime));
        System.out.println("Throughput: " + String.format("%.2f transactions/second", throughput));
        System.out.println("Response Time P50: " + p50 + "ms");
        System.out.println("Response Time P95: " + p95 + "ms");
        System.out.println("Response Time P99: " + p99 + "ms");

        // Assertions
        assertTrue(completed, "Load test should complete within timeout");
        assertTrue(successRate >= SUCCESS_RATE_THRESHOLD, 
                "Success rate should be at least " + (SUCCESS_RATE_THRESHOLD * 100) + "%");
        assertTrue(p95 <= PERFORMANCE_THRESHOLD_MS, 
                "95th percentile response time should be under " + PERFORMANCE_THRESHOLD_MS + "ms");
        assertTrue(throughput > 10, "Throughput should be at least 10 transactions per second");
    }

    @Test
    @DisplayName("Database Performance Test - Large Dataset Queries")
    @Transactional
    void testDatabaseQueryPerformanceWithLargeDataset() {
        // Create large dataset
        int recordCount = 10000;
        List<Transaction> transactions = new ArrayList<>();
        
        for (int i = 0; i < recordCount; i++) {
            Transaction transaction = new Transaction();
            transaction.setTransactionId(UUID.randomUUID().toString());
            transaction.setFromAccountId("account-" + (i % 100)); // 100 different accounts
            transaction.setToAccountId("account-" + ((i + 50) % 100));
            transaction.setAmount(BigDecimal.valueOf(100.00 + i));
            transaction.setCurrency("USD");
            transaction.setType(TransactionType.TRANSFER);
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setDescription("Performance test transaction " + i);
            transaction.setCreatedAt(LocalDateTime.now().minusDays(i % 30)); // Spread over 30 days
            transaction.setProcessedAt(LocalDateTime.now().minusDays(i % 30));
            transaction.setCreatedBy("testuser");
            transactions.add(transaction);
        }

        // Batch insert for better performance
        long insertStart = System.currentTimeMillis();
        transactionRepository.saveAll(transactions);
        long insertTime = System.currentTimeMillis() - insertStart;

        System.out.println("=== DATABASE PERFORMANCE TEST RESULTS ===");
        System.out.println("Inserted " + recordCount + " records in " + insertTime + "ms");
        System.out.println("Insert rate: " + String.format("%.2f records/second", 
                (double) recordCount / (insertTime / 1000.0)));

        // Test various query patterns
        testQueryPerformance("Find by Account ID", () -> 
                transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                        "account-1", "account-1", 
                        org.springframework.data.domain.PageRequest.of(0, 10)));

        testQueryPerformance("Find by Date Range", () -> 
                transactionRepository.findTransactionsWithFilters(
                        "account-1", null, null,
                        LocalDateTime.now().minusDays(7), 
                        LocalDateTime.now(),
                        null, null, null, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 10)));

        testQueryPerformance("Find by Status", () -> 
                transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.COMPLETED));

        testQueryPerformance("Count by Account", () -> 
                transactionRepository.countTransactionsByAccountAndDateRange(
                        "account-1", null, null));

        // Test pagination performance
        long paginationStart = System.currentTimeMillis();
        var page = transactionRepository.findAll(
                org.springframework.data.domain.PageRequest.of(0, 100));
        long paginationTime = System.currentTimeMillis() - paginationStart;
        assertNotNull(page);
        assertFalse(page.getContent().isEmpty());
        
        System.out.println("Pagination query (100 records): " + paginationTime + "ms");
        assertTrue(paginationTime < 500, "Pagination should be fast even with large dataset");
    }

    @Test
    @DisplayName("Cache Performance Test - Hit Rates and Response Times")
    void testCachePerformance() {
        // Warm up cache with some data
        String cacheKey = "transaction:history:account-1";
        List<String> testData = List.of("tx1", "tx2", "tx3", "tx4", "tx5");
        redisTemplate.opsForList().rightPushAll(cacheKey, testData.toArray());

        int iterations = 1000;
        AtomicLong cacheHitTime = new AtomicLong(0);
        AtomicLong cacheMissTime = new AtomicLong(0);
        AtomicInteger hits = new AtomicInteger(0);
        AtomicInteger misses = new AtomicInteger(0);

        // Test cache hits
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            List<Object> result = redisTemplate.opsForList().range(cacheKey, 0, -1);
            long time = System.currentTimeMillis() - start;
            
            if (result != null && !result.isEmpty()) {
                hits.incrementAndGet();
                cacheHitTime.addAndGet(time);
            } else {
                misses.incrementAndGet();
                cacheMissTime.addAndGet(time);
            }
        }

        // Test cache misses
        for (int i = 0; i < 100; i++) {
            String missKey = "transaction:history:nonexistent-" + i;
            long start = System.currentTimeMillis();
            List<Object> result = redisTemplate.opsForList().range(missKey, 0, -1);
            long time = System.currentTimeMillis() - start;
            
            if (result == null || result.isEmpty()) {
                misses.incrementAndGet();
                cacheMissTime.addAndGet(time);
            }
        }

        double hitRate = (double) hits.get() / (hits.get() + misses.get());
        double avgHitTime = hits.get() > 0 ? (double) cacheHitTime.get() / hits.get() : 0;
        double avgMissTime = misses.get() > 0 ? (double) cacheMissTime.get() / misses.get() : 0;

        System.out.println("=== CACHE PERFORMANCE TEST RESULTS ===");
        System.out.println("Cache Hits: " + hits.get());
        System.out.println("Cache Misses: " + misses.get());
        System.out.println("Hit Rate: " + String.format("%.2f%%", hitRate * 100));
        System.out.println("Average Hit Time: " + String.format("%.2fms", avgHitTime));
        System.out.println("Average Miss Time: " + String.format("%.2fms", avgMissTime));

        // Assertions
        assertTrue(avgHitTime < 10, "Cache hits should be very fast (< 10ms)");
        assertTrue(avgMissTime < 50, "Cache misses should still be reasonably fast (< 50ms)");
        assertTrue(hitRate > 0.8, "Hit rate should be high for repeated queries");
    }

    @Test
    @DisplayName("Stress Test - System Behavior Under Extreme Load")
    void testSystemUnderExtremeLoad() throws InterruptedException {
        int extremeUsers = 100;
        int transactionsPerUser = 10;
        ExecutorService executor = Executors.newFixedThreadPool(extremeUsers);
        CountDownLatch latch = new CountDownLatch(extremeUsers);
        
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger timeoutErrors = new AtomicInteger(0);
        AtomicInteger serverErrors = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < extremeUsers; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < transactionsPerUser; j++) {
                        totalRequests.incrementAndGet();
                        
                        DepositRequest request = DepositRequest.builder()
                                .accountId("stress-account-" + userId)
                                .amount(BigDecimal.valueOf(50.00))
                                .currency("USD")
                                .description("Stress test deposit")
                                .build();

                        try {
                            MvcResult result = mockMvc.perform(post("/api/transactions/deposit")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                                    .andReturn();

                            int status = result.getResponse().getStatus();
                            if (status == 200 || status == 201) {
                                successfulRequests.incrementAndGet();
                            } else if (status >= 500) {
                                serverErrors.incrementAndGet();
                            }
                        } catch (Exception e) {
                            if (e.getMessage().contains("timeout")) {
                                timeoutErrors.incrementAndGet();
                            } else {
                                serverErrors.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.MINUTES);
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        double successRate = (double) successfulRequests.get() / totalRequests.get();
        double errorRate = (double) (timeoutErrors.get() + serverErrors.get()) / totalRequests.get();

        System.out.println("=== STRESS TEST RESULTS ===");
        System.out.println("Total Time: " + totalTime + "ms");
        System.out.println("Total Requests: " + totalRequests.get());
        System.out.println("Successful Requests: " + successfulRequests.get());
        System.out.println("Timeout Errors: " + timeoutErrors.get());
        System.out.println("Server Errors: " + serverErrors.get());
        System.out.println("Success Rate: " + String.format("%.2f%%", successRate * 100));
        System.out.println("Error Rate: " + String.format("%.2f%%", errorRate * 100));

        // Under extreme load, we expect some degradation but system should remain stable
        assertTrue(completed, "Stress test should complete within timeout");
        assertTrue(successRate > 0.7, "Even under stress, success rate should be > 70%");
        assertTrue(errorRate < 0.3, "Error rate should be manageable under stress");
    }

    private void testQueryPerformance(String queryName, Runnable query) {
        // Warm up
        for (int i = 0; i < 10; i++) {
            query.run();
        }

        // Measure performance
        int iterations = 100;
        long totalTime = 0;
        
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            query.run();
            totalTime += System.currentTimeMillis() - start;
        }

        double avgTime = (double) totalTime / iterations;
        System.out.println(queryName + " - Average time: " + String.format("%.2fms", avgTime));
        
        // Assert reasonable performance
        assertTrue(avgTime < 100, queryName + " should complete in under 100ms on average");
    }

    private long getPercentile(List<Long> sortedList, double percentile) {
        if (sortedList.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sortedList.size()) - 1;
        return sortedList.get(Math.max(0, Math.min(index, sortedList.size() - 1)));
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
