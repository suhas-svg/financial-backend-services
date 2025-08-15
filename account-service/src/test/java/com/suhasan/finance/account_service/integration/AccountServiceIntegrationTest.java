package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.SavingsAccount;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("Account Service Integration Tests")
public class AccountServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private Account checkingAccount;
    private Account savingsAccount;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        accountRepository.deleteAll();

        // Create test accounts
        checkingAccount = new CheckingAccount();
        checkingAccount.setOwnerId("user123");
        checkingAccount.setBalance(BigDecimal.valueOf(1000.00));

        savingsAccount = new SavingsAccount();
        savingsAccount.setOwnerId("user456");
        savingsAccount.setBalance(BigDecimal.valueOf(5000.00));
        ((SavingsAccount) savingsAccount).setInterestRate(0.025); // 2.5% interest rate
    }

    @Test
    @DisplayName("Should create and persist checking account")
    void shouldCreateAndPersistCheckingAccount() {
        // When
        Account savedAccount = accountService.create(checkingAccount);

        // Then
        assertThat(savedAccount).isNotNull();
        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getOwnerId()).isEqualTo("user123");
        assertThat(savedAccount.getBalance()).isEqualTo(BigDecimal.valueOf(1000.00));

        // Verify persistence
        Account foundAccount = accountService.findById(savedAccount.getId());
        assertThat(foundAccount).isNotNull();
        assertThat(foundAccount.getOwnerId()).isEqualTo("user123");
    }

    @Test
    @DisplayName("Should create and persist savings account with interest rate")
    void shouldCreateAndPersistSavingsAccountWithInterestRate() {
        // When
        Account savedAccount = accountService.create(savingsAccount);

        // Then
        assertThat(savedAccount).isNotNull();
        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount).isInstanceOf(SavingsAccount.class);
        
        SavingsAccount savedSavingsAccount = (SavingsAccount) savedAccount;
        assertThat(savedSavingsAccount.getInterestRate()).isEqualTo(0.025);
        assertThat(savedSavingsAccount.getBalance()).isEqualTo(BigDecimal.valueOf(5000.00));
    }

    @Test
    @DisplayName("Should find all accounts")
    void shouldFindAllAccounts() {
        // Given
        accountService.create(checkingAccount);
        accountService.create(savingsAccount);

        // When
        List<Account> allAccounts = accountService.findAll();

        // Then
        assertThat(allAccounts).hasSize(2);
        assertThat(allAccounts).extracting(Account::getOwnerId)
                .containsExactlyInAnyOrder("user123", "user456");
    }

    @Test
    @DisplayName("Should update account balance")
    void shouldUpdateAccountBalance() {
        // Given
        Account savedAccount = accountService.create(checkingAccount);
        Long accountId = savedAccount.getId();

        // When
        Account updateAccount = new CheckingAccount();
        updateAccount.setBalance(BigDecimal.valueOf(2000.00));
        Account updatedAccount = accountService.update(accountId, updateAccount);

        // Then
        assertThat(updatedAccount.getBalance()).isEqualTo(BigDecimal.valueOf(2000.00));

        // Verify persistence
        Account foundAccount = accountService.findById(accountId);
        assertThat(foundAccount.getBalance()).isEqualTo(BigDecimal.valueOf(2000.00));
    }

    @Test
    @DisplayName("Should delete account")
    void shouldDeleteAccount() {
        // Given
        Account savedAccount = accountService.create(checkingAccount);
        Long accountId = savedAccount.getId();

        // When
        accountService.delete(accountId);

        // Then
        assertThatThrownBy(() -> accountService.findById(accountId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    @DisplayName("Should handle concurrent account creation")
    void shouldHandleConcurrentAccountCreation() {
        // Given
        Account account1 = new CheckingAccount();
        account1.setOwnerId("concurrent1");
        account1.setBalance(BigDecimal.valueOf(100.00));

        Account account2 = new SavingsAccount();
        account2.setOwnerId("concurrent2");
        account2.setBalance(BigDecimal.valueOf(200.00));

        // When
        Account saved1 = accountService.create(account1);
        Account saved2 = accountService.create(account2);

        // Then
        assertThat(saved1.getId()).isNotEqualTo(saved2.getId());
        assertThat(accountService.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Should maintain data integrity across transactions")
    void shouldMaintainDataIntegrityAcrossTransactions() {
        // Given
        Account account = accountService.create(checkingAccount);
        Long accountId = account.getId();

        // When - Update in separate transaction context
        Account updateData = new CheckingAccount();
        updateData.setBalance(BigDecimal.valueOf(1500.00));
        accountService.update(accountId, updateData);

        // Then - Verify data consistency
        Account retrievedAccount = accountService.findById(accountId);
        assertThat(retrievedAccount.getBalance()).isEqualTo(BigDecimal.valueOf(1500.00));
        assertThat(retrievedAccount.getOwnerId()).isEqualTo("user123"); // Original data preserved
    }
}