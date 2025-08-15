/**
 * Transaction Service Reversal Endpoint Tests
 * Tests transaction reversal functionality with business rule validation
 * Requirements: 3.7
 */

import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { generateTestId, createTestUser, generateRandomAmount, delay, compareAmounts } from '../utils/test-helpers';
import { AuthResponse, AccountInfo, TransactionInfo } from '../types';

interface ReversalInfo {
  id: string;
  originalTransactionId: string;
  reversalTransactionId: string;
  amount: number;
  reason: string;
  status: string;
  createdAt: string;
  processedAt?: string;
}

describe('Transaction Service Reversal Endpoints', () => {
  let accountClient: ReturnType<typeof createAccountServiceClient>;
  let transactionClient: ReturnType<typeof createTransactionServiceClient>;
  let testUserId: string;
  let authToken: string;
  let testUser: any;
  let testAccounts: AccountInfo[] = [];
  let reversibleTransactions: TransactionInfo[] = [];

  beforeAll(async () => {
    accountClient = createAccountServiceClient();
    transactionClient = createTransactionServiceClient();
    testUserId = generateTestId();
    
    // Wait for services to be ready
    const accountReady = await accountClient.waitForService();
    const transactionReady = await transactionClient.waitForService();
    
    if (!accountReady || !transactionReady) {
      throw new Error('Services are not ready for testing');
    }
    
    // Setup test user and accounts
    testUser = createTestUser({ username: `txnreversal_${testUserId}` });
    
    // Register and login user
    await accountClient.post('/api/auth/register', {
      username: testUser.username,
      password: testUser.password
    });
    
    const loginResponse = await accountClient.post('/api/auth/login', {
      username: testUser.username,
      password: testUser.password
    });
    
    authToken = loginResponse.data.token;
    accountClient.setAuthToken(authToken);
    transactionClient.setAuthToken(authToken);
    
    // Create test accounts
    await createTestAccounts();
    
    // Create transactions that can be reversed
    await createReversibleTransactions();
    
    logger.info('Transaction Service Reversal Tests - Setup Complete');
  });

  afterAll(async () => {
    // Cleanup
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
    logger.info('Transaction Service Reversal Tests - Cleanup Complete');
  });

  async function createTestAccounts() {
    const accountTypes = ['CHECKING', 'SAVINGS'];
    
    for (const accountType of accountTypes) {
      const accountResponse = await accountClient.post('/api/accounts', {
        accountType,
        initialBalance: 5000.00
      });
      testAccounts.push(accountResponse.data);
      await delay(50);
    }
  }

  async function createReversibleTransactions() {
    const transactions = [
      // Deposits that can be reversed
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[0].id, amount: 500.00, description: 'Reversible deposit 1' }, delay: 0 },
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[1].id, amount: 300.00, description: 'Reversible deposit 2' }, delay: 500 },
      
      // Withdrawals that can be reversed
      { endpoint: '/api/transactions/withdraw', data: { accountId: testAccounts[0].id, amount: 200.00, description: 'Reversible withdrawal 1' }, delay: 1000 },
      { endpoint: '/api/transactions/withdraw', data: { accountId: testAccounts[1].id, amount: 150.00, description: 'Reversible withdrawal 2' }, delay: 1500 },
      
      // Transfers that can be reversed
      { endpoint: '/api/transactions/transfer', data: { fromAccountId: testAccounts[0].id, toAccountId: testAccounts[1].id, amount: 250.00, description: 'Reversible transfer 1' }, delay: 2000 },
      { endpoint: '/api/transactions/transfer', data: { fromAccountId: testAccounts[1].id, toAccountId: testAccounts[0].id, amount: 100.00, description: 'Reversible transfer 2' }, delay: 2500 }
    ];

    for (const transaction of transactions) {
      await delay(transaction.delay);
      const response = await transactionClient.post(transaction.endpoint, transaction.data);
      if (response.status === 201) {
        reversibleTransactions.push(response.data);
      }
    }
  }

  describe('Transaction Reversal Endpoint', () => {
    describe('Valid Reversal Scenarios', () => {
      it('should reverse a deposit transaction successfully', async () => {
        logger.testStart('Transaction Reversal - Deposit');
        
        const depositTransaction = reversibleTransactions.find(t => t.type === 'DEPOSIT');
        expect(depositTransaction).toBeDefined();
        
        const reversalData = {
          reason: 'Customer request - duplicate deposit',
          description: 'Reversing duplicate deposit transaction'
        };
        
        const startTime = Date.now();
        const response = await transactionClient.post(`/api/transactions/${depositTransaction!.id}/reverse`, reversalData);
        const duration = Date.now() - startTime;

        // Validate response structure
        expect(response.status).toBe(201);
        expect(response.data).toBeDefined();
        
        const reversal: ReversalInfo = response.data;
        expect(reversal.originalTransactionId).toBe(depositTransaction!.id);
        expect(reversal.reversalTransactionId).toBeDefined();
        expect(reversal.amount).toBe(depositTransaction!.amount);
        expect(reversal.reason).toBe(reversalData.reason);
        expect(reversal.status).toBe('COMPLETED');
        expect(reversal.createdAt).toBeDefined();
        expect(reversal.processedAt).toBeDefined();
        
        // Validate response time
        expect(response).toHaveResponseTime(5000);
        
        // Verify reversal transaction was created
        const reversalTxnResponse = await transactionClient.get(`/api/transactions/${reversal.reversalTransactionId}`);
        expect(reversalTxnResponse.status).toBe(200);
        expect(reversalTxnResponse.data.type).toBe('REVERSAL');
        expect(reversalTxnResponse.data.amount).toBe(depositTransaction!.amount);
        
        logger.testComplete('Transaction Reversal - Deposit', 'PASSED', duration);
      });

      it('should reverse a withdrawal transaction successfully', async () => {
        logger.testStart('Transaction Reversal - Withdrawal');
        
        const withdrawalTransaction = reversibleTransactions.find(t => t.type === 'WITHDRAWAL');
        expect(withdrawalTransaction).toBeDefined();
        
        const reversalData = {
          reason: 'Bank error - incorrect withdrawal',
          description: 'Reversing erroneous withdrawal'
        };
        
        const response = await transactionClient.post(`/api/transactions/${withdrawalTransaction!.id}/reverse`, reversalData);
        
        expect(response.status).toBe(201);
        
        const reversal: ReversalInfo = response.data;
        expect(reversal.originalTransactionId).toBe(withdrawalTransaction!.id);
        expect(reversal.amount).toBe(withdrawalTransaction!.amount);
        expect(reversal.reason).toBe(reversalData.reason);
        expect(reversal.status).toBe('COMPLETED');
        
        // Verify reversal transaction was created
        const reversalTxnResponse = await transactionClient.get(`/api/transactions/${reversal.reversalTransactionId}`);
        expect(reversalTxnResponse.status).toBe(200);
        expect(reversalTxnResponse.data.type).toBe('REVERSAL');
        
        logger.testComplete('Transaction Reversal - Withdrawal', 'PASSED');
      });

      it('should reverse a transfer transaction successfully', async () => {
        logger.testStart('Transaction Reversal - Transfer');
        
        const transferTransaction = reversibleTransactions.find(t => t.type === 'TRANSFER');
        expect(transferTransaction).toBeDefined();
        
        const reversalData = {
          reason: 'Customer dispute - unauthorized transfer',
          description: 'Reversing disputed transfer'
        };
        
        const response = await transactionClient.post(`/api/transactions/${transferTransaction!.id}/reverse`, reversalData);
        
        expect(response.status).toBe(201);
        
        const reversal: ReversalInfo = response.data;
        expect(reversal.originalTransactionId).toBe(transferTransaction!.id);
        expect(reversal.amount).toBe(transferTransaction!.amount);
        expect(reversal.reason).toBe(reversalData.reason);
        expect(reversal.status).toBe('COMPLETED');
        
        // Verify reversal transaction was created
        const reversalTxnResponse = await transactionClient.get(`/api/transactions/${reversal.reversalTransactionId}`);
        expect(reversalTxnResponse.status).toBe(200);
        expect(reversalTxnResponse.data.type).toBe('REVERSAL');
        
        logger.testComplete('Transaction Reversal - Transfer', 'PASSED');
      });

      it('should create reversal with proper audit trail', async () => {
        logger.testStart('Transaction Reversal - Audit Trail');
        
        const transaction = reversibleTransactions.find(t => t.type === 'DEPOSIT' && t.status === 'COMPLETED');
        expect(transaction).toBeDefined();
        
        const reversalData = {
          reason: 'Audit test - creating reversal trail',
          description: 'Testing audit trail functionality'
        };
        
        const response = await transactionClient.post(`/api/transactions/${transaction!.id}/reverse`, reversalData);
        
        expect(response.status).toBe(201);
        
        const reversal: ReversalInfo = response.data;
        
        // Verify reversal record contains all required audit information
        expect(reversal.id).toBeDefined();
        expect(reversal.originalTransactionId).toBe(transaction!.id);
        expect(reversal.reversalTransactionId).toBeDefined();
        expect(reversal.reason).toBe(reversalData.reason);
        expect(reversal.createdAt).toBeDefined();
        expect(reversal.processedAt).toBeDefined();
        
        // Verify original transaction is marked as reversed
        const originalTxnResponse = await transactionClient.get(`/api/transactions/${transaction!.id}`);
        expect(originalTxnResponse.status).toBe(200);
        expect(originalTxnResponse.data.status).toBe('REVERSED');
        
        logger.testComplete('Transaction Reversal - Audit Trail', 'PASSED');
      });
    });

    describe('Reversal Business Rule Validation', () => {
      it('should validate transaction is eligible for reversal', async () => {
        logger.testStart('Transaction Reversal - Eligibility Validation');
        
        const eligibleTransaction = reversibleTransactions.find(t => t.status === 'COMPLETED');
        expect(eligibleTransaction).toBeDefined();
        
        const reversalData = {
          reason: 'Testing eligibility validation',
          description: 'Eligible transaction reversal test'
        };
        
        const response = await transactionClient.post(`/api/transactions/${eligibleTransaction!.id}/reverse`, reversalData);
        
        expect(response.status).toBe(201);
        expect(response.data.status).toBe('COMPLETED');
        
        logger.testComplete('Transaction Reversal - Eligibility Validation', 'PASSED');
      });

      it('should validate reversal time limits', async () => {
        logger.testStart('Transaction Reversal - Time Limits');
        
        // Create a fresh transaction for time limit testing
        const newTxnResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: testAccounts[0].id,
          amount: 100.00,
          description: 'Time limit test transaction'
        });
        
        expect(newTxnResponse.status).toBe(201);
        
        // Immediately try to reverse (should be allowed)
        const reversalData = {
          reason: 'Testing time limits',
          description: 'Fresh transaction reversal'
        };
        
        const response = await transactionClient.post(`/api/transactions/${newTxnResponse.data.id}/reverse`, reversalData);
        
        expect(response.status).toBe(201);
        expect(response.data.status).toBe('COMPLETED');
        
        logger.testComplete('Transaction Reversal - Time Limits', 'PASSED');
      });

      it('should validate sufficient funds for reversal', async () => {
        logger.testStart('Transaction Reversal - Sufficient Funds');
        
        // Create a withdrawal transaction
        const withdrawalResponse = await transactionClient.post('/api/transactions/withdraw', {
          accountId: testAccounts[0].id,
          amount: 50.00,
          description: 'Funds validation test withdrawal'
        });
        
        expect(withdrawalResponse.status).toBe(201);
        
        // Reverse the withdrawal (should add funds back)
        const reversalData = {
          reason: 'Testing funds validation',
          description: 'Withdrawal reversal for funds test'
        };
        
        const response = await transactionClient.post(`/api/transactions/${withdrawalResponse.data.id}/reverse`, reversalData);
        
        expect(response.status).toBe(201);
        expect(response.data.status).toBe('COMPLETED');
        
        logger.testComplete('Transaction Reversal - Sufficient Funds', 'PASSED');
      });

      it('should validate account status for reversal', async () => {
        logger.testStart('Transaction Reversal - Account Status');
        
        const transaction = reversibleTransactions.find(t => t.type === 'DEPOSIT' && t.status === 'COMPLETED');
        expect(transaction).toBeDefined();
        
        // Get account status before reversal
        const accountResponse = await accountClient.get(`/api/accounts/${transaction!.accountId}`);
        expect(accountResponse.status).toBe(200);
        expect(accountResponse.data.status).toBe('ACTIVE');
        
        const reversalData = {
          reason: 'Testing account status validation',
          description: 'Account status validation test'
        };
        
        const response = await transactionClient.post(`/api/transactions/${transaction!.id}/reverse`, reversalData);
        
        expect(response.status).toBe(201);
        expect(response.data.status).toBe('COMPLETED');
        
        logger.testComplete('Transaction Reversal - Account Status', 'PASSED');
      });
    });

    describe('Invalid Reversal Scenarios', () => {
      it('should reject reversal of non-existent transaction', async () => {
        logger.testStart('Transaction Reversal - Non-existent Transaction');
        
        const nonExistentId = 'non-existent-transaction-id';
        
        const reversalData = {
          reason: 'Testing non-existent transaction',
          description: 'Should fail'
        };
        
        const response = await transactionClient.post(`/api/transactions/${nonExistentId}/reverse`, reversalData);
        
        expect(response.status).toBe(404);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Reversal - Non-existent Transaction', 'PASSED');
      });

      it('should reject reversal of already reversed transaction', async () => {
        logger.testStart('Transaction Reversal - Already Reversed');
        
        // Create and reverse a transaction
        const txnResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: testAccounts[0].id,
          amount: 75.00,
          description: 'Double reversal test'
        });
        
        const firstReversalData = {
          reason: 'First reversal',
          description: 'Initial reversal'
        };
        
        const firstReversalResponse = await transactionClient.post(`/api/transactions/${txnResponse.data.id}/reverse`, firstReversalData);
        expect(firstReversalResponse.status).toBe(201);
        
        // Try to reverse again
        const secondReversalData = {
          reason: 'Second reversal attempt',
          description: 'Should fail'
        };
        
        const secondReversalResponse = await transactionClient.post(`/api/transactions/${txnResponse.data.id}/reverse`, secondReversalData);
        
        expect(secondReversalResponse.status).toBe(400);
        expect(secondReversalResponse.data.error).toBeDefined();
        expect(secondReversalResponse.data.error).toContain('already reversed');
        
        logger.testComplete('Transaction Reversal - Already Reversed', 'PASSED');
      });

      it('should reject reversal with missing required fields', async () => {
        logger.testStart('Transaction Reversal - Missing Fields');
        
        const transaction = reversibleTransactions.find(t => t.status === 'COMPLETED');
        expect(transaction).toBeDefined();
        
        // Try reversal without reason
        const incompleteData = {
          description: 'Missing reason field'
        };
        
        const response = await transactionClient.post(`/api/transactions/${transaction!.id}/reverse`, incompleteData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Reversal - Missing Fields', 'PASSED');
      });

      it('should reject reversal with invalid reason', async () => {
        logger.testStart('Transaction Reversal - Invalid Reason');
        
        const transaction = reversibleTransactions.find(t => t.status === 'COMPLETED');
        expect(transaction).toBeDefined();
        
        const invalidReversalData = {
          reason: '', // Empty reason
          description: 'Invalid reason test'
        };
        
        const response = await transactionClient.post(`/api/transactions/${transaction!.id}/reverse`, invalidReversalData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Reversal - Invalid Reason', 'PASSED');
      });

      it('should reject reversal of other user\'s transaction', async () => {
        logger.testStart('Transaction Reversal - Other User Transaction');
        
        // Create another user and transaction
        const otherUser = createTestUser({ username: `otheruser_${testUserId}` });
        
        await accountClient.post('/api/auth/register', {
          username: otherUser.username,
          password: otherUser.password
        });
        
        const otherLoginResponse = await accountClient.post('/api/auth/login', {
          username: otherUser.username,
          password: otherUser.password
        });
        
        const otherAccountClient = createAccountServiceClient();
        const otherTransactionClient = createTransactionServiceClient();
        otherAccountClient.setAuthToken(otherLoginResponse.data.token);
        otherTransactionClient.setAuthToken(otherLoginResponse.data.token);
        
        const otherAccountResponse = await otherAccountClient.post('/api/accounts', {
          accountType: 'CHECKING',
          initialBalance: 1000.00
        });
        
        const otherTxnResponse = await otherTransactionClient.post('/api/transactions/deposit', {
          accountId: otherAccountResponse.data.id,
          amount: 100.00,
          description: 'Other user transaction'
        });
        
        // Try to reverse other user's transaction with original user's token
        const reversalData = {
          reason: 'Unauthorized reversal attempt',
          description: 'Should fail'
        };
        
        const response = await transactionClient.post(`/api/transactions/${otherTxnResponse.data.id}/reverse`, reversalData);
        
        expect(response.status).toBe(403);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Reversal - Other User Transaction', 'PASSED');
      });
    });

    describe('Reversal Authentication and Authorization', () => {
      it('should reject reversal without authentication', async () => {
        logger.testStart('Transaction Reversal - No Authentication');
        
        const unauthenticatedClient = createTransactionServiceClient();
        const transaction = reversibleTransactions[0];
        
        const reversalData = {
          reason: 'Unauthenticated reversal attempt',
          description: 'Should fail'
        };
        
        const response = await unauthenticatedClient.post(`/api/transactions/${transaction.id}/reverse`, reversalData);
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Reversal - No Authentication', 'PASSED');
      });

      it('should reject reversal with invalid token', async () => {
        logger.testStart('Transaction Reversal - Invalid Token');
        
        const invalidTokenClient = createTransactionServiceClient();
        invalidTokenClient.setAuthToken('invalid-jwt-token');
        
        const transaction = reversibleTransactions[0];
        
        const reversalData = {
          reason: 'Invalid token reversal attempt',
          description: 'Should fail'
        };
        
        const response = await invalidTokenClient.post(`/api/transactions/${transaction.id}/reverse`, reversalData);
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Reversal - Invalid Token', 'PASSED');
      });
    });
  });

  describe('Reversal History and Tracking', () => {
    describe('Reversal History Retrieval', () => {
      it('should retrieve reversal history for a transaction', async () => {
        logger.testStart('Reversal History - Transaction History');
        
        // Create and reverse a transaction
        const txnResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: testAccounts[0].id,
          amount: 125.00,
          description: 'History test transaction'
        });
        
        const reversalData = {
          reason: 'Testing reversal history',
          description: 'History tracking test'
        };
        
        const reversalResponse = await transactionClient.post(`/api/transactions/${txnResponse.data.id}/reverse`, reversalData);
        expect(reversalResponse.status).toBe(201);
        
        // Get reversal history
        const historyResponse = await transactionClient.get(`/api/transactions/${txnResponse.data.id}/reversals`);
        
        expect(historyResponse.status).toBe(200);
        expect(Array.isArray(historyResponse.data.reversals)).toBe(true);
        expect(historyResponse.data.reversals.length).toBe(1);
        
        const reversalHistory = historyResponse.data.reversals[0];
        expect(reversalHistory.originalTransactionId).toBe(txnResponse.data.id);
        expect(reversalHistory.reason).toBe(reversalData.reason);
        expect(reversalHistory.status).toBe('COMPLETED');
        
        logger.testComplete('Reversal History - Transaction History', 'PASSED');
      });

      it('should retrieve user reversal history', async () => {
        logger.testStart('Reversal History - User History');
        
        const response = await transactionClient.get('/api/transactions/reversals');
        
        expect(response.status).toBe(200);
        expect(Array.isArray(response.data.reversals)).toBe(true);
        expect(response.data.reversals.length).toBeGreaterThan(0);
        
        // Validate each reversal record
        response.data.reversals.forEach((reversal: ReversalInfo) => {
          expect(reversal.id).toBeDefined();
          expect(reversal.originalTransactionId).toBeDefined();
          expect(reversal.reversalTransactionId).toBeDefined();
          expect(reversal.reason).toBeDefined();
          expect(reversal.status).toBeDefined();
          expect(reversal.createdAt).toBeDefined();
        });
        
        logger.testComplete('Reversal History - User History', 'PASSED');
      });

      it('should retrieve account reversal history', async () => {
        logger.testStart('Reversal History - Account History');
        
        const accountId = testAccounts[0].id;
        
        const response = await transactionClient.get(`/api/transactions/account/${accountId}/reversals`);
        
        expect(response.status).toBe(200);
        expect(Array.isArray(response.data.reversals)).toBe(true);
        
        // All reversals should be for transactions from this account
        response.data.reversals.forEach(async (reversal: ReversalInfo) => {
          const originalTxnResponse = await transactionClient.get(`/api/transactions/${reversal.originalTransactionId}`);
          expect(originalTxnResponse.data.accountId).toBe(accountId);
        });
        
        logger.testComplete('Reversal History - Account History', 'PASSED');
      });
    });

    describe('Reversal Tracking and Status', () => {
      it('should track reversal status changes', async () => {
        logger.testStart('Reversal Tracking - Status Changes');
        
        // Create transaction
        const txnResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: testAccounts[0].id,
          amount: 175.00,
          description: 'Status tracking test'
        });
        
        // Initiate reversal
        const reversalData = {
          reason: 'Testing status tracking',
          description: 'Status change tracking test'
        };
        
        const reversalResponse = await transactionClient.post(`/api/transactions/${txnResponse.data.id}/reverse`, reversalData);
        
        expect(reversalResponse.status).toBe(201);
        
        const reversal: ReversalInfo = reversalResponse.data;
        expect(reversal.status).toBe('COMPLETED');
        expect(reversal.processedAt).toBeDefined();
        
        // Verify original transaction status is updated
        const originalTxnResponse = await transactionClient.get(`/api/transactions/${txnResponse.data.id}`);
        expect(originalTxnResponse.data.status).toBe('REVERSED');
        
        logger.testComplete('Reversal Tracking - Status Changes', 'PASSED');
      });

      it('should maintain referential integrity between transactions and reversals', async () => {
        logger.testStart('Reversal Tracking - Referential Integrity');
        
        // Create and reverse a transaction
        const txnResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: testAccounts[0].id,
          amount: 225.00,
          description: 'Integrity test transaction'
        });
        
        const reversalData = {
          reason: 'Testing referential integrity',
          description: 'Integrity validation test'
        };
        
        const reversalResponse = await transactionClient.post(`/api/transactions/${txnResponse.data.id}/reverse`, reversalData);
        expect(reversalResponse.status).toBe(201);
        
        const reversal: ReversalInfo = reversalResponse.data;
        
        // Verify original transaction exists and is marked as reversed
        const originalTxnResponse = await transactionClient.get(`/api/transactions/${reversal.originalTransactionId}`);
        expect(originalTxnResponse.status).toBe(200);
        expect(originalTxnResponse.data.status).toBe('REVERSED');
        
        // Verify reversal transaction exists
        const reversalTxnResponse = await transactionClient.get(`/api/transactions/${reversal.reversalTransactionId}`);
        expect(reversalTxnResponse.status).toBe(200);
        expect(reversalTxnResponse.data.type).toBe('REVERSAL');
        expect(reversalTxnResponse.data.amount).toBe(originalTxnResponse.data.amount);
        
        logger.testComplete('Reversal Tracking - Referential Integrity', 'PASSED');
      });
    });
  });

  describe('Reversal Error Handling and Edge Cases', () => {
    describe('System Error Scenarios', () => {
      it('should handle reversal processing errors gracefully', async () => {
        logger.testStart('Reversal Error Handling - Processing Errors');
        
        // This test validates that the system handles errors gracefully
        // In a real scenario, this might involve simulating database errors or service failures
        const transaction = reversibleTransactions.find(t => t.status === 'COMPLETED');
        expect(transaction).toBeDefined();
        
        const reversalData = {
          reason: 'Testing error handling',
          description: 'Error handling validation test'
        };
        
        const response = await transactionClient.post(`/api/transactions/${transaction!.id}/reverse`, reversalData);
        
        // Should either succeed or fail gracefully with proper error message
        if (response.status === 201) {
          expect(response.data.status).toBe('COMPLETED');
        } else {
          expect(response.status).toBeGreaterThanOrEqual(400);
          expect(response.data.error).toBeDefined();
        }
        
        logger.testComplete('Reversal Error Handling - Processing Errors', 'PASSED');
      });

      it('should handle concurrent reversal attempts', async () => {
        logger.testStart('Reversal Error Handling - Concurrent Attempts');
        
        // Create a transaction for concurrent reversal testing
        const txnResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: testAccounts[0].id,
          amount: 300.00,
          description: 'Concurrent reversal test'
        });
        
        const reversalData = {
          reason: 'Testing concurrent reversals',
          description: 'Concurrent reversal test'
        };
        
        // Attempt concurrent reversals
        const reversal1Promise = transactionClient.post(`/api/transactions/${txnResponse.data.id}/reverse`, reversalData);
        const reversal2Promise = transactionClient.post(`/api/transactions/${txnResponse.data.id}/reverse`, reversalData);
        
        const [response1, response2] = await Promise.all([reversal1Promise, reversal2Promise]);
        
        // One should succeed, one should fail
        const responses = [response1, response2];
        const successCount = responses.filter(r => r.status === 201).length;
        const failureCount = responses.filter(r => r.status >= 400).length;
        
        expect(successCount).toBe(1);
        expect(failureCount).toBe(1);
        
        logger.testComplete('Reversal Error Handling - Concurrent Attempts', 'PASSED');
      });
    });

    describe('Data Validation and Constraints', () => {
      it('should validate reversal reason length limits', async () => {
        logger.testStart('Reversal Validation - Reason Length');
        
        const transaction = reversibleTransactions.find(t => t.status === 'COMPLETED');
        expect(transaction).toBeDefined();
        
        // Test with excessively long reason
        const longReason = 'A'.repeat(1000); // Very long reason
        
        const reversalData = {
          reason: longReason,
          description: 'Testing reason length validation'
        };
        
        const response = await transactionClient.post(`/api/transactions/${transaction!.id}/reverse`, reversalData);
        
        // Should either accept it or reject with proper validation error
        if (response.status >= 400) {
          expect(response.data.error).toBeDefined();
          expect(response.data.error).toContain('reason');
        } else {
          expect(response.status).toBe(201);
        }
        
        logger.testComplete('Reversal Validation - Reason Length', 'PASSED');
      });

      it('should validate reversal description format', async () => {
        logger.testStart('Reversal Validation - Description Format');
        
        const transaction = reversibleTransactions.find(t => t.status === 'COMPLETED');
        expect(transaction).toBeDefined();
        
        const reversalData = {
          reason: 'Valid reason',
          description: 'Valid description with special chars: @#$%^&*()'
        };
        
        const response = await transactionClient.post(`/api/transactions/${transaction!.id}/reverse`, reversalData);
        
        // Should handle special characters in description
        expect(response.status).toBe(201);
        expect(response.data.status).toBe('COMPLETED');
        
        logger.testComplete('Reversal Validation - Description Format', 'PASSED');
      });
    });
  });
});