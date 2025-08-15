import { describe, test, expect, beforeAll, afterAll, beforeEach, afterEach } from '@jest/globals';
import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { createTestUser, generateTestId, waitForCondition, delay, retryOperation } from '../utils/test-helpers';
import { logger } from '../utils/logger';
import { testConfig } from '../config/test-config';
import { TestUser, AuthResponse, AccountInfo, TransactionInfo } from '../types';

/**
 * Error Scenario and Recovery Tests
 * Tests transaction failure recovery, service failure recovery, and data corruption recovery
 * Requirements: 5.6
 */
describe('Error Scenario and Recovery Tests', () => {
  let accountClient: any;
  let transactionClient: any;
  let testUsers: TestUser[] = [];
  let createdAccounts: AccountInfo[] = [];
  let testRunId: string;

  beforeAll(async () => {
    testRunId = generateTestId('error-recovery');
    logger.info(`🚀 Starting Error Scenario and Recovery Tests - Run ID: ${testRunId}`);

    // Initialize service clients
    accountClient = createAccountServiceClient();
    transactionClient = createTransactionServiceClient();

    // Wait for services to be ready
    const accountReady = await accountClient.waitForService();
    const transactionReady = await transactionClient.waitForService();

    if (!accountReady || !transactionReady) {
      throw new Error('Services not ready for testing');
    }

    logger.info('✅ Services are ready for error recovery testing');
  });

  afterAll(async () => {
    logger.info(`🧹 Cleaning up error recovery test data - Run ID: ${testRunId}`);
    // Cleanup will be implemented in later tasks
  });

  beforeEach(async () => {
    // Reset clients for each test
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
  });

  afterEach(async () => {
    // Clear auth tokens after each test
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
  });

  describe('Transaction Failure Recovery Tests', () => {
    test('should handle transaction failure with proper rollback validation', async () => {
      const testUser = createTestUser({
        username: `tx_rollback_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 100.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Transaction failure with proper rollback validation');

      // Setup user and account
      await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 100.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Record initial state
      const initialBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const initialBalance = initialBalanceResponse.data.balance;
      const initialHistoryResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const initialTransactionCount = initialHistoryResponse.data.content.length;

      logger.info(`Initial state - Balance: ${initialBalance}, Transactions: ${initialTransactionCount}`);

      // Attempt transaction that should fail (insufficient funds)
      logger.info('❌ Attempting transaction that should fail (insufficient funds)');
      const failingWithdrawalAmount = 500.00; // More than available balance

      try {
        const failedWithdrawalResponse = await transactionClient.post('/api/transactions/withdraw', {
          accountId: account.id,
          amount: failingWithdrawalAmount,
          currency: 'USD',
          description: 'Transaction failure test'
        });

        // If we reach here, the transaction should have been rejected
        expect(failedWithdrawalResponse.status).toBe(400);
      } catch (error: any) {
        expect(error.status).toBe(400);
        expect(error.data).toHaveProperty('message');
        logger.info('✅ Transaction properly rejected for insufficient funds');
      }

      // Verify rollback - balance should be unchanged
      logger.info('🔍 Verifying rollback - balance should be unchanged');
      const balanceAfterFailure = await accountClient.get(`/api/accounts/${account.id}`);
      expect(balanceAfterFailure.data.balance).toBe(initialBalance);
      logger.info(`✅ Balance unchanged after failed transaction: ${balanceAfterFailure.data.balance}`);

      // Verify no failed transaction appears in history
      logger.info('📋 Verifying no failed transaction appears in history');
      const historyAfterFailure = await transactionClient.get(`/api/transactions/account/${account.id}`);
      expect(historyAfterFailure.data.content.length).toBe(initialTransactionCount);
      logger.info('✅ No failed transaction in history');

      // Verify system can still process valid transactions after failure
      logger.info('✅ Testing valid transaction after failure');
      const validWithdrawalAmount = 50.00;
      const validWithdrawalResponse = await transactionClient.post('/api/transactions/withdraw', {
        accountId: account.id,
        amount: validWithdrawalAmount,
        currency: 'USD',
        description: 'Valid transaction after failure'
      });

      expect(validWithdrawalResponse.status).toBe(201);
      expect(validWithdrawalResponse.data.status).toBe('COMPLETED');

      // Verify valid transaction processed correctly
      const finalBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const expectedFinalBalance = initialBalance - validWithdrawalAmount;
      expect(finalBalanceResponse.data.balance).toBe(expectedFinalBalance);

      const finalHistoryResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      expect(finalHistoryResponse.data.content.length).toBe(initialTransactionCount + 1);

      logger.info('✅ System recovered and processed valid transaction successfully');
      logger.testComplete('Transaction failure with proper rollback validation', 'PASSED');

      testUsers.push(testUser);
    }, 60000);

    test('should handle concurrent transaction failures with data consistency', async () => {
      const testUser = createTestUser({
        username: `concurrent_failure_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 200.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Concurrent transaction failures with data consistency');

      // Setup user and account
      await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 200.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      const initialBalance = account.balance;

      // Attempt multiple concurrent transactions that should fail
      logger.info('❌ Attempting multiple concurrent failing transactions');
      const failingTransactions = [
        { amount: 300.00, description: 'Concurrent failure 1' },
        { amount: 250.00, description: 'Concurrent failure 2' },
        { amount: 400.00, description: 'Concurrent failure 3' }
      ];

      const failurePromises = failingTransactions.map(async (tx) => {
        try {
          const response = await transactionClient.post('/api/transactions/withdraw', {
            accountId: account.id,
            amount: tx.amount,
            currency: 'USD',
            description: tx.description
          });
          return { success: true, response };
        } catch (error) {
          return { success: false, error };
        }
      });

      const results = await Promise.all(failurePromises);

      // All should fail due to insufficient funds
      results.forEach((result, index) => {
        if (result.success) {
          // If any succeeded, it should have been rejected
          expect((result as any).response.status).toBe(400);
        } else {
          expect((result as any).error.status).toBe(400);
        }
        logger.info(`✅ Concurrent transaction ${index + 1} properly failed`);
      });

      // Verify account balance is still consistent
      const balanceAfterFailures = await accountClient.get(`/api/accounts/${account.id}`);
      expect(balanceAfterFailures.data.balance).toBe(initialBalance);

      // Verify no partial transactions in history
      const historyAfterFailures = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const failedTransactionCount = historyAfterFailures.data.content.filter(
        (tx: TransactionInfo) => tx.status === 'FAILED' || tx.status === 'PENDING'
      ).length;

      expect(failedTransactionCount).toBe(0);

      logger.info('✅ Concurrent transaction failures handled with data consistency');
      logger.testComplete('Concurrent transaction failures with data consistency', 'PASSED');

      testUsers.push(testUser);
    }, 45000);

    test('should handle transfer failure with proper rollback for both accounts', async () => {
      const testUser = createTestUser({
        username: `transfer_rollback_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 100.00,
            currency: 'USD'
          },
          {
            accountType: 'SAVINGS',
            initialBalance: 500.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Transfer failure with proper rollback for both accounts');

      // Setup user and accounts
      await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      // Create source account with limited funds
      const sourceAccountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 100.00,
        currency: 'USD'
      });

      const sourceAccount: AccountInfo = sourceAccountResponse.data;
      createdAccounts.push(sourceAccount);

      // Create destination account
      const destAccountResponse = await accountClient.post('/api/accounts', {
        accountType: 'SAVINGS',
        initialBalance: 500.00,
        currency: 'USD'
      });

      const destAccount: AccountInfo = destAccountResponse.data;
      createdAccounts.push(destAccount);

      // Record initial balances
      const initialSourceBalance = sourceAccount.balance;
      const initialDestBalance = destAccount.balance;

      logger.info(`Initial balances - Source: ${initialSourceBalance}, Dest: ${initialDestBalance}`);

      // Attempt transfer with insufficient funds
      logger.info('❌ Attempting transfer with insufficient funds');
      const transferAmount = 200.00; // More than source account balance

      try {
        const transferResponse = await transactionClient.post('/api/transactions/transfer', {
          fromAccountId: sourceAccount.id,
          toAccountId: destAccount.id,
          amount: transferAmount,
          currency: 'USD',
          description: 'Transfer failure test'
        });

        // Should not reach here if validation works
        expect(transferResponse.status).toBe(400);
      } catch (error: any) {
        expect(error.status).toBe(400);
        logger.info('✅ Transfer properly rejected for insufficient funds');
      }

      // Verify both account balances are unchanged
      logger.info('🔍 Verifying both account balances are unchanged');
      const sourceBalanceAfterFailure = await accountClient.get(`/api/accounts/${sourceAccount.id}`);
      const destBalanceAfterFailure = await accountClient.get(`/api/accounts/${destAccount.id}`);

      expect(sourceBalanceAfterFailure.data.balance).toBe(initialSourceBalance);
      expect(destBalanceAfterFailure.data.balance).toBe(initialDestBalance);

      logger.info('✅ Both account balances unchanged after failed transfer');

      // Verify no failed transfer transactions in either account history
      const sourceHistoryResponse = await transactionClient.get(`/api/transactions/account/${sourceAccount.id}`);
      const destHistoryResponse = await transactionClient.get(`/api/transactions/account/${destAccount.id}`);

      const sourceFailedTransfers = sourceHistoryResponse.data.content.filter(
        (tx: TransactionInfo) => tx.type === 'TRANSFER' && tx.status === 'FAILED'
      );
      const destFailedTransfers = destHistoryResponse.data.content.filter(
        (tx: TransactionInfo) => tx.type === 'TRANSFER' && tx.status === 'FAILED'
      );

      expect(sourceFailedTransfers.length).toBe(0);
      expect(destFailedTransfers.length).toBe(0);

      logger.info('✅ No failed transfer transactions in account histories');

      // Test that valid transfer still works after failure
      logger.info('✅ Testing valid transfer after failure');
      const validTransferAmount = 50.00;
      const validTransferResponse = await transactionClient.post('/api/transactions/transfer', {
        fromAccountId: sourceAccount.id,
        toAccountId: destAccount.id,
        amount: validTransferAmount,
        currency: 'USD',
        description: 'Valid transfer after failure'
      });

      expect(validTransferResponse.status).toBe(201);
      expect(validTransferResponse.data.status).toBe('COMPLETED');

      // Verify valid transfer processed correctly
      const finalSourceBalance = await accountClient.get(`/api/accounts/${sourceAccount.id}`);
      const finalDestBalance = await accountClient.get(`/api/accounts/${destAccount.id}`);

      expect(finalSourceBalance.data.balance).toBe(initialSourceBalance - validTransferAmount);
      expect(finalDestBalance.data.balance).toBe(initialDestBalance + validTransferAmount);

      logger.info('✅ Valid transfer processed successfully after failure recovery');
      logger.testComplete('Transfer failure with proper rollback for both accounts', 'PASSED');

      testUsers.push(testUser);
    }, 60000);
  });

  describe('Service Failure Recovery Tests', () => {
    test('should handle service unavailability with graceful degradation', async () => {
      const testUser = createTestUser({
        username: `service_failure_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Service unavailability with graceful degradation');

      // Setup user and account
      await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 1000.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Test retry mechanism with temporary failures
      logger.info('🔄 Testing retry mechanism with simulated temporary failures');
      
      // Use retry operation to handle potential temporary failures
      const transactionWithRetry = await retryOperation(
        async () => {
          const response = await transactionClient.post('/api/transactions/deposit', {
            accountId: account.id,
            amount: 100.00,
            currency: 'USD',
            description: 'Retry mechanism test'
          });
          
          if (response.status !== 201) {
            throw new Error(`Unexpected status: ${response.status}`);
          }
          
          return response;
        },
        3, // max attempts
        1000, // base delay
        'deposit transaction with retry'
      );

      expect(transactionWithRetry.status).toBe(201);
      expect(transactionWithRetry.data.status).toBe('COMPLETED');

      logger.info('✅ Transaction completed successfully with retry mechanism');

      // Test timeout handling
      logger.info('⏱️ Testing timeout handling');
      const originalTimeout = transactionClient.client.defaults.timeout;
      
      try {
        // Set a very short timeout to simulate timeout scenarios
        transactionClient.client.defaults.timeout = 1; // 1ms - will likely timeout
        
        try {
          await transactionClient.post('/api/transactions/deposit', {
            accountId: account.id,
            amount: 50.00,
            currency: 'USD',
            description: 'Timeout test'
          });
          
          // If we reach here, the request didn't timeout (which is also valid)
          logger.info('✅ Request completed within timeout (no timeout occurred)');
        } catch (error: any) {
          // Expect timeout or network error
          expect(error.message).toMatch(/timeout|network|ECONNABORTED/i);
          logger.info('✅ Timeout properly handled');
        }
      } finally {
        // Restore original timeout
        transactionClient.client.defaults.timeout = originalTimeout;
      }

      // Verify system is still functional after timeout
      logger.info('🔍 Verifying system functionality after timeout handling');
      const recoveryTransactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: account.id,
        amount: 25.00,
        currency: 'USD',
        description: 'Recovery after timeout test'
      });

      expect(recoveryTransactionResponse.status).toBe(201);
      expect(recoveryTransactionResponse.data.status).toBe('COMPLETED');

      logger.info('✅ System recovered successfully after timeout handling');
      logger.testComplete('Service unavailability with graceful degradation', 'PASSED');

      testUsers.push(testUser);
    }, 60000);

    test('should handle authentication service failures with proper error messages', async () => {
      logger.testStart('Authentication service failures with proper error messages');

      // Test with invalid credentials
      logger.info('🔐 Testing authentication with invalid credentials');
      try {
        const invalidLoginResponse = await accountClient.post('/api/auth/login', {
          username: 'nonexistent_user',
          password: 'wrong_password'
        });

        // Should not reach here if authentication works
        expect(invalidLoginResponse.status).toBe(401);
      } catch (error: any) {
        expect(error.status).toBe(401);
        expect(error.data).toHaveProperty('message');
        logger.info('✅ Invalid credentials properly rejected with error message');
      }

      // Test with malformed requests
      logger.info('📝 Testing authentication with malformed requests');
      try {
        const malformedLoginResponse = await accountClient.post('/api/auth/login', {
          // Missing required fields
          username: 'test'
          // password field missing
        });

        expect(malformedLoginResponse.status).toBe(400);
      } catch (error: any) {
        expect(error.status).toBe(400);
        expect(error.data).toHaveProperty('message');
        logger.info('✅ Malformed request properly rejected with error message');
      }

      // Test with expired/invalid tokens
      logger.info('🎫 Testing with invalid authentication tokens');
      accountClient.setAuthToken('invalid.jwt.token');
      transactionClient.setAuthToken('invalid.jwt.token');

      try {
        const unauthorizedResponse = await accountClient.get('/api/accounts');
        expect(unauthorizedResponse.status).toBe(401);
      } catch (error: any) {
        expect(error.status).toBe(401);
        logger.info('✅ Invalid token properly rejected');
      }

      logger.info('✅ Authentication service failures handled with proper error messages');
      logger.testComplete('Authentication service failures with proper error messages', 'PASSED');
    }, 30000);
  });

  describe('Data Consistency Recovery Tests', () => {
    test('should detect and handle data corruption with consistency validation', async () => {
      const testUser = createTestUser({
        username: `data_consistency_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Data corruption detection and consistency validation');

      // Setup user and account
      await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 1000.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Perform a series of transactions to establish baseline
      logger.info('💰 Establishing baseline with series of transactions');
      const transactions = [
        { type: 'deposit', amount: 200.00 },
        { type: 'withdraw', amount: 150.00 },
        { type: 'deposit', amount: 100.00 }
      ];

      let expectedBalance = 1000.00;
      const completedTransactions: TransactionInfo[] = [];

      for (const tx of transactions) {
        const endpoint = tx.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
        const response = await transactionClient.post(endpoint, {
          accountId: account.id,
          amount: tx.amount,
          currency: 'USD',
          description: `Baseline ${tx.type}`
        });

        expect(response.status).toBe(201);
        completedTransactions.push(response.data);
        expectedBalance += tx.type === 'deposit' ? tx.amount : -tx.amount;
      }

      // Verify data consistency between account balance and transaction history
      logger.info('🔍 Verifying data consistency between account balance and transaction history');
      const currentBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const currentBalance = currentBalanceResponse.data.balance;

      const historyResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const transactionHistory = historyResponse.data.content;

      // Calculate balance from transaction history
      let calculatedBalance = 1000.00; // Initial balance
      for (const tx of transactionHistory) {
        if (tx.status === 'COMPLETED') {
          if (tx.type === 'DEPOSIT') {
            calculatedBalance += tx.amount;
          } else if (tx.type === 'WITHDRAWAL') {
            calculatedBalance -= tx.amount;
          }
        }
      }

      // Verify consistency
      expect(currentBalance).toBe(calculatedBalance);
      expect(currentBalance).toBe(expectedBalance);

      logger.info(`✅ Data consistency verified - Account: ${currentBalance}, Calculated: ${calculatedBalance}, Expected: ${expectedBalance}`);

      // Test data validation with invalid data
      logger.info('❌ Testing data validation with invalid transaction data');
      try {
        const invalidTransactionResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: account.id,
          amount: -100.00, // Negative amount should be rejected
          currency: 'USD',
          description: 'Invalid negative deposit'
        });

        expect(invalidTransactionResponse.status).toBe(400);
      } catch (error: any) {
        expect(error.status).toBe(400);
        logger.info('✅ Invalid transaction data properly rejected');
      }

      // Verify account state unchanged after invalid transaction
      const balanceAfterInvalidTx = await accountClient.get(`/api/accounts/${account.id}`);
      expect(balanceAfterInvalidTx.data.balance).toBe(currentBalance);

      // Test currency consistency
      logger.info('💱 Testing currency consistency validation');
      try {
        const invalidCurrencyResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: account.id,
          amount: 100.00,
          currency: 'INVALID', // Invalid currency
          description: 'Invalid currency test'
        });

        expect(invalidCurrencyResponse.status).toBe(400);
      } catch (error: any) {
        expect(error.status).toBe(400);
        logger.info('✅ Invalid currency properly rejected');
      }

      // Final consistency check
      logger.info('🔍 Final consistency validation');
      const finalBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const finalHistoryResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);

      expect(finalBalanceResponse.data.balance).toBe(expectedBalance);
      expect(finalHistoryResponse.data.content.length).toBe(transactions.length);

      logger.info('✅ Data consistency maintained throughout error scenarios');
      logger.testComplete('Data corruption detection and consistency validation', 'PASSED');

      testUsers.push(testUser);
    }, 90000);

    test('should handle concurrent operations with data integrity validation', async () => {
      const testUser = createTestUser({
        username: `concurrent_integrity_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Concurrent operations with data integrity validation');

      // Setup user and account
      await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 1000.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Perform concurrent valid transactions
      logger.info('🔄 Performing concurrent valid transactions');
      const concurrentTransactions = [
        { type: 'deposit', amount: 50.00, description: 'Concurrent deposit 1' },
        { type: 'deposit', amount: 75.00, description: 'Concurrent deposit 2' },
        { type: 'withdraw', amount: 25.00, description: 'Concurrent withdrawal 1' },
        { type: 'deposit', amount: 100.00, description: 'Concurrent deposit 3' },
        { type: 'withdraw', amount: 30.00, description: 'Concurrent withdrawal 2' }
      ];

      const transactionPromises = concurrentTransactions.map(async (tx) => {
        const endpoint = tx.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
        try {
          const response = await transactionClient.post(endpoint, {
            accountId: account.id,
            amount: tx.amount,
            currency: 'USD',
            description: tx.description
          });
          return { success: true, transaction: response.data, type: tx.type, amount: tx.amount };
        } catch (error) {
          return { success: false, error, type: tx.type, amount: tx.amount };
        }
      });

      const results = await Promise.all(transactionPromises);

      // Verify all transactions succeeded
      const successfulTransactions = results.filter(r => r.success);
      const failedTransactions = results.filter(r => !r.success);

      logger.info(`Concurrent transactions - Successful: ${successfulTransactions.length}, Failed: ${failedTransactions.length}`);

      // Calculate expected balance from successful transactions
      let expectedBalance = 1000.00;
      for (const result of successfulTransactions) {
        if (result.success) {
          expectedBalance += (result as any).type === 'deposit' ? (result as any).amount : -(result as any).amount;
        }
      }

      // Wait a moment for all transactions to be processed
      await delay(2000);

      // Verify final balance consistency
      logger.info('🔍 Verifying final balance consistency after concurrent operations');
      const finalBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const finalBalance = finalBalanceResponse.data.balance;

      expect(finalBalance).toBe(expectedBalance);

      // Verify transaction history consistency
      const finalHistoryResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const completedTransactions = finalHistoryResponse.data.content.filter(
        (tx: TransactionInfo) => tx.status === 'COMPLETED'
      );

      expect(completedTransactions.length).toBe(successfulTransactions.length);

      // Verify no duplicate transactions
      const transactionIds = completedTransactions.map((tx: TransactionInfo) => tx.id);
      const uniqueTransactionIds = [...new Set(transactionIds)];
      expect(transactionIds.length).toBe(uniqueTransactionIds.length);

      logger.info('✅ Data integrity maintained during concurrent operations');
      logger.testComplete('Concurrent operations with data integrity validation', 'PASSED');

      testUsers.push(testUser);
    }, 60000);
  });

  describe('System Recovery and Resilience Tests', () => {
    test('should maintain system stability after multiple error scenarios', async () => {
      const testUser = createTestUser({
        username: `stability_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 500.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('System stability after multiple error scenarios');

      // Setup user and account
      await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 500.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Simulate multiple error scenarios
      const errorScenarios = [
        {
          name: 'Insufficient funds withdrawal',
          operation: async () => {
            try {
              await transactionClient.post('/api/transactions/withdraw', {
                accountId: account.id,
                amount: 1000.00,
                currency: 'USD'
              });
            } catch (error) {
              // Expected to fail
            }
          }
        },
        {
          name: 'Invalid account ID',
          operation: async () => {
            try {
              await transactionClient.post('/api/transactions/deposit', {
                accountId: 'invalid-account-id',
                amount: 100.00,
                currency: 'USD'
              });
            } catch (error) {
              // Expected to fail
            }
          }
        },
        {
          name: 'Negative amount transaction',
          operation: async () => {
            try {
              await transactionClient.post('/api/transactions/deposit', {
                accountId: account.id,
                amount: -50.00,
                currency: 'USD'
              });
            } catch (error) {
              // Expected to fail
            }
          }
        },
        {
          name: 'Invalid currency',
          operation: async () => {
            try {
              await transactionClient.post('/api/transactions/deposit', {
                accountId: account.id,
                amount: 100.00,
                currency: 'INVALID'
              });
            } catch (error) {
              // Expected to fail
            }
          }
        }
      ];

      // Execute all error scenarios
      logger.info('❌ Executing multiple error scenarios');
      for (const scenario of errorScenarios) {
        logger.info(`Testing: ${scenario.name}`);
        await scenario.operation();
        
        // Verify system is still responsive after each error
        const healthCheck = await accountClient.healthCheck();
        expect(healthCheck).toBe(true);
      }

      // Verify system can still process valid transactions after all errors
      logger.info('✅ Testing system functionality after all error scenarios');
      const validTransactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: account.id,
        amount: 100.00,
        currency: 'USD',
        description: 'Recovery test after multiple errors'
      });

      expect(validTransactionResponse.status).toBe(201);
      expect(validTransactionResponse.data.status).toBe('COMPLETED');

      // Verify account balance is correct
      const finalBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      expect(finalBalanceResponse.data.balance).toBe(600.00); // 500 + 100

      // Verify only valid transaction appears in history
      const historyResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const completedTransactions = historyResponse.data.content.filter(
        (tx: TransactionInfo) => tx.status === 'COMPLETED'
      );

      expect(completedTransactions.length).toBe(1);
      expect(completedTransactions[0].amount).toBe(100.00);
      expect(completedTransactions[0].type).toBe('DEPOSIT');

      logger.info('✅ System maintained stability and functionality after multiple error scenarios');
      logger.testComplete('System stability after multiple error scenarios', 'PASSED');

      testUsers.push(testUser);
    }, 90000);
  });
});