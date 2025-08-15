import { describe, test, expect, beforeAll, afterAll, beforeEach, afterEach } from '@jest/globals';
import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { 
  createTestUser, 
  generateTestId, 
  waitForCondition,
  compareAmounts,
  delay
} from '../utils/test-helpers';
import { AuthResponse, AccountInfo, TransactionInfo, ApiResponse } from '../types';

/**
 * Service Integration Tests - Data Consistency Validation
 * 
 * Tests data consistency between Account Service and Transaction Service
 * Validates account balance consistency, transaction state consistency, and concurrent operations
 * 
 * Requirements: 4.3
 */
describe('Service Integration - Data Consistency Validation', () => {
  const accountClient = createAccountServiceClient();
  const transactionClient = createTransactionServiceClient();
  const testId = generateTestId('integration_consistency');
  
  let testUser: any;
  let authToken: string;
  let testAccount: AccountInfo;
  
  beforeAll(async () => {
    logger.info(`Starting data consistency validation tests: ${testId}`);
    
    // Wait for both services to be ready
    const accountReady = await accountClient.waitForService();
    const transactionReady = await transactionClient.waitForService();
    
    if (!accountReady || !transactionReady) {
      throw new Error('Services not ready for data consistency testing');
    }
    
    logger.info('Both services are ready for data consistency testing');
  }, 60000);

  beforeEach(async () => {
    // Create fresh test user and account for each test
    testUser = createTestUser({ username: `${testId}_${Date.now()}` });
    
    // Register user
    const registerResponse = await accountClient.post('/api/auth/register', {
      username: testUser.username,
      password: testUser.password,
      email: testUser.email
    });
    
    expect(registerResponse.status).toBe(201);
    
    // Login to get token
    const loginResponse = await accountClient.post<AuthResponse>('/api/auth/login', {
      username: testUser.username,
      password: testUser.password
    });
    
    expect(loginResponse.status).toBe(200);
    authToken = loginResponse.data!.accessToken;
    
    // Set auth token for both clients
    accountClient.setAuthToken(authToken);
    transactionClient.setAuthToken(authToken);
    
    // Create test account
    const accountResponse = await accountClient.post<AccountInfo>('/api/accounts', {
      ownerId: testUser.username,
      accountType: 'CHECKING',
      balance: 1000.00
    });
    
    expect(accountResponse.status).toBe(201);
    testAccount = accountResponse.data!;
    
    logger.debug(`Data consistency test setup complete for ${testUser.username} with account ${testAccount.id}`);
  });

  afterEach(async () => {
    // Clear auth tokens
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
    
    logger.debug(`Data consistency test cleanup complete for ${testUser.username}`);
  });

  afterAll(async () => {
    logger.info(`Data consistency validation tests completed: ${testId}`);
  });

  describe('Account Balance Consistency Across Services', () => {
    test('should maintain balance consistency after single deposit', async () => {
      const initialBalance = testAccount.balance;
      const depositAmount = 250.00;
      
      // Process deposit via Transaction Service
      const transactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: depositAmount,
        currency: 'USD',
        description: 'Balance consistency test deposit'
      });
      
      expect(transactionResponse.status).toBe(201);
      expect(transactionResponse.data.status).toBe('COMPLETED');
      
      // Wait for balance update to propagate
      await delay(500);
      
      // Verify balance via Account Service
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      
      const expectedBalance = initialBalance + depositAmount;
      expect(compareAmounts(accountResponse.data!.balance, expectedBalance)).toBe(true);
      
      // Verify transaction history reflects the balance change
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      const transactions = transactionsResponse.data.content;
      const depositTransaction = transactions.find((t: any) => t.id === transactionResponse.data.id);
      expect(depositTransaction).toBeDefined();
      expect(depositTransaction.status).toBe('COMPLETED');
      
      logger.info('✅ Balance consistency maintained after single deposit');
    });

    test('should maintain balance consistency after single withdrawal', async () => {
      const initialBalance = testAccount.balance;
      const withdrawalAmount = 300.00;
      
      // Process withdrawal via Transaction Service
      const transactionResponse = await transactionClient.post('/api/transactions/withdraw', {
        accountId: testAccount.id,
        amount: withdrawalAmount,
        currency: 'USD',
        description: 'Balance consistency test withdrawal'
      });
      
      expect(transactionResponse.status).toBe(201);
      expect(transactionResponse.data.status).toBe('COMPLETED');
      
      // Wait for balance update to propagate
      await delay(500);
      
      // Verify balance via Account Service
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      
      const expectedBalance = initialBalance - withdrawalAmount;
      expect(compareAmounts(accountResponse.data!.balance, expectedBalance)).toBe(true);
      
      // Verify transaction history
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      const transactions = transactionsResponse.data.content;
      const withdrawalTransaction = transactions.find((t: any) => t.id === transactionResponse.data.id);
      expect(withdrawalTransaction).toBeDefined();
      expect(withdrawalTransaction.status).toBe('COMPLETED');
      
      logger.info('✅ Balance consistency maintained after single withdrawal');
    });

    test('should maintain balance consistency after transfer between accounts', async () => {
      // Create second account for transfer
      const secondAccountResponse = await accountClient.post<AccountInfo>('/api/accounts', {
        ownerId: testUser.username,
        accountType: 'SAVINGS',
        balance: 500.00
      });
      
      expect(secondAccountResponse.status).toBe(201);
      const secondAccount = secondAccountResponse.data!;
      
      const initialBalance1 = testAccount.balance;
      const initialBalance2 = secondAccount.balance;
      const transferAmount = 200.00;
      
      // Process transfer via Transaction Service
      const transactionResponse = await transactionClient.post('/api/transactions/transfer', {
        fromAccountId: testAccount.id,
        toAccountId: secondAccount.id,
        amount: transferAmount,
        currency: 'USD',
        description: 'Balance consistency test transfer'
      });
      
      expect(transactionResponse.status).toBe(201);
      expect(transactionResponse.data.status).toBe('COMPLETED');
      
      // Wait for balance updates to propagate
      await delay(1000);
      
      // Verify both account balances via Account Service
      const account1Response = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      const account2Response = await accountClient.get<AccountInfo>(`/api/accounts/${secondAccount.id}`);
      
      expect(account1Response.status).toBe(200);
      expect(account2Response.status).toBe(200);
      
      const expectedBalance1 = initialBalance1 - transferAmount;
      const expectedBalance2 = initialBalance2 + transferAmount;
      
      expect(compareAmounts(account1Response.data!.balance, expectedBalance1)).toBe(true);
      expect(compareAmounts(account2Response.data!.balance, expectedBalance2)).toBe(true);
      
      // Verify total balance is conserved
      const totalInitial = initialBalance1 + initialBalance2;
      const totalFinal = account1Response.data!.balance + account2Response.data!.balance;
      expect(compareAmounts(totalInitial, totalFinal)).toBe(true);
      
      logger.info('✅ Balance consistency maintained after transfer between accounts');
    });

    test('should maintain balance consistency with multiple sequential operations', async () => {
      const operations = [
        { type: 'deposit', amount: 100.00 },
        { type: 'withdrawal', amount: 50.00 },
        { type: 'deposit', amount: 200.00 },
        { type: 'withdrawal', amount: 75.00 },
        { type: 'deposit', amount: 150.00 }
      ];
      
      let expectedBalance = testAccount.balance;
      const transactionIds: string[] = [];
      
      // Execute operations sequentially
      for (const operation of operations) {
        const endpoint = operation.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
        
        const response = await transactionClient.post(endpoint, {
          accountId: testAccount.id,
          amount: operation.amount,
          currency: 'USD',
          description: `Sequential ${operation.type} test`
        });
        
        expect(response.status).toBe(201);
        expect(response.data.status).toBe('COMPLETED');
        transactionIds.push(response.data.id);
        
        // Update expected balance
        if (operation.type === 'deposit') {
          expectedBalance += operation.amount;
        } else {
          expectedBalance -= operation.amount;
        }
        
        // Wait for balance update
        await delay(300);
        
        // Verify balance after each operation
        const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
        expect(accountResponse.status).toBe(200);
        expect(compareAmounts(accountResponse.data!.balance, expectedBalance)).toBe(true);
        
        logger.debug(`✅ Balance consistent after ${operation.type} of ${operation.amount}: ${accountResponse.data!.balance}`);
      }
      
      // Verify all transactions are recorded
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      const transactions = transactionsResponse.data.content;
      transactionIds.forEach(id => {
        const transaction = transactions.find((t: any) => t.id === id);
        expect(transaction).toBeDefined();
        expect(transaction.status).toBe('COMPLETED');
      });
      
      logger.info('✅ Balance consistency maintained through multiple sequential operations');
    });

    test('should detect and handle balance inconsistencies', async () => {
      // This test verifies that the system can detect balance inconsistencies
      // We'll create a scenario and then verify consistency
      
      const initialBalance = testAccount.balance;
      
      // Perform a transaction
      const depositAmount = 100.00;
      const transactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: depositAmount,
        currency: 'USD',
        description: 'Consistency detection test'
      });
      
      expect(transactionResponse.status).toBe(201);
      
      // Wait for processing
      await delay(500);
      
      // Get account balance from Account Service
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      
      // Get transaction history from Transaction Service
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      // Calculate expected balance from transaction history
      const transactions = transactionsResponse.data.content;
      let calculatedBalance = initialBalance;
      
      transactions.forEach((transaction: any) => {
        if (transaction.status === 'COMPLETED') {
          if (transaction.type === 'DEPOSIT') {
            calculatedBalance += transaction.amount;
          } else if (transaction.type === 'WITHDRAWAL') {
            calculatedBalance -= transaction.amount;
          }
        }
      });
      
      // Verify balance consistency
      const actualBalance = accountResponse.data!.balance;
      expect(compareAmounts(actualBalance, calculatedBalance)).toBe(true);
      
      logger.info(`✅ Balance consistency verified: Account=${actualBalance}, Calculated=${calculatedBalance}`);
    });
  });

  describe('Transaction State Consistency with Rollback Scenarios', () => {
    test('should rollback transaction state on insufficient funds', async () => {
      const initialBalance = testAccount.balance;
      const excessiveAmount = initialBalance + 500.00; // More than available
      
      // Attempt withdrawal with insufficient funds
      const response = await transactionClient.post('/api/transactions/withdraw', {
        accountId: testAccount.id,
        amount: excessiveAmount,
        currency: 'USD',
        description: 'Insufficient funds rollback test'
      });
      
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty('error');
      expect(response.data.error).toContain('Insufficient funds');
      
      // Verify account balance remains unchanged
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(compareAmounts(accountResponse.data!.balance, initialBalance)).toBe(true);
      
      // Verify no transaction was created
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      const failedTransaction = transactionsResponse.data.content.find(
        (t: any) => t.amount === excessiveAmount && t.type === 'WITHDRAWAL'
      );
      expect(failedTransaction).toBeUndefined();
      
      logger.info('✅ Transaction state rollback successful for insufficient funds');
    });

    test('should rollback transaction state on invalid account', async () => {
      const invalidAccountId = 'invalid-account-id';
      const amount = 100.00;
      
      // Attempt transaction with invalid account
      const response = await transactionClient.post('/api/transactions/deposit', {
        accountId: invalidAccountId,
        amount: amount,
        currency: 'USD',
        description: 'Invalid account rollback test'
      });
      
      expect(response.status).toBe(404);
      expect(response.data).toHaveProperty('error');
      
      // Verify no transaction was created for any account
      const transactionsResponse = await transactionClient.get('/api/transactions');
      expect(transactionsResponse.status).toBe(200);
      
      const invalidTransaction = transactionsResponse.data.content.find(
        (t: any) => t.accountId === invalidAccountId
      );
      expect(invalidTransaction).toBeUndefined();
      
      logger.info('✅ Transaction state rollback successful for invalid account');
    });

    test('should rollback transfer state when target account is invalid', async () => {
      const initialBalance = testAccount.balance;
      const invalidTargetId = 'invalid-target-account';
      const transferAmount = 100.00;
      
      // Attempt transfer to invalid account
      const response = await transactionClient.post('/api/transactions/transfer', {
        fromAccountId: testAccount.id,
        toAccountId: invalidTargetId,
        amount: transferAmount,
        currency: 'USD',
        description: 'Invalid target rollback test'
      });
      
      expect(response.status).toBe(404);
      expect(response.data).toHaveProperty('error');
      
      // Verify source account balance remains unchanged
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(compareAmounts(accountResponse.data!.balance, initialBalance)).toBe(true);
      
      // Verify no transfer transaction was created
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      const failedTransfer = transactionsResponse.data.content.find(
        (t: any) => t.type === 'TRANSFER' && t.amount === transferAmount
      );
      expect(failedTransfer).toBeUndefined();
      
      logger.info('✅ Transfer state rollback successful for invalid target account');
    });

    test('should maintain transaction atomicity during system errors', async () => {
      // This test verifies that transactions are atomic even during potential system errors
      const initialBalance = testAccount.balance;
      
      // Create multiple transactions in quick succession
      const transactions = [
        { amount: 100.00, type: 'deposit' },
        { amount: 50.00, type: 'withdrawal' },
        { amount: 200.00, type: 'deposit' }
      ];
      
      const results = [];
      
      for (const transaction of transactions) {
        const endpoint = transaction.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
        
        try {
          const response = await transactionClient.post(endpoint, {
            accountId: testAccount.id,
            amount: transaction.amount,
            currency: 'USD',
            description: `Atomicity test ${transaction.type}`
          });
          
          results.push({
            success: response.status === 201,
            amount: transaction.amount,
            type: transaction.type,
            id: response.data?.id
          });
        } catch (error) {
          results.push({
            success: false,
            amount: transaction.amount,
            type: transaction.type,
            error: error
          });
        }
        
        // Small delay between transactions
        await delay(100);
      }
      
      // Wait for all processing to complete
      await delay(1000);
      
      // Calculate expected balance based on successful transactions
      let expectedBalance = initialBalance;
      results.forEach(result => {
        if (result.success) {
          if (result.type === 'deposit') {
            expectedBalance += result.amount;
          } else {
            expectedBalance -= result.amount;
          }
        }
      });
      
      // Verify final balance matches expected
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(compareAmounts(accountResponse.data!.balance, expectedBalance)).toBe(true);
      
      // Verify transaction history matches successful operations
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      const successfulIds = results.filter(r => r.success).map(r => r.id);
      const recordedTransactions = transactionsResponse.data.content;
      
      successfulIds.forEach(id => {
        const transaction = recordedTransactions.find((t: any) => t.id === id);
        expect(transaction).toBeDefined();
        expect(transaction.status).toBe('COMPLETED');
      });
      
      logger.info('✅ Transaction atomicity maintained during system operations');
    });
  });

  describe('Concurrent Operation Consistency with Race Condition Handling', () => {
    test('should handle concurrent deposits without race conditions', async () => {
      const initialBalance = testAccount.balance;
      const concurrentDeposits = 5;
      const depositAmount = 100.00;
      
      // Create concurrent deposit operations
      const depositPromises = Array.from({ length: concurrentDeposits }, (_, index) =>
        transactionClient.post('/api/transactions/deposit', {
          accountId: testAccount.id,
          amount: depositAmount,
          currency: 'USD',
          description: `Concurrent deposit ${index + 1}`
        })
      );
      
      // Execute all deposits concurrently
      const results = await Promise.all(depositPromises);
      
      // Verify all deposits succeeded
      results.forEach((result, index) => {
        expect(result.status).toBe(201);
        expect(result.data.status).toBe('COMPLETED');
        expect(result.data.amount).toBe(depositAmount);
        logger.debug(`Concurrent deposit ${index + 1} completed: ${result.data.id}`);
      });
      
      // Wait for all balance updates to complete
      await delay(2000);
      
      // Verify final balance is correct
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      
      const expectedBalance = initialBalance + (depositAmount * concurrentDeposits);
      expect(compareAmounts(accountResponse.data!.balance, expectedBalance)).toBe(true);
      
      // Verify all transactions are recorded
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      const transactionIds = results.map(r => r.data.id);
      const recordedTransactions = transactionsResponse.data.content;
      
      transactionIds.forEach(id => {
        const transaction = recordedTransactions.find((t: any) => t.id === id);
        expect(transaction).toBeDefined();
        expect(transaction.status).toBe('COMPLETED');
      });
      
      logger.info('✅ Concurrent deposits handled without race conditions');
    });

    test('should handle concurrent withdrawals with proper balance validation', async () => {
      const initialBalance = testAccount.balance;
      const concurrentWithdrawals = 3;
      const withdrawalAmount = 200.00; // Total would be 600, but account has 1000
      
      // Create concurrent withdrawal operations
      const withdrawalPromises = Array.from({ length: concurrentWithdrawals }, (_, index) =>
        transactionClient.post('/api/transactions/withdraw', {
          accountId: testAccount.id,
          amount: withdrawalAmount,
          currency: 'USD',
          description: `Concurrent withdrawal ${index + 1}`
        })
      );
      
      // Execute all withdrawals concurrently
      const results = await Promise.all(withdrawalPromises);
      
      // Count successful withdrawals
      const successfulWithdrawals = results.filter(r => r.status === 201);
      const failedWithdrawals = results.filter(r => r.status !== 201);
      
      logger.info(`Concurrent withdrawals: ${successfulWithdrawals.length} successful, ${failedWithdrawals.length} failed`);
      
      // At least some withdrawals should succeed
      expect(successfulWithdrawals.length).toBeGreaterThan(0);
      
      // Wait for all processing to complete
      await delay(2000);
      
      // Verify final balance is correct
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      
      const expectedBalance = initialBalance - (withdrawalAmount * successfulWithdrawals.length);
      expect(compareAmounts(accountResponse.data!.balance, expectedBalance)).toBe(true);
      expect(accountResponse.data!.balance).toBeGreaterThanOrEqual(0); // No negative balance
      
      logger.info('✅ Concurrent withdrawals handled with proper balance validation');
    });

    test('should handle concurrent mixed operations consistently', async () => {
      const initialBalance = testAccount.balance;
      
      // Create mixed concurrent operations
      const operations = [
        { type: 'deposit', amount: 150.00 },
        { type: 'withdrawal', amount: 100.00 },
        { type: 'deposit', amount: 200.00 },
        { type: 'withdrawal', amount: 75.00 },
        { type: 'deposit', amount: 125.00 }
      ];
      
      const operationPromises = operations.map((operation, index) => {
        const endpoint = operation.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
        return transactionClient.post(endpoint, {
          accountId: testAccount.id,
          amount: operation.amount,
          currency: 'USD',
          description: `Concurrent ${operation.type} ${index + 1}`
        });
      });
      
      // Execute all operations concurrently
      const results = await Promise.all(operationPromises);
      
      // Analyze results
      const successfulResults = results.filter(r => r.status === 201);
      const failedResults = results.filter(r => r.status !== 201);
      
      logger.info(`Mixed operations: ${successfulResults.length} successful, ${failedResults.length} failed`);
      
      // Calculate expected balance from successful operations
      let expectedBalance = initialBalance;
      successfulResults.forEach((result, index) => {
        const operation = operations[results.indexOf(result)];
        if (operation.type === 'deposit') {
          expectedBalance += operation.amount;
        } else {
          expectedBalance -= operation.amount;
        }
      });
      
      // Wait for all processing to complete
      await delay(2000);
      
      // Verify final balance
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(compareAmounts(accountResponse.data!.balance, expectedBalance)).toBe(true);
      
      // Verify all successful transactions are recorded
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      const successfulIds = successfulResults.map(r => r.data.id);
      const recordedTransactions = transactionsResponse.data.content;
      
      successfulIds.forEach(id => {
        const transaction = recordedTransactions.find((t: any) => t.id === id);
        expect(transaction).toBeDefined();
        expect(transaction.status).toBe('COMPLETED');
      });
      
      logger.info('✅ Concurrent mixed operations handled consistently');
    });

    test('should prevent race conditions in balance updates', async () => {
      // This test specifically targets race conditions in balance updates
      const initialBalance = testAccount.balance;
      const operationCount = 10;
      const operationAmount = 50.00;
      
      // Create rapid-fire operations
      const promises = [];
      for (let i = 0; i < operationCount; i++) {
        const isDeposit = i % 2 === 0;
        const endpoint = isDeposit ? '/api/transactions/deposit' : '/api/transactions/withdraw';
        
        promises.push(
          transactionClient.post(endpoint, {
            accountId: testAccount.id,
            amount: operationAmount,
            currency: 'USD',
            description: `Race condition test ${i + 1} - ${isDeposit ? 'deposit' : 'withdrawal'}`
          })
        );
      }
      
      // Execute all operations simultaneously
      const results = await Promise.all(promises);
      
      // Count successful operations by type
      let successfulDeposits = 0;
      let successfulWithdrawals = 0;
      
      results.forEach((result, index) => {
        if (result.status === 201) {
          const isDeposit = index % 2 === 0;
          if (isDeposit) {
            successfulDeposits++;
          } else {
            successfulWithdrawals++;
          }
        }
      });
      
      // Wait for all processing to complete
      await delay(3000);
      
      // Calculate expected balance
      const expectedBalance = initialBalance + 
        (successfulDeposits * operationAmount) - 
        (successfulWithdrawals * operationAmount);
      
      // Verify final balance
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(compareAmounts(accountResponse.data!.balance, expectedBalance)).toBe(true);
      
      // Verify balance is non-negative
      expect(accountResponse.data!.balance).toBeGreaterThanOrEqual(0);
      
      logger.info(`✅ Race conditions prevented: ${successfulDeposits} deposits, ${successfulWithdrawals} withdrawals, final balance: ${accountResponse.data!.balance}`);
    });

    test('should maintain consistency during high-frequency operations', async () => {
      // Test system behavior under high-frequency operations
      const initialBalance = testAccount.balance;
      const batchSize = 20;
      const batchCount = 3;
      const operationAmount = 25.00;
      
      for (let batch = 0; batch < batchCount; batch++) {
        logger.info(`Executing batch ${batch + 1}/${batchCount}`);
        
        // Create batch of operations
        const batchPromises = [];
        for (let i = 0; i < batchSize; i++) {
          const isDeposit = Math.random() > 0.5;
          const endpoint = isDeposit ? '/api/transactions/deposit' : '/api/transactions/withdraw';
          
          batchPromises.push(
            transactionClient.post(endpoint, {
              accountId: testAccount.id,
              amount: operationAmount,
              currency: 'USD',
              description: `High-frequency batch ${batch + 1} operation ${i + 1}`
            })
          );
        }
        
        // Execute batch
        const batchResults = await Promise.all(batchPromises);
        
        // Log batch results
        const successful = batchResults.filter(r => r.status === 201).length;
        logger.debug(`Batch ${batch + 1}: ${successful}/${batchSize} operations successful`);
        
        // Wait between batches
        await delay(1000);
      }
      
      // Wait for all processing to complete
      await delay(5000);
      
      // Verify final state consistency
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      
      // Get all transactions for this account
      const transactionsResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
      expect(transactionsResponse.status).toBe(200);
      
      // Calculate balance from transaction history
      const transactions = transactionsResponse.data.content;
      let calculatedBalance = initialBalance;
      
      transactions.forEach((transaction: any) => {
        if (transaction.status === 'COMPLETED') {
          if (transaction.type === 'DEPOSIT') {
            calculatedBalance += transaction.amount;
          } else if (transaction.type === 'WITHDRAWAL') {
            calculatedBalance -= transaction.amount;
          }
        }
      });
      
      // Verify consistency
      expect(compareAmounts(accountResponse.data!.balance, calculatedBalance)).toBe(true);
      expect(accountResponse.data!.balance).toBeGreaterThanOrEqual(0);
      
      logger.info(`✅ High-frequency operations maintained consistency: Account=${accountResponse.data!.balance}, Calculated=${calculatedBalance}`);
    });
  });
});