package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@SuppressWarnings("resource")
class TransactionRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("transactiondb_repo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void findTransactionsWithFilters_AllowsNullDescriptionFilter() {
        Transaction transaction = transactionRepository.saveAndFlush(buildTransaction(
                "account-1",
                "account-2",
                "Salary Payment",
                "user-1",
                LocalDateTime.now().minusHours(2)));

        Pageable pageable = PageRequest.of(0, 10);

        Page<Transaction> result = transactionRepository.findTransactionsWithFilters(
                "account-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "user-1",
                pageable);

        assertThat(result.getContent())
                .extracting(Transaction::getTransactionId)
                .contains(transaction.getTransactionId());
    }

    @Test
    void findTransactionsWithFilters_AppliesDescriptionPatternCaseInsensitively() {
        transactionRepository.saveAndFlush(buildTransaction(
                "account-1",
                "account-2",
                "Salary Payment",
                "user-1",
                LocalDateTime.now().minusHours(1)));
        transactionRepository.saveAndFlush(buildTransaction(
                "account-1",
                "account-3",
                "Monthly rent",
                "user-1",
                LocalDateTime.now().minusMinutes(30)));

        Page<Transaction> result = transactionRepository.findTransactionsWithFilters(
                "account-1",
                null,
                null,
                null,
                null,
                null,
                null,
                "%payment%",
                null,
                "user-1",
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .hasSize(1)
                .extracting(Transaction::getDescription)
                .containsExactly("Salary Payment");
    }

    private Transaction buildTransaction(String fromAccountId,
                                         String toAccountId,
                                         String description,
                                         String createdBy,
                                         LocalDateTime createdAt) {
        return Transaction.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description(description)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .build();
    }
}
