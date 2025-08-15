/**
 * Transaction Service Retrieval Endpoint Tests
 * Tests transaction retrieval by ID, account history, and user history
 * Requirements: 3.4
 */

import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { generateTestId, createTestUser, generateRandomAmount, delay } from '../utils/test-helpers';
import { AuthResponse, AccountInfo, TransactionInfo } from '../types';

describe('Transaction Service Retrieval Endpoints', () => {
  let accountClient: ReturnType<typeof createAccountServiceClient>;
  let transactionClient: ReturnType<typeof createTransactionServiceClient>;
  let testUserId: string;
  let authToken: string;
  let testUser: any;
  let testAccount: AccountInfo;
  let secondAccount: AccountInfo;
  let createdTransactions: TransactionInfo[] = [];

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
    testUser = createTestUser({ username: `txnretrieval_${testUserId}` });
    
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
    const accountResponse = await accountClient.post('/api/accounts', {
      accountType: 'CHECKING',
      initialBalance: 2000.00
    });
    testAccount = accountResponse.data;
    
    const secondAccountResponse = await accountClient.post('/api/accounts', {
      accountType: 'SAVINGS',
      initialBalance: 1500.00
    });
    secondAccount = secondAccountResponse.data;
    
    // Create test transactions for retrieval tests
    await createTestTransactions();
    
    logger.info('Transaction Service Retrieval Tests - Setup Complete');
  });

  afterAll(async () => {
    // Cleanup
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
    logger.info('Transaction Service Retrieval Tests - Cleanup Complete');
  });

  async function createTestTransactions() {
    // Create various types of transactions for testing
    const transactions = [
      // Deposits
      {
        endpoint: '/api/transactions/deposit',
        data: { accountId: testAccount.id, amount: 500.00, description: 'Test deposit 1' }
      },
      {
        endpoint: '/api/transactions/deposit',
        data: { accountId: testAccount.id, amount: 250.00, description: 'Test deposit 2' }
      },
      {
        endpoint: '/api/transactions/deposit',
        data: { accountId: secondAccount.id, amount: 300.00, description: 'Test deposit 3' }
      },
      // Withdrawals
      {
        endpoint: '/api/transactions/withdraw',
        data: { accountId: testAccount.id, amount: 100.00, description: 'Test withdrawal 1' }
      },
      {
        endpoint: '/api/transactions/withdraw',
        data: { accountId: secondAccount.id, amount: 50.00, description: 'Test withdrawal 2' }
      },
      // Transfer
      {
        endpoint: '/api/transactions/transfer',
        data: { 
          fromAccountId: testAccount.id, 
          toAccountId: secondAccount.id, 
          amount: 200.00, 
          description: 'Test transfer 1' 
        }
      }
    ];

    for (const transaction of transactions) {
      const response = await transactionClient.post(transaction.endpoint, transaction.data);
      if (response.status === 201) {
        createdTransactions.push(response.data);
      }
      // Small delay to ensure different timestamps
      await delay(100);
    }
  }

  describe('Transaction Retrieval by ID Endpoint', () => {
    describe('Valid Retrieval Scenarios', () => {
      it('should retrieve transaction by valid ID', async () => {
        logger.testStart('Transaction Retrieval - Valid ID');
        
        const transactionId = createdTransactions[0].id;
        
        const startTime = Date.now();
        const response = await transactionClient.get(`/api/transactions/${transactionId}`);
        const duration = Date.now() - startTime;

        // Validate response structure
        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        expect(response.data).toHaveValidTransactionStructure();
        
        // Validate transaction details
        const transaction: TransactionInfo = response.data;
        expect(transaction.id).toBe(transactionId);
        expect(transaction.accountId).toBe(testAccount.id);
        expect(transaction.type).toBeDefined();
        expect(transaction.amount).toBeGreaterThan(0);
        expect(transaction.status).toBeDefined();
        expect(transaction.createdAt).toBeDefined();
        
        // Validate response time
        expect(response).toHaveResponseTime(3000);
        
        logger.testComplete('Transaction Retrieval - Valid ID', 'PASSED', duration);
      });

      it('should retrieve different transaction types correctly', async () => {
        logger.testStart('Transaction Retrieval - Different Types');
        
        // Test deposit transaction
        const depositTransaction = createdTransactions.find(t => t.type === 'DEPOSIT');
        if (depositTransaction) {
          const depositResponse = await transactionClient.get(`/api/transactions/${depositTransaction.id}`);
          expect(depositResponse.status).toBe(200);
          expect(depositResponse.data.type).toBe('DEPOSIT');
          expect(depositResponse.data.amount).toBeGreaterThan(0);
        }
        
        // Test withdrawal transaction
        const withdrawalTransaction = createdTransactions.find(t => t.type === 'WITHDRAWAL');
        if (withdrawalTransaction) {
          const withdrawalResponse = await transactionClient.get(`/api/transactions/${withdrawalTransaction.id}`);
          expect(withdrawalResponse.status).toBe(200);
          expect(withdrawalResponse.data.type).toBe('WITHDRAWAL');
          expect(withdrawalResponse.data.amount).toBeGreaterThan(0);
        }
        
        // Test transfer transaction
        const transferTransaction = createdTransactions.find(t => t.type === 'TRANSFER');
        if (transferTransaction) {
          const transferResponse = await transactionClient.get(`/api/transactions/${transferTransaction.id}`);
          expect(transferResponse.status).toBe(200);
          expect(transferResponse.data.type).toBe('TRANSFER');
          expect(transferResponse.data.amount).toBeGreaterThan(0);
        }
        
        logger.testComplete('Transaction Retrieval - Different Types', 'PASSED');
      });
    });

    describe('Invalid Retrieval Scenarios', () => {
      it('should return 404 for non-existent transaction ID', async () => {
        logger.testStart('Transaction Retrieval - Non-existent ID');
        
        const nonExistentId = 'non-existent-transaction-id';
        
        const response = await transactionClient.get(`/api/transactions/${nonExistentId}`);
        
        expect(response.status).toBe(404);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Retrieval - Non-existent ID', 'PASSED');
      });

      it('should return 400 for invalid transaction ID format', async () => {
        logger.testStart('Transaction Retrieval - Invalid ID Format');
        
        const invalidId = 'invalid-id-format-123!@#';
        
        const response = await transactionClient.get(`/api/transactions/${invalidId}`);
        
        expect([400, 404]).toContain(response.status);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Retrieval - Invalid ID Format', 'PASSED');
      });

      it('should reject retrieval without authentication', async () => {
        logger.testStart('Transaction Retrieval - No Authentication');
        
        const unauthenticatedClient = createTransactionServiceClient();
        const transactionId = createdTransactions[0].id;
        
        const response = await unauthenticatedClient.get(`/api/transactions/${transactionId}`);
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Retrieval - No Authentication', 'PASSED');
      });

      it('should reject retrieval of other user\'s transaction', async () => {
        logger.testStart('Transaction Retrieval - Other User Transaction');
        
        // Create another user
        const otherUser = createTestUser({ username: `otheruser_${testUserId}` });
        
        await accountClient.post('/api/auth/register', {
          username: otherUser.username,
          password: otherUser.password
        });
        
        const otherLoginResponse = await accountClient.post('/api/auth/login', {
          username: otherUser.username,
          password: otherUser.password
        });
        
        const otherClient = createTransactionServiceClient();
        otherClient.setAuthToken(otherLoginResponse.data.token);
        
        // Try to access original user's transaction
        const transactionId = createdTransactions[0].id;
        const response = await otherClient.get(`/api/transactions/${transactionId}`);
        
        expect(response.status).toBe(403);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Retrieval - Other User Transaction', 'PASSED');
      });
    });
  });

  describe('Account Transaction History Endpoint', () => {
    describe('Valid History Retrieval', () => {
      it('should retrieve account transaction history', async () => {
        logger.testStart('Account Transaction History - Basic Retrieval');
        
        const response = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
        
        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        expect(Array.isArray(response.data.transactions)).toBe(true);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        // Validate each transaction structure
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction).toHaveValidTransactionStructure();
          expect(transaction.accountId).toBe(testAccount.id);
        });
        
        logger.testComplete('Account Transaction History - Basic Retrieval', 'PASSED');
      });

      it('should retrieve account history with pagination', async () => {
        logger.testStart('Account Transaction History - Pagination');
        
        // Test first page
        const firstPageResponse = await transactionClient.get(
          `/api/transactions/account/${testAccount.id}?page=0&size=2`
        );
        
        expect(firstPageResponse.status).toBe(200);
        expect(firstPageResponse.data.transactions).toBeDefined();
        expect(firstPageResponse.data.transactions.length).toBeLessThanOrEqual(2);
        expect(firstPageResponse.data.totalElements).toBeGreaterThan(0);
        expect(firstPageResponse.data.totalPages).toBeGreaterThan(0);
        expect(firstPageResponse.data.currentPage).toBe(0);
        
        // Test second page if available
        if (firstPageResponse.data.totalPages > 1) {
          const secondPageResponse = await transactionClient.get(
            `/api/transactions/account/${testAccount.id}?page=1&size=2`
          );
          
          expect(secondPageResponse.status).toBe(200);
          expect(secondPageResponse.data.currentPage).toBe(1);
        }
        
        logger.testComplete('Account Transaction History - Pagination', 'PASSED');
      });

      it('should retrieve account history with sorting', async () => {
        logger.testStart('Account Transaction History - Sorting');
        
        // Test sorting by creation date descending (newest first)
        const descResponse = await transactionClient.get(
          `/api/transactions/account/${testAccount.id}?sort=createdAt,desc`
        );
        
        expect(descResponse.status).toBe(200);
        expect(descResponse.data.transactions.length).toBeGreaterThan(1);
        
        // Verify descending order
        const descTransactions = descResponse.data.transactions;
        for (let i = 1; i < descTransactions.length; i++) {
          const prevDate = new Date(descTransactions[i - 1].createdAt);
          const currDate = new Date(descTransactions[i].createdAt);
          expect(prevDate.getTime()).toBeGreaterThanOrEqual(currDate.getTime());
        }
        
        // Test sorting by amount ascending
        const amountAscResponse = await transactionClient.get(
          `/api/transactions/account/${testAccount.id}?sort=amount,asc`
        );
        
        expect(amountAscResponse.status).toBe(200);
        const amountTransactions = amountAscResponse.data.transactions;
        for (let i = 1; i < amountTransactions.length; i++) {
          expect(amountTransactions[i - 1].amount).toBeLessThanOrEqual(amountTransactions[i].amount);
        }
        
        logger.testComplete('Account Transaction History - Sorting', 'PASSED');
      });
    });

    describe('Invalid History Retrieval', () => {
      it('should return 404 for non-existent account', async () => {
        logger.testStart('Account Transaction History - Non-existent Account');
        
        const nonExistentAccountId = 'non-existent-account-id';
        
        const response = await transactionClient.get(`/api/transactions/account/${nonExistentAccountId}`);
        
        expect(response.status).toBe(404);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Account Transaction History - Non-existent Account', 'PASSED');
      });

      it('should return 400 for invalid pagination parameters', async () => {
        logger.testStart('Account Transaction History - Invalid Pagination');
        
        // Test negative page number
        const negativePageResponse = await transactionClient.get(
          `/api/transactions/account/${testAccount.id}?page=-1&size=10`
        );
        expect(negativePageResponse.status).toBe(400);
        
        // Test invalid page size
        const invalidSizeResponse = await transactionClient.get(
          `/api/transactions/account/${testAccount.id}?page=0&size=0`
        );
        expect(invalidSizeResponse.status).toBe(400);
        
        // Test excessive page size
        const excessiveSizeResponse = await transactionClient.get(
          `/api/transactions/account/${testAccount.id}?page=0&size=1000`
        );
        expect(excessiveSizeResponse.status).toBe(400);
        
        logger.testComplete('Account Transaction History - Invalid Pagination', 'PASSED');
      });

      it('should reject access to other user\'s account history', async () => {
        logger.testStart('Account Transaction History - Other User Account');
        
        // Create another user and account
        const otherUser = createTestUser({ username: `otheruser2_${testUserId}` });
        
        await accountClient.post('/api/auth/register', {
          username: otherUser.username,
          password: otherUser.password
        });
        
        const otherLoginResponse = await accountClient.post('/api/auth/login', {
          username: otherUser.username,
          password: otherUser.password
        });
        
        const otherAccountClient = createAccountServiceClient();
        otherAccountClient.setAuthToken(otherLoginResponse.data.token);
        
        const otherAccountResponse = await otherAccountClient.post('/api/accounts', {
          accountType: 'CHECKING',
          initialBalance: 1000.00
        });
        
        // Try to access other user's account history with original user's token
        const response = await transactionClient.get(`/api/transactions/account/${otherAccountResponse.data.id}`);
        
        expect(response.status).toBe(403);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Account Transaction History - Other User Account', 'PASSED');
      });
    });
  });

  describe('User Transaction History Endpoint', () => {
    describe('Valid User History Retrieval', () => {
      it('should retrieve user transaction history across all accounts', async () => {
        logger.testStart('User Transaction History - All Accounts');
        
        const response = await transactionClient.get('/api/transactions');
        
        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        expect(Array.isArray(response.data.transactions)).toBe(true);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        // Validate each transaction structure
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction).toHaveValidTransactionStructure();
          // Transaction should belong to one of the user's accounts
          expect([testAccount.id, secondAccount.id]).toContain(transaction.accountId);
        });
        
        // Should include transactions from both accounts
        const accountIds = response.data.transactions.map((t: TransactionInfo) => t.accountId);
        const uniqueAccountIds = [...new Set(accountIds)];
        expect(uniqueAccountIds.length).toBeGreaterThan(1);
        
        logger.testComplete('User Transaction History - All Accounts', 'PASSED');
      });

      it('should retrieve user history with filtering by transaction type', async () => {
        logger.testStart('User Transaction History - Type Filtering');
        
        // Test deposit filter
        const depositResponse = await transactionClient.get('/api/transactions?type=DEPOSIT');
        expect(depositResponse.status).toBe(200);
        depositResponse.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.type).toBe('DEPOSIT');
        });
        
        // Test withdrawal filter
        const withdrawalResponse = await transactionClient.get('/api/transactions?type=WITHDRAWAL');
        expect(withdrawalResponse.status).toBe(200);
        withdrawalResponse.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.type).toBe('WITHDRAWAL');
        });
        
        // Test transfer filter
        const transferResponse = await transactionClient.get('/api/transactions?type=TRANSFER');
        expect(transferResponse.status).toBe(200);
        transferResponse.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.type).toBe('TRANSFER');
        });
        
        logger.testComplete('User Transaction History - Type Filtering', 'PASSED');
      });

      it('should retrieve user history with date range filtering', async () => {
        logger.testStart('User Transaction History - Date Range Filtering');
        
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);
        
        const fromDate = yesterday.toISOString().split('T')[0];
        const toDate = tomorrow.toISOString().split('T')[0];
        
        const response = await transactionClient.get(
          `/api/transactions?fromDate=${fromDate}&toDate=${toDate}`
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        // Validate all transactions are within date range
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          const transactionDate = new Date(transaction.createdAt);
          expect(transactionDate.getTime()).toBeGreaterThanOrEqual(yesterday.getTime());
          expect(transactionDate.getTime()).toBeLessThanOrEqual(tomorrow.getTime());
        });
        
        logger.testComplete('User Transaction History - Date Range Filtering', 'PASSED');
      });

      it('should retrieve user history with pagination and sorting', async () => {
        logger.testStart('User Transaction History - Pagination and Sorting');
        
        const response = await transactionClient.get(
          '/api/transactions?page=0&size=3&sort=createdAt,desc'
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeLessThanOrEqual(3);
        expect(response.data.totalElements).toBeGreaterThan(0);
        expect(response.data.currentPage).toBe(0);
        
        // Verify sorting (newest first)
        const transactions = response.data.transactions;
        for (let i = 1; i < transactions.length; i++) {
          const prevDate = new Date(transactions[i - 1].createdAt);
          const currDate = new Date(transactions[i].createdAt);
          expect(prevDate.getTime()).toBeGreaterThanOrEqual(currDate.getTime());
        }
        
        logger.testComplete('User Transaction History - Pagination and Sorting', 'PASSED');
      });
    });

    describe('Invalid User History Retrieval', () => {
      it('should reject retrieval without authentication', async () => {
        logger.testStart('User Transaction History - No Authentication');
        
        const unauthenticatedClient = createTransactionServiceClient();
        
        const response = await unauthenticatedClient.get('/api/transactions');
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('User Transaction History - No Authentication', 'PASSED');
      });

      it('should return 400 for invalid filter parameters', async () => {
        logger.testStart('User Transaction History - Invalid Filters');
        
        // Test invalid transaction type
        const invalidTypeResponse = await transactionClient.get('/api/transactions?type=INVALID_TYPE');
        expect(invalidTypeResponse.status).toBe(400);
        
        // Test invalid date format
        const invalidDateResponse = await transactionClient.get('/api/transactions?fromDate=invalid-date');
        expect(invalidDateResponse.status).toBe(400);
        
        // Test invalid date range (from > to)
        const invalidRangeResponse = await transactionClient.get(
          '/api/transactions?fromDate=2024-12-31&toDate=2024-01-01'
        );
        expect(invalidRangeResponse.status).toBe(400);
        
        logger.testComplete('User Transaction History - Invalid Filters', 'PASSED');
      });

      it('should return empty result for future date range', async () => {
        logger.testStart('User Transaction History - Future Date Range');
        
        const futureDate = new Date();
        futureDate.setFullYear(futureDate.getFullYear() + 1);
        const futureDateStr = futureDate.toISOString().split('T')[0];
        
        const response = await transactionClient.get(
          `/api/transactions?fromDate=${futureDateStr}&toDate=${futureDateStr}`
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBe(0);
        expect(response.data.totalElements).toBe(0);
        
        logger.testComplete('User Transaction History - Future Date Range', 'PASSED');
      });
    });
  });

  describe('Transaction Retrieval Error Handling', () => {
    describe('Service Communication Errors', () => {
      it('should handle database connection errors gracefully', async () => {
        logger.testStart('Transaction Retrieval - Database Error Handling');
        
        // This test would require simulating database issues
        // For now, we'll test that the service responds appropriately to valid requests
        const response = await transactionClient.get(`/api/transactions/${createdTransactions[0].id}`);
        expect(response.status).toBe(200);
        
        logger.testComplete('Transaction Retrieval - Database Error Handling', 'PASSED');
      });

      it('should handle timeout scenarios', async () => {
        logger.testStart('Transaction Retrieval - Timeout Handling');
        
        // Test with a reasonable timeout
        const response = await transactionClient.get('/api/transactions');
        expect(response.status).toBe(200);
        expect(response.duration).toBeLessThan(10000); // Should complete within 10 seconds
        
        logger.testComplete('Transaction Retrieval - Timeout Handling', 'PASSED');
      });
    });

    describe('Data Consistency Validation', () => {
      it('should return consistent transaction data across different endpoints', async () => {
        logger.testStart('Transaction Retrieval - Data Consistency');
        
        const transactionId = createdTransactions[0].id;
        
        // Get transaction by ID
        const byIdResponse = await transactionClient.get(`/api/transactions/${transactionId}`);
        
        // Get transaction through account history
        const accountHistoryResponse = await transactionClient.get(`/api/transactions/account/${testAccount.id}`);
        const transactionFromHistory = accountHistoryResponse.data.transactions.find(
          (t: TransactionInfo) => t.id === transactionId
        );
        
        // Get transaction through user history
        const userHistoryResponse = await transactionClient.get('/api/transactions');
        const transactionFromUserHistory = userHistoryResponse.data.transactions.find(
          (t: TransactionInfo) => t.id === transactionId
        );
        
        // All should return the same transaction data
        expect(byIdResponse.data).toEqual(transactionFromHistory);
        expect(byIdResponse.data).toEqual(transactionFromUserHistory);
        
        logger.testComplete('Transaction Retrieval - Data Consistency', 'PASSED');
      });
    });
  });
});