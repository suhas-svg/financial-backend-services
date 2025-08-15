package com.suhasan.finance.transaction_service.performance;

import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Database Performance Benchmark")
public class DatabasePerformanceBenchmark {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("transaction_perf_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("performance-test-schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DataSource dataSource;

    private static final int SMALL_DATASET = 1000;
    private static final int MEDIUM_DATASET = 10000;
    private static final int LARGE_DATASET = 100000;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Bulk Insert Performance Test")
    @Transactional
    void testBulkInsertPerformance() {
        System.out.println("=== BULK INSERT PERFORMANCE TEST ===");

        // Test different batch sizes
        int[] batchSizes = {100, 500, 1000, 2000};
        
        for (int batchSize : batchSizes) {
            transactionRepository.deleteAll();
            
            List<Transaction> transactions = generateTransactions(MEDIUM_DATASET);
            
            long startTime = System.currentTimeMillis();
            
            // Insert in batches
            for (int i = 0; i < transactions.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, transactions.size());
                List<Transaction> batch = transactions.subList(i, endIndex);
                transactionRepository.saveAll(batch);
                
                // Flush every batch to measure actual database performance
                transactionRepository.flush();
            }
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            double recordsPerSecond = (double) MEDIUM_DATASET / (totalTime / 1000.0);
            
            System.out.println("Batch size " + batchSize + ": " + totalTime + "ms (" + 
                    String.format("%.2f", recordsPerSecond) + " records/sec)");
            
            // Verify all records were inserted
            long count = transactionRepository.count();
            assertEquals(MEDIUM_DATASET, count, "All records should be inserted");
        }
    }

    @Test
    @DisplayName("Query Performance with Different Dataset Sizes")
    void testQueryPerformanceWithDifferentDatasetSizes() {
        System.out.println("=== QUERY PERFORMANCE WITH DIFFERENT DATASET SIZES ===");

        int[] datasetSizes = {SMALL_DATASET, MEDIUM_DATASET};
        
        for (int size : datasetSizes) {
            System.out.println("\n--- Testing with " + size + " records ---");
            
            // Setup data
            transactionRepository.deleteAll();
            List<Transaction> transactions = generateTransactions(size);
            transactionRepository.saveAll(transactions);
            
            // Test various query patterns
            testQueryPattern("Find by Account ID", size, () -> 
                transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                        "account-1", "account-1", 
                        org.springframework.data.domain.PageRequest.of(0, 10)));
            
            testQueryPattern("Find by Status", size, () -> 
                transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.COMPLETED));
            
            testQueryPattern("Find by Date Range", size, () -> 
                transactionRepository.findTransactionsWithFilters(
                        "account-1", null, null,
                        LocalDateTime.now().minusDays(7), LocalDateTime.now(),
                        null, null, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 10)));
            
            testQueryPattern("Count by Account", size, () -> 
                transactionRepository.countTransactionsByAccountAndDateRange(
                        "account-1", null, null));
            
            // Test pagination performance
            testPaginationPerformance(size);
        }
    }

    @Test
    @DisplayName("Index Performance Analysis")
    void testIndexPerformance() throws SQLException {
        System.out.println("=== INDEX PERFORMANCE ANALYSIS ===");
        
        // Create large dataset
        List<Transaction> transactions = generateTransactions(LARGE_DATASET);
        transactionRepository.saveAll(transactions);
        
        try (Connection conn = dataSource.getConnection()) {
            // Test query performance with EXPLAIN ANALYZE
            testQueryWithExplain(conn, 
                "SELECT * FROM transactions WHERE from_account_id = ?", 
                "account-1", 
                "Query by from_account_id (should use index)");
            
            testQueryWithExplain(conn,
                "SELECT * FROM transactions WHERE created_at BETWEEN ? AND ?",
                LocalDateTime.now().minusDays(7),
                "Query by date range (should use index)");
            
            testQueryWithExplain(conn,
                "SELECT * FROM transactions WHERE status = ?",
                TransactionStatus.COMPLETED.name(),
                "Query by status (should use index)");
            
            // Test query without index (description field)
            testQueryWithExplain(conn,
                "SELECT * FROM transactions WHERE description LIKE ?",
                "%test%",
                "Query by description (no index - should be slow)");
        }
    }

    @Test
    @DisplayName("Concurrent Read/Write Performance")
    void testConcurrentReadWritePerformance() throws InterruptedException {
        System.out.println("=== CONCURRENT READ/WRITE PERFORMANCE TEST ===");
        
        // Setup initial data
        List<Transaction> initialData = generateTransactions(MEDIUM_DATASET);
        transactionRepository.saveAll(initialData);
        
        int numThreads = 10;
        int operationsPerThread = 100;
        
        Thread[] threads = new Thread[numThreads];
        long[] threadTimes = new long[numThreads];
        
        long startTime = System.currentTimeMillis();
        
        // Create threads that perform mixed read/write operations
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                long threadStart = System.currentTimeMillis();
                
                for (int j = 0; j < operationsPerThread; j++) {
                    if (j % 3 == 0) {
                        // Write operation (33% of operations)
                        Transaction newTransaction = generateTransaction("thread-" + threadId + "-" + j);
                        transactionRepository.save(newTransaction);
                    } else {
                        // Read operation (67% of operations)
                        String accountId = "account-" + ThreadLocalRandom.current().nextInt(1, 100);
                        transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                                accountId, accountId, 
                                org.springframework.data.domain.PageRequest.of(0, 5));
                    }
                }
                
                threadTimes[threadId] = System.currentTimeMillis() - threadStart;
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Calculate statistics
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        long totalThreadTime = 0;
        
        for (long time : threadTimes) {
            minTime = Math.min(minTime, time);
            maxTime = Math.max(maxTime, time);
            totalThreadTime += time;
        }
        
        double avgThreadTime = (double) totalThreadTime / numThreads;
        int totalOperations = numThreads * operationsPerThread;
        double operationsPerSecond = (double) totalOperations / (totalTime / 1000.0);
        
        System.out.println("Total time: " + totalTime + "ms");
        System.out.println("Average thread time: " + String.format("%.2f", avgThreadTime) + "ms");
        System.out.println("Min thread time: " + minTime + "ms");
        System.out.println("Max thread time: " + maxTime + "ms");
        System.out.println("Operations per second: " + String.format("%.2f", operationsPerSecond));
        
        // Verify data integrity
        long finalCount = transactionRepository.count();
        long expectedWrites = numThreads * operationsPerThread / 3; // 33% were writes
        assertEquals(MEDIUM_DATASET + expectedWrites, finalCount, 
                "Final count should include initial data plus new writes");
    }

    @Test
    @DisplayName("Memory Usage and Connection Pool Performance")
    void testMemoryAndConnectionPoolPerformance() {
        System.out.println("=== MEMORY USAGE AND CONNECTION POOL PERFORMANCE ===");
        
        Runtime runtime = Runtime.getRuntime();
        
        // Measure memory before
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Perform memory-intensive operations
        List<List<Transaction>> batches = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<Transaction> batch = generateTransactions(1000);
            transactionRepository.saveAll(batch);
            
            // Keep some batches in memory to test memory usage
            if (i % 2 == 0) {
                batches.add(batch);
            }
        }
        
        // Measure memory after
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        System.out.println("Memory used: " + (memoryUsed / 1024 / 1024) + " MB");
        
        // Test connection pool under load
        long connectionTestStart = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            transactionRepository.count(); // Simple query to test connection acquisition
        }
        long connectionTestTime = System.currentTimeMillis() - connectionTestStart;
        
        System.out.println("100 connection acquisitions took: " + connectionTestTime + "ms");
        System.out.println("Average connection acquisition time: " + 
                String.format("%.2f", (double) connectionTestTime / 100) + "ms");
        
        // Assertions
        assertTrue(memoryUsed < 500 * 1024 * 1024, "Memory usage should be reasonable (< 500MB)");
        assertTrue(connectionTestTime < 1000, "Connection pool should be fast (< 1000ms for 100 acquisitions)");
    }

    private void testQueryPattern(String queryName, int datasetSize, Runnable query) {
        // Warm up
        for (int i = 0; i < 5; i++) {
            query.run();
        }
        
        // Measure performance
        int iterations = 50;
        long totalTime = 0;
        
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            query.run();
            totalTime += System.currentTimeMillis() - start;
        }
        
        double avgTime = (double) totalTime / iterations;
        System.out.println(queryName + " (dataset: " + datasetSize + "): " + 
                String.format("%.2f", avgTime) + "ms avg");
    }

    private void testPaginationPerformance(int datasetSize) {
        System.out.println("Testing pagination performance with " + datasetSize + " records:");
        
        int[] pageSizes = {10, 50, 100, 500};
        
        for (int pageSize : pageSizes) {
            Pageable pageable = PageRequest.of(0, pageSize, Sort.by("createdAt").descending());
            
            long start = System.currentTimeMillis();
            Page<Transaction> page = transactionRepository.findAll(pageable);
            long time = System.currentTimeMillis() - start;
            
            System.out.println("  Page size " + pageSize + ": " + time + "ms (" + 
                    page.getTotalElements() + " total elements)");
            
            assertTrue(time < 500, "Pagination should be fast even with large datasets");
        }
    }

    private void testQueryWithExplain(Connection conn, String sql, Object param, String description) 
            throws SQLException {
        String explainSql = "EXPLAIN ANALYZE " + sql;
        
        try (PreparedStatement stmt = conn.prepareStatement(explainSql)) {
            if (param instanceof String) {
                stmt.setString(1, (String) param);
            } else if (param instanceof LocalDateTime) {
                stmt.setObject(1, param);
                stmt.setObject(2, LocalDateTime.now());
            }
            
            long start = System.currentTimeMillis();
            ResultSet rs = stmt.executeQuery();
            long time = System.currentTimeMillis() - start;
            
            System.out.println("\n" + description + " (" + time + "ms):");
            while (rs.next()) {
                System.out.println("  " + rs.getString(1));
            }
        }
    }

    private List<Transaction> generateTransactions(int count) {
        List<Transaction> transactions = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(30);
        
        for (int i = 0; i < count; i++) {
            transactions.add(generateTransaction("bulk-" + i));
        }
        
        return transactions;
    }

    private Transaction generateTransaction(String suffix) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setFromAccountId("account-" + ThreadLocalRandom.current().nextInt(1, 100));
        transaction.setToAccountId("account-" + ThreadLocalRandom.current().nextInt(101, 200));
        transaction.setAmount(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(10, 1000)));
        transaction.setCurrency("USD");
        transaction.setType(TransactionType.values()[ThreadLocalRandom.current().nextInt(TransactionType.values().length)]);
        transaction.setStatus(TransactionStatus.values()[ThreadLocalRandom.current().nextInt(TransactionStatus.values().length)]);
        transaction.setDescription("Performance test transaction " + suffix);
        transaction.setCreatedAt(LocalDateTime.now().minusDays(ThreadLocalRandom.current().nextInt(0, 30)));
        transaction.setProcessedAt(LocalDateTime.now().minusDays(ThreadLocalRandom.current().nextInt(0, 30)));
        transaction.setCreatedBy("testuser");
        return transaction;
    }
}