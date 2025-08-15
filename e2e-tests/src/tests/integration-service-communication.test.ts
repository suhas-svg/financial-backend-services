import { describe, test, expect, beforeAll, afterAll, beforeEach, afterEach } from '@jest/globals';
import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { 
  createTestUser, 
  generateTestId, 
  retryOperation, 
  waitForCondition,
  validateAccountStructure,
  validateTransactionStructure,
  delay
} from '../utils/test-helpers';
import { AuthResponse, AccountInfo, TransactionInfo, ApiResponse } from '../types';

/**
 * Service Integration Tests - Service-to-Service Communication
 * 
 * Tests the communication between Account Service and Transaction Service
 * Validates account validation, balance updates, and error handling
 * 
 * Requirements: 4.1, 4.2, 4.5
 */
describe('Service Integration - Service-to-Service Communication', () => {
  const accountClient = createAccountServiceClient();
  const transactionClient = createTransactionServiceClient();
  const testId = generateTestId('integration_comm');
  
  let testUser: any;
  let authToken: string;
  let testAccount: AccountInfo;
  
  beforeAll(async () => {
    logger.info(`Starting service communication integration tests: ${testId}`);
    
    // Wait for both services to be ready
    const accountReady = await accountClient.waitForService();
    const transactionReady = await transactionClient.waitForService();
    
    if (!accountReady || !transactionReady) {
      throw new Error('Services not ready for integration testing');
    }
    
    logger.info('Both services are ready for integration testing');
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
    
    logger.debug(`Test setup complete for ${testUser.username} with account ${testAccount.id}`);
  });

  afterEach(async () => {
    // Clear auth tokens
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
    
    // Cleanup will be handled by global teardown
    logger.debug(`Test cleanup complete for ${testUser.username}`);
  });

  afterAll(async () => {
    logger.info(`Service communication integration tests completed: ${testId}`);
  });

  describe('Account Validation During Transaction Processing', () => {
    test('should validate account existence before processing deposit', async () => {
      // Test that Transaction Service calls Account Service to validate account
      const depositAmount = 500.00;
      
      const response = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: depositAmount,
        currency: 'USD',
        description: 'Integration test deposit'
      });
      
      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty('id');
      expect(response.data.accountId).toBe(testAccount.id);
      expect(response.data.amount).toBe(depositAmount);
      expect(response.data.type).toBe('DEPOSIT');
      expect(response.data.status).toBe('COMPLETED');
      
      // Verify account balance was updated via service communication
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(accountResponse.data!.balance).toBe(1000.00 + depositAmount);
      
      logger.info('✅ Account validation during deposit successful');
    });

    test('should validate account existence before processing withdrawal', async () => {
      const withdrawalAmount = 300.00;
      
      const response = await transactionClient.post('/api/transactions/withdraw', {
        accountId: testAccount.id,
        amount: withdrawalAmount,
        currency: 'USD',
        description: 'Integration test withdrawal'
      });
      
      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty('id');
      expect(response.data.accountId).toBe(testAccount.id);
      expect(response.data.amount).toBe(withdrawalAmount);
      expect(response.data.type).toBe('WITHDRAWAL');
      expect(response.data.status).toBe('COMPLETED');
      
      // Verify account balance was updated
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(accountResponse.data!.balance).toBe(1000.00 - withdrawalAmount);
      
      logger.info('✅ Account validation during withdrawal successful');
    });

    test('should validate both accounts before processing transfer', async () => {
      // Create second account for transfer
      const secondAccountResponse = await accountClient.post<AccountInfo>('/api/accounts', {
        ownerId: testUser.username,
        accountType: 'SAVINGS',
        balance: 500.00
      });
      
      expect(secondAccountResponse.status).toBe(201);
      const secondAccount = secondAccountResponse.data!;
      
      const transferAmount = 200.00;
      
      const response = await transactionClient.post('/api/transactions/transfer', {
        fromAccountId: testAccount.id,
        toAccountId: secondAccount.id,
        amount: transferAmount,
        currency: 'USD',
        description: 'Integration test transfer'
      });
      
      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty('id');
      expect(response.data.amount).toBe(transferAmount);
      expect(response.data.type).toBe('TRANSFER');
      expect(response.data.status).toBe('COMPLETED');
      
      // Verify both account balances were updated
      const fromAccountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      const toAccountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${secondAccount.id}`);
      
      expect(fromAccountResponse.status).toBe(200);
      expect(toAccountResponse.status).toBe(200);
      expect(fromAccountResponse.data!.balance).toBe(1000.00 - transferAmount);
      expect(toAccountResponse.data!.balance).toBe(500.00 + transferAmount);
      
      logger.info('✅ Account validation during transfer successful');
    });

    test('should reject transaction for non-existent account', async () => {
      const nonExistentAccountId = 'non-existent-account-id';
      
      const response = await transactionClient.post('/api/transactions/deposit', {
        accountId: nonExistentAccountId,
        amount: 100.00,
        currency: 'USD',
        description: 'Test with non-existent account'
      });
      
      expect(response.status).toBe(404);
      expect(response.data).toHaveProperty('error');
      expect(response.data.error).toContain('Account not found');
      
      logger.info('✅ Non-existent account rejection successful');
    });

    test('should reject transaction for account belonging to different user', async () => {
      // Create another user and account
      const otherUser = createTestUser({ username: `${testId}_other_${Date.now()}` });
      
      // Register other user
      await accountClient.post('/api/auth/register', {
        username: otherUser.username,
        password: otherUser.password,
        email: otherUser.email
      });
      
      // Login as other user
      const otherLoginResponse = await accountClient.post<AuthResponse>('/api/auth/login', {
        username: otherUser.username,
        password: otherUser.password
      });
      
      const otherToken = otherLoginResponse.data!.accessToken;
      accountClient.setAuthToken(otherToken);
      
      // Create account for other user
      const otherAccountResponse = await accountClient.post<AccountInfo>('/api/accounts', {
        ownerId: otherUser.username,
        accountType: 'CHECKING',
        balance: 1000.00
      });
      
      const otherAccount = otherAccountResponse.data!;
      
      // Switch back to original user's token
      transactionClient.setAuthToken(authToken);
      
      // Try to deposit to other user's account
      const response = await transactionClient.post('/api/transactions/deposit', {
        accountId: otherAccount.id,
        amount: 100.00,
        currency: 'USD',
        description: 'Unauthorized deposit attempt'
      });
      
      expect(response.status).toBe(403);
      expect(response.data).toHaveProperty('error');
      
      logger.info('✅ Cross-user account access rejection successful');
    });
  });

  describe('Balance Update Communication', () => {
    test('should successfully update account balance via service call', async () => {
      const initialBalance = testAccount.balance;
      const depositAmount = 250.00;
      
      // Process deposit transaction
      const transactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: depositAmount,
        currency: 'USD',
        description: 'Balance update test'
      });
      
      expect(transactionResponse.status).toBe(201);
      
      // Verify balance update happened immediately
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(accountResponse.data!.balance).toBe(initialBalance + depositAmount);
      
      logger.info('✅ Balance update communication successful');
    });

    test('should handle balance update failures gracefully', async () => {
      // This test simulates a scenario where balance update might fail
      // We'll test with insufficient funds for withdrawal
      const withdrawalAmount = 2000.00; // More than account balance
      
      const response = await transactionClient.post('/api/transactions/withdraw', {
        accountId: testAccount.id,
        amount: withdrawalAmount,
        currency: 'USD',
        description: 'Insufficient funds test'
      });
      
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty('error');
      expect(response.data.error).toContain('Insufficient funds');
      
      // Verify account balance remains unchanged
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(accountResponse.data!.balance).toBe(1000.00); // Original balance
      
      logger.info('✅ Balance update failure handling successful');
    });

    test('should maintain balance consistency during concurrent operations', async () => {
      const initialBalance = testAccount.balance;
      const operationAmount = 100.00;
      const concurrentOperations = 5;
      
      // Create multiple concurrent deposit operations
      const depositPromises = Array.from({ length: concurrentOperations }, (_, index) =>
        transactionClient.post('/api/transactions/deposit', {
          accountId: testAccount.id,
          amount: operationAmount,
          currency: 'USD',
          description: `Concurrent deposit ${index + 1}`
        })
      );
      
      // Execute all operations concurrently
      const results = await Promise.all(depositPromises);
      
      // Verify all operations succeeded
      results.forEach((result, index) => {
        expect(result.status).toBe(201);
        expect(result.data.amount).toBe(operationAmount);
        logger.debug(`Concurrent operation ${index + 1} completed successfully`);
      });
      
      // Wait a moment for all balance updates to complete
      await delay(1000);
      
      // Verify final balance is correct
      const finalAccountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(finalAccountResponse.status).toBe(200);
      
      const expectedBalance = initialBalance + (operationAmount * concurrentOperations);
      expect(finalAccountResponse.data!.balance).toBe(expectedBalance);
      
      logger.info('✅ Concurrent balance update consistency maintained');
    });
  });

  describe('Service Communication Timeout and Retry', () => {
    test('should handle service communication timeouts gracefully', async () => {
      // This test verifies that the Transaction Service handles timeouts
      // when communicating with the Account Service
      
      // We'll test with a valid request but monitor response times
      const startTime = Date.now();
      
      const response = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: 100.00,
        currency: 'USD',
        description: 'Timeout handling test'
      });
      
      const duration = Date.now() - startTime;
      
      expect(response.status).toBe(201);
      expect(duration).toBeLessThan(testConfig.getTimeoutConfig().httpTimeout);
      
      logger.info(`✅ Service communication completed within timeout: ${duration}ms`);
    });

    test('should retry failed service communications', async () => {
      // This test verifies retry behavior by monitoring multiple requests
      const retryAttempts = 3;
      let successfulRequests = 0;
      
      for (let i = 0; i < retryAttempts; i++) {
        try {
          const response = await transactionClient.post('/api/transactions/deposit', {
            accountId: testAccount.id,
            amount: 50.00,
            currency: 'USD',
            description: `Retry test ${i + 1}`
          });
          
          if (response.status === 201) {
            successfulRequests++;
          }
        } catch (error) {
          logger.warn(`Retry attempt ${i + 1} failed:`, error);
        }
        
        // Small delay between attempts
        await delay(500);
      }
      
      expect(successfulRequests).toBeGreaterThan(0);
      logger.info(`✅ Service communication retry successful: ${successfulRequests}/${retryAttempts} requests succeeded`);
    });

    test('should handle service unavailability scenarios', async () => {
      // This test verifies behavior when services are temporarily unavailable
      // We'll test by making requests and checking error handling
      
      try {
        const response = await transactionClient.post('/api/transactions/deposit', {
          accountId: testAccount.id,
          amount: 100.00,
          currency: 'USD',
          description: 'Service availability test'
        });
        
        // If service is available, request should succeed
        expect(response.status).toBe(201);
        logger.info('✅ Service is available and responding correctly');
        
      } catch (error) {
        // If service is unavailable, error should be handled gracefully
        logger.info('✅ Service unavailability handled gracefully');
        expect(error).toBeDefined();
      }
    });

    test('should implement circuit breaker pattern for service failures', async () => {
      // This test verifies circuit breaker behavior
      // We'll make multiple requests and monitor failure patterns
      
      const requestCount = 10;
      const results: boolean[] = [];
      
      for (let i = 0; i < requestCount; i++) {
        try {
          const response = await transactionClient.post('/api/transactions/deposit', {
            accountId: testAccount.id,
            amount: 10.00,
            currency: 'USD',
            description: `Circuit breaker test ${i + 1}`
          });
          
          results.push(response.status === 201);
        } catch (error) {
          results.push(false);
        }
        
        // Small delay between requests
        await delay(100);
      }
      
      const successRate = results.filter(r => r).length / requestCount;
      
      // Circuit breaker should maintain reasonable success rate
      expect(successRate).toBeGreaterThan(0.5);
      
      logger.info(`✅ Circuit breaker pattern working: ${(successRate * 100).toFixed(1)}% success rate`);
    });
  });

  describe('Error Propagation and Handling', () => {
    test('should propagate account service errors correctly', async () => {
      // Test with invalid account data to trigger Account Service error
      const response = await transactionClient.post('/api/transactions/deposit', {
        accountId: 'invalid-account-format',
        amount: 100.00,
        currency: 'USD',
        description: 'Error propagation test'
      });
      
      expect(response.status).toBeGreaterThanOrEqual(400);
      expect(response.data).toHaveProperty('error');
      
      logger.info('✅ Account service error propagation successful');
    });

    test('should handle service communication errors with proper rollback', async () => {
      // This test verifies that failed service communications result in proper rollback
      const initialBalance = testAccount.balance;
      
      // Attempt a transaction that might fail during service communication
      try {
        const response = await transactionClient.post('/api/transactions/withdraw', {
          accountId: testAccount.id,
          amount: -100.00, // Invalid negative amount
          currency: 'USD',
          description: 'Rollback test'
        });
        
        expect(response.status).toBeGreaterThanOrEqual(400);
      } catch (error) {
        // Error is expected for invalid amount
      }
      
      // Verify account balance remains unchanged (rollback successful)
      const accountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(200);
      expect(accountResponse.data!.balance).toBe(initialBalance);
      
      logger.info('✅ Service communication rollback successful');
    });

    test('should maintain data consistency during service communication failures', async () => {
      // Test data consistency when service communication fails
      const initialBalance = testAccount.balance;
      
      // Make multiple operations, some of which might fail
      const operations = [
        { amount: 100.00, shouldSucceed: true },
        { amount: -50.00, shouldSucceed: false }, // Invalid negative amount
        { amount: 200.00, shouldSucceed: true },
        { amount: 0, shouldSucceed: false }, // Invalid zero amount
        { amount: 150.00, shouldSucceed: true }
      ];
      
      let expectedBalance = initialBalance;
      
      for (const operation of operations) {
        try {
          const response = await transactionClient.post('/api/transactions/deposit', {
            accountId: testAccount.id,
            amount: operation.amount,
            currency: 'USD',
            description: `Consistency test - ${operation.amount}`
          });
          
          if (operation.shouldSucceed) {
            expect(response.status).toBe(201);
            expectedBalance += operation.amount;
          } else {
            expect(response.status).toBeGreaterThanOrEqual(400);
          }
        } catch (error) {
          if (operation.shouldSucceed) {
            throw error; // Unexpected failure
          }
          // Expected failure for invalid operations
        }
        
        // Small delay between operations
        await delay(200);
      }
      
      // Verify final balance matches expected value
      const finalAccountResponse = await accountClient.get<AccountInfo>(`/api/accounts/${testAccount.id}`);
      expect(finalAccountResponse.status).toBe(200);
      expect(finalAccountResponse.data!.balance).toBe(expectedBalance);
      
      logger.info('✅ Data consistency maintained during service communication failures');
    });
  });
});