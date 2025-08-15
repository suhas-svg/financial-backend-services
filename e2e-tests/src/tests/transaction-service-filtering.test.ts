/**
 * Transaction Service Filtering and Search Endpoint Tests
 * Tests transaction search with various filters and pagination
 * Requirements: 3.5
 */

import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { generateTestId, createTestUser, generateRandomAmount, delay } from '../utils/test-helpers';
import { AuthResponse, AccountInfo, TransactionInfo } from '../types';

describe('Transaction Service Filtering and Search Endpoints', () => {
  let accountClient: ReturnType<typeof createAccountServiceClient>;
  let transactionClient: ReturnType<typeof createTransactionServiceClient>;
  let testUserId: string;
  let authToken: string;
  let testUser: any;
  let testAccounts: AccountInfo[] = [];
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
    testUser = createTestUser({ username: `txnfilter_${testUserId}` });
    
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
    
    // Create multiple test accounts
    await createTestAccounts();
    
    // Create diverse test transactions for filtering tests
    await createDiverseTestTransactions();
    
    logger.info('Transaction Service Filtering Tests - Setup Complete');
  });

  afterAll(async () => {
    // Cleanup
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
    logger.info('Transaction Service Filtering Tests - Cleanup Complete');
  });

  async function createTestAccounts() {
    const accountTypes = ['CHECKING', 'SAVINGS', 'CREDIT'];
    
    for (const accountType of accountTypes) {
      const accountResponse = await accountClient.post('/api/accounts', {
        accountType,
        initialBalance: 2000.00
      });
      testAccounts.push(accountResponse.data);
      await delay(50);
    }
  }

  async function createDiverseTestTransactions() {
    const baseDate = new Date();
    const transactions = [
      // Different types and amounts
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[0].id, amount: 100.00, description: 'Small deposit' }, delay: 0 },
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[0].id, amount: 500.00, description: 'Medium deposit' }, delay: 1000 },
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[1].id, amount: 1000.00, description: 'Large deposit' }, delay: 2000 },
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[2].id, amount: 50.00, description: 'Tiny deposit' }, delay: 3000 },
      
      { endpoint: '/api/transactions/withdraw', data: { accountId: testAccounts[0].id, amount: 75.00, description: 'Small withdrawal' }, delay: 4000 },
      { endpoint: '/api/transactions/withdraw', data: { accountId: testAccounts[1].id, amount: 250.00, description: 'Medium withdrawal' }, delay: 5000 },
      { endpoint: '/api/transactions/withdraw', data: { accountId: testAccounts[2].id, amount: 25.00, description: 'Tiny withdrawal' }, delay: 6000 },
      
      { endpoint: '/api/transactions/transfer', data: { fromAccountId: testAccounts[0].id, toAccountId: testAccounts[1].id, amount: 200.00, description: 'Account transfer 1' }, delay: 7000 },
      { endpoint: '/api/transactions/transfer', data: { fromAccountId: testAccounts[1].id, toAccountId: testAccounts[2].id, amount: 150.00, description: 'Account transfer 2' }, delay: 8000 },
      { endpoint: '/api/transactions/transfer', data: { fromAccountId: testAccounts[2].id, toAccountId: testAccounts[0].id, amount: 300.00, description: 'Account transfer 3' }, delay: 9000 }
    ];

    for (const transaction of transactions) {
      await delay(transaction.delay);
      const response = await transactionClient.post(transaction.endpoint, transaction.data);
      if (response.status === 201) {
        createdTransactions.push(response.data);
      }
    }
  }

  describe('Transaction Search by Type Filter', () => {
    describe('Valid Type Filtering', () => {
      it('should filter transactions by DEPOSIT type', async () => {
        logger.testStart('Transaction Search - DEPOSIT Type Filter');
        
        const response = await transactionClient.get('/api/transactions/search?type=DEPOSIT');
        
        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        expect(Array.isArray(response.data.transactions)).toBe(true);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        // Validate all returned transactions are deposits
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction).toHaveValidTransactionStructure();
          expect(transaction.type).toBe('DEPOSIT');
        });
        
        // Validate response time
        expect(response).toHaveResponseTime(5000);
        
        logger.testComplete('Transaction Search - DEPOSIT Type Filter', 'PASSED');
      });

      it('should filter transactions by WITHDRAWAL type', async () => {
        logger.testStart('Transaction Search - WITHDRAWAL Type Filter');
        
        const response = await transactionClient.get('/api/transactions/search?type=WITHDRAWAL');
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.type).toBe('WITHDRAWAL');
        });
        
        logger.testComplete('Transaction Search - WITHDRAWAL Type Filter', 'PASSED');
      });

      it('should filter transactions by TRANSFER type', async () => {
        logger.testStart('Transaction Search - TRANSFER Type Filter');
        
        const response = await transactionClient.get('/api/transactions/search?type=TRANSFER');
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.type).toBe('TRANSFER');
        });
        
        logger.testComplete('Transaction Search - TRANSFER Type Filter', 'PASSED');
      });

      it('should return empty results for non-existent type', async () => {
        logger.testStart('Transaction Search - Non-existent Type');
        
        const response = await transactionClient.get('/api/transactions/search?type=NONEXISTENT');
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBe(0);
        expect(response.data.totalElements).toBe(0);
        
        logger.testComplete('Transaction Search - Non-existent Type', 'PASSED');
      });
    });

    describe('Invalid Type Filtering', () => {
      it('should reject invalid transaction type', async () => {
        logger.testStart('Transaction Search - Invalid Type');
        
        const response = await transactionClient.get('/api/transactions/search?type=INVALID_TYPE');
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Invalid Type', 'PASSED');
      });
    });
  });

  describe('Transaction Search by Status Filter', () => {
    describe('Valid Status Filtering', () => {
      it('should filter transactions by COMPLETED status', async () => {
        logger.testStart('Transaction Search - COMPLETED Status Filter');
        
        const response = await transactionClient.get('/api/transactions/search?status=COMPLETED');
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction).toHaveValidTransactionStructure();
          expect(transaction.status).toBe('COMPLETED');
        });
        
        logger.testComplete('Transaction Search - COMPLETED Status Filter', 'PASSED');
      });

      it('should filter transactions by PENDING status', async () => {
        logger.testStart('Transaction Search - PENDING Status Filter');
        
        const response = await transactionClient.get('/api/transactions/search?status=PENDING');
        
        expect(response.status).toBe(200);
        // May return empty if no pending transactions exist
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.status).toBe('PENDING');
        });
        
        logger.testComplete('Transaction Search - PENDING Status Filter', 'PASSED');
      });

      it('should filter transactions by FAILED status', async () => {
        logger.testStart('Transaction Search - FAILED Status Filter');
        
        const response = await transactionClient.get('/api/transactions/search?status=FAILED');
        
        expect(response.status).toBe(200);
        // May return empty if no failed transactions exist
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.status).toBe('FAILED');
        });
        
        logger.testComplete('Transaction Search - FAILED Status Filter', 'PASSED');
      });
    });

    describe('Invalid Status Filtering', () => {
      it('should reject invalid transaction status', async () => {
        logger.testStart('Transaction Search - Invalid Status');
        
        const response = await transactionClient.get('/api/transactions/search?status=INVALID_STATUS');
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Invalid Status', 'PASSED');
      });
    });
  });

  describe('Transaction Search by Date Range Filter', () => {
    describe('Valid Date Range Filtering', () => {
      it('should filter transactions by date range', async () => {
        logger.testStart('Transaction Search - Date Range Filter');
        
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);
        
        const fromDate = yesterday.toISOString().split('T')[0];
        const toDate = tomorrow.toISOString().split('T')[0];
        
        const response = await transactionClient.get(
          `/api/transactions/search?fromDate=${fromDate}&toDate=${toDate}`
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        // Validate all transactions are within date range
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          const transactionDate = new Date(transaction.createdAt);
          expect(transactionDate.getTime()).toBeGreaterThanOrEqual(yesterday.getTime());
          expect(transactionDate.getTime()).toBeLessThanOrEqual(tomorrow.getTime());
        });
        
        logger.testComplete('Transaction Search - Date Range Filter', 'PASSED');
      });

      it('should filter transactions by single date', async () => {
        logger.testStart('Transaction Search - Single Date Filter');
        
        const today = new Date().toISOString().split('T')[0];
        
        const response = await transactionClient.get(
          `/api/transactions/search?fromDate=${today}&toDate=${today}`
        );
        
        expect(response.status).toBe(200);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          const transactionDate = new Date(transaction.createdAt).toISOString().split('T')[0];
          expect(transactionDate).toBe(today);
        });
        
        logger.testComplete('Transaction Search - Single Date Filter', 'PASSED');
      });

      it('should return empty results for future date range', async () => {
        logger.testStart('Transaction Search - Future Date Range');
        
        const futureDate = new Date();
        futureDate.setFullYear(futureDate.getFullYear() + 1);
        const futureDateStr = futureDate.toISOString().split('T')[0];
        
        const response = await transactionClient.get(
          `/api/transactions/search?fromDate=${futureDateStr}&toDate=${futureDateStr}`
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBe(0);
        expect(response.data.totalElements).toBe(0);
        
        logger.testComplete('Transaction Search - Future Date Range', 'PASSED');
      });
    });

    describe('Invalid Date Range Filtering', () => {
      it('should reject invalid date format', async () => {
        logger.testStart('Transaction Search - Invalid Date Format');
        
        const response = await transactionClient.get('/api/transactions/search?fromDate=invalid-date');
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Invalid Date Format', 'PASSED');
      });

      it('should reject invalid date range (from > to)', async () => {
        logger.testStart('Transaction Search - Invalid Date Range');
        
        const response = await transactionClient.get(
          '/api/transactions/search?fromDate=2024-12-31&toDate=2024-01-01'
        );
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Invalid Date Range', 'PASSED');
      });
    });
  });

  describe('Transaction Search by Amount Range Filter', () => {
    describe('Valid Amount Range Filtering', () => {
      it('should filter transactions by amount range', async () => {
        logger.testStart('Transaction Search - Amount Range Filter');
        
        const minAmount = 100.00;
        const maxAmount = 500.00;
        
        const response = await transactionClient.get(
          `/api/transactions/search?minAmount=${minAmount}&maxAmount=${maxAmount}`
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.amount).toBeGreaterThanOrEqual(minAmount);
          expect(transaction.amount).toBeLessThanOrEqual(maxAmount);
        });
        
        logger.testComplete('Transaction Search - Amount Range Filter', 'PASSED');
      });

      it('should filter transactions by minimum amount only', async () => {
        logger.testStart('Transaction Search - Minimum Amount Filter');
        
        const minAmount = 200.00;
        
        const response = await transactionClient.get(
          `/api/transactions/search?minAmount=${minAmount}`
        );
        
        expect(response.status).toBe(200);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.amount).toBeGreaterThanOrEqual(minAmount);
        });
        
        logger.testComplete('Transaction Search - Minimum Amount Filter', 'PASSED');
      });

      it('should filter transactions by maximum amount only', async () => {
        logger.testStart('Transaction Search - Maximum Amount Filter');
        
        const maxAmount = 200.00;
        
        const response = await transactionClient.get(
          `/api/transactions/search?maxAmount=${maxAmount}`
        );
        
        expect(response.status).toBe(200);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.amount).toBeLessThanOrEqual(maxAmount);
        });
        
        logger.testComplete('Transaction Search - Maximum Amount Filter', 'PASSED');
      });

      it('should return empty results for impossible amount range', async () => {
        logger.testStart('Transaction Search - Impossible Amount Range');
        
        const response = await transactionClient.get(
          '/api/transactions/search?minAmount=10000&maxAmount=20000'
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBe(0);
        
        logger.testComplete('Transaction Search - Impossible Amount Range', 'PASSED');
      });
    });

    describe('Invalid Amount Range Filtering', () => {
      it('should reject negative amounts', async () => {
        logger.testStart('Transaction Search - Negative Amount');
        
        const response = await transactionClient.get('/api/transactions/search?minAmount=-100');
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Negative Amount', 'PASSED');
      });

      it('should reject invalid amount range (min > max)', async () => {
        logger.testStart('Transaction Search - Invalid Amount Range');
        
        const response = await transactionClient.get(
          '/api/transactions/search?minAmount=500&maxAmount=100'
        );
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Invalid Amount Range', 'PASSED');
      });

      it('should reject non-numeric amount values', async () => {
        logger.testStart('Transaction Search - Non-numeric Amount');
        
        const response = await transactionClient.get('/api/transactions/search?minAmount=not-a-number');
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Non-numeric Amount', 'PASSED');
      });
    });
  });

  describe('Transaction Search by Account Filter', () => {
    describe('Valid Account Filtering', () => {
      it('should filter transactions by specific account', async () => {
        logger.testStart('Transaction Search - Account Filter');
        
        const accountId = testAccounts[0].id;
        
        const response = await transactionClient.get(
          `/api/transactions/search?accountId=${accountId}`
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.accountId).toBe(accountId);
        });
        
        logger.testComplete('Transaction Search - Account Filter', 'PASSED');
      });

      it('should filter transactions by multiple accounts', async () => {
        logger.testStart('Transaction Search - Multiple Account Filter');
        
        const accountIds = [testAccounts[0].id, testAccounts[1].id];
        const accountIdsParam = accountIds.join(',');
        
        const response = await transactionClient.get(
          `/api/transactions/search?accountIds=${accountIdsParam}`
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(0);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(accountIds).toContain(transaction.accountId);
        });
        
        logger.testComplete('Transaction Search - Multiple Account Filter', 'PASSED');
      });
    });

    describe('Invalid Account Filtering', () => {
      it('should return empty results for non-existent account', async () => {
        logger.testStart('Transaction Search - Non-existent Account');
        
        const nonExistentAccountId = 'non-existent-account-id';
        
        const response = await transactionClient.get(
          `/api/transactions/search?accountId=${nonExistentAccountId}`
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBe(0);
        
        logger.testComplete('Transaction Search - Non-existent Account', 'PASSED');
      });

      it('should reject access to other user\'s account transactions', async () => {
        logger.testStart('Transaction Search - Other User Account');
        
        // Create another user and account
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
        otherAccountClient.setAuthToken(otherLoginResponse.data.token);
        
        const otherAccountResponse = await otherAccountClient.post('/api/accounts', {
          accountType: 'CHECKING',
          initialBalance: 1000.00
        });
        
        // Try to search transactions for other user's account
        const response = await transactionClient.get(
          `/api/transactions/search?accountId=${otherAccountResponse.data.id}`
        );
        
        expect(response.status).toBe(403);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Other User Account', 'PASSED');
      });
    });
  });

  describe('Transaction Search with Combined Filters', () => {
    describe('Valid Combined Filtering', () => {
      it('should filter transactions by type and amount range', async () => {
        logger.testStart('Transaction Search - Type and Amount Combined');
        
        const response = await transactionClient.get(
          '/api/transactions/search?type=DEPOSIT&minAmount=100&maxAmount=600'
        );
        
        expect(response.status).toBe(200);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.type).toBe('DEPOSIT');
          expect(transaction.amount).toBeGreaterThanOrEqual(100);
          expect(transaction.amount).toBeLessThanOrEqual(600);
        });
        
        logger.testComplete('Transaction Search - Type and Amount Combined', 'PASSED');
      });

      it('should filter transactions by status, date range, and account', async () => {
        logger.testStart('Transaction Search - Status, Date, and Account Combined');
        
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);
        
        const fromDate = yesterday.toISOString().split('T')[0];
        const toDate = tomorrow.toISOString().split('T')[0];
        const accountId = testAccounts[0].id;
        
        const response = await transactionClient.get(
          `/api/transactions/search?status=COMPLETED&fromDate=${fromDate}&toDate=${toDate}&accountId=${accountId}`
        );
        
        expect(response.status).toBe(200);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.status).toBe('COMPLETED');
          expect(transaction.accountId).toBe(accountId);
          
          const transactionDate = new Date(transaction.createdAt);
          expect(transactionDate.getTime()).toBeGreaterThanOrEqual(yesterday.getTime());
          expect(transactionDate.getTime()).toBeLessThanOrEqual(tomorrow.getTime());
        });
        
        logger.testComplete('Transaction Search - Status, Date, and Account Combined', 'PASSED');
      });

      it('should filter transactions with all available filters', async () => {
        logger.testStart('Transaction Search - All Filters Combined');
        
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);
        
        const fromDate = yesterday.toISOString().split('T')[0];
        const toDate = tomorrow.toISOString().split('T')[0];
        
        const response = await transactionClient.get(
          `/api/transactions/search?type=DEPOSIT&status=COMPLETED&fromDate=${fromDate}&toDate=${toDate}&minAmount=50&maxAmount=1000&accountId=${testAccounts[0].id}`
        );
        
        expect(response.status).toBe(200);
        
        response.data.transactions.forEach((transaction: TransactionInfo) => {
          expect(transaction.type).toBe('DEPOSIT');
          expect(transaction.status).toBe('COMPLETED');
          expect(transaction.accountId).toBe(testAccounts[0].id);
          expect(transaction.amount).toBeGreaterThanOrEqual(50);
          expect(transaction.amount).toBeLessThanOrEqual(1000);
          
          const transactionDate = new Date(transaction.createdAt);
          expect(transactionDate.getTime()).toBeGreaterThanOrEqual(yesterday.getTime());
          expect(transactionDate.getTime()).toBeLessThanOrEqual(tomorrow.getTime());
        });
        
        logger.testComplete('Transaction Search - All Filters Combined', 'PASSED');
      });
    });
  });

  describe('Transaction Search Pagination and Sorting', () => {
    describe('Valid Pagination', () => {
      it('should paginate search results correctly', async () => {
        logger.testStart('Transaction Search - Pagination');
        
        // Get first page
        const firstPageResponse = await transactionClient.get(
          '/api/transactions/search?page=0&size=3'
        );
        
        expect(firstPageResponse.status).toBe(200);
        expect(firstPageResponse.data.transactions.length).toBeLessThanOrEqual(3);
        expect(firstPageResponse.data.currentPage).toBe(0);
        expect(firstPageResponse.data.totalElements).toBeGreaterThan(0);
        expect(firstPageResponse.data.totalPages).toBeGreaterThan(0);
        
        // Get second page if available
        if (firstPageResponse.data.totalPages > 1) {
          const secondPageResponse = await transactionClient.get(
            '/api/transactions/search?page=1&size=3'
          );
          
          expect(secondPageResponse.status).toBe(200);
          expect(secondPageResponse.data.currentPage).toBe(1);
          
          // Ensure different results
          const firstPageIds = firstPageResponse.data.transactions.map((t: TransactionInfo) => t.id);
          const secondPageIds = secondPageResponse.data.transactions.map((t: TransactionInfo) => t.id);
          const intersection = firstPageIds.filter((id: string) => secondPageIds.includes(id));
          expect(intersection.length).toBe(0);
        }
        
        logger.testComplete('Transaction Search - Pagination', 'PASSED');
      });

      it('should handle empty pages correctly', async () => {
        logger.testStart('Transaction Search - Empty Page');
        
        const response = await transactionClient.get(
          '/api/transactions/search?page=999&size=10'
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBe(0);
        expect(response.data.currentPage).toBe(999);
        
        logger.testComplete('Transaction Search - Empty Page', 'PASSED');
      });
    });

    describe('Valid Sorting', () => {
      it('should sort search results by creation date descending', async () => {
        logger.testStart('Transaction Search - Sort by Date Desc');
        
        const response = await transactionClient.get(
          '/api/transactions/search?sort=createdAt,desc'
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(1);
        
        // Verify descending order
        const transactions = response.data.transactions;
        for (let i = 1; i < transactions.length; i++) {
          const prevDate = new Date(transactions[i - 1].createdAt);
          const currDate = new Date(transactions[i].createdAt);
          expect(prevDate.getTime()).toBeGreaterThanOrEqual(currDate.getTime());
        }
        
        logger.testComplete('Transaction Search - Sort by Date Desc', 'PASSED');
      });

      it('should sort search results by amount ascending', async () => {
        logger.testStart('Transaction Search - Sort by Amount Asc');
        
        const response = await transactionClient.get(
          '/api/transactions/search?sort=amount,asc'
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(1);
        
        // Verify ascending order
        const transactions = response.data.transactions;
        for (let i = 1; i < transactions.length; i++) {
          expect(transactions[i - 1].amount).toBeLessThanOrEqual(transactions[i].amount);
        }
        
        logger.testComplete('Transaction Search - Sort by Amount Asc', 'PASSED');
      });

      it('should sort search results by type and then by date', async () => {
        logger.testStart('Transaction Search - Multi-field Sort');
        
        const response = await transactionClient.get(
          '/api/transactions/search?sort=type,asc&sort=createdAt,desc'
        );
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeGreaterThan(1);
        
        // Verify primary sort by type
        const transactions = response.data.transactions;
        for (let i = 1; i < transactions.length; i++) {
          const prevType = transactions[i - 1].type;
          const currType = transactions[i].type;
          expect(prevType.localeCompare(currType)).toBeLessThanOrEqual(0);
        }
        
        logger.testComplete('Transaction Search - Multi-field Sort', 'PASSED');
      });
    });

    describe('Invalid Pagination and Sorting', () => {
      it('should reject invalid pagination parameters', async () => {
        logger.testStart('Transaction Search - Invalid Pagination');
        
        // Test negative page
        const negativePageResponse = await transactionClient.get(
          '/api/transactions/search?page=-1&size=10'
        );
        expect(negativePageResponse.status).toBe(400);
        
        // Test zero size
        const zeroSizeResponse = await transactionClient.get(
          '/api/transactions/search?page=0&size=0'
        );
        expect(zeroSizeResponse.status).toBe(400);
        
        // Test excessive size
        const excessiveSizeResponse = await transactionClient.get(
          '/api/transactions/search?page=0&size=1000'
        );
        expect(excessiveSizeResponse.status).toBe(400);
        
        logger.testComplete('Transaction Search - Invalid Pagination', 'PASSED');
      });

      it('should reject invalid sort parameters', async () => {
        logger.testStart('Transaction Search - Invalid Sort');
        
        const response = await transactionClient.get(
          '/api/transactions/search?sort=invalidField,asc'
        );
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Invalid Sort', 'PASSED');
      });
    });
  });

  describe('Transaction Search Error Handling', () => {
    describe('Authentication and Authorization', () => {
      it('should reject search without authentication', async () => {
        logger.testStart('Transaction Search - No Authentication');
        
        const unauthenticatedClient = createTransactionServiceClient();
        
        const response = await unauthenticatedClient.get('/api/transactions/search?type=DEPOSIT');
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - No Authentication', 'PASSED');
      });

      it('should reject search with invalid token', async () => {
        logger.testStart('Transaction Search - Invalid Token');
        
        const invalidTokenClient = createTransactionServiceClient();
        invalidTokenClient.setAuthToken('invalid-jwt-token');
        
        const response = await invalidTokenClient.get('/api/transactions/search?type=DEPOSIT');
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Search - Invalid Token', 'PASSED');
      });
    });

    describe('Performance and Limits', () => {
      it('should handle search with reasonable response time', async () => {
        logger.testStart('Transaction Search - Performance');
        
        const startTime = Date.now();
        const response = await transactionClient.get('/api/transactions/search');
        const duration = Date.now() - startTime;
        
        expect(response.status).toBe(200);
        expect(duration).toBeLessThan(5000); // Should complete within 5 seconds
        
        logger.testComplete('Transaction Search - Performance', 'PASSED');
      });

      it('should enforce reasonable result limits', async () => {
        logger.testStart('Transaction Search - Result Limits');
        
        const response = await transactionClient.get('/api/transactions/search?size=100');
        
        expect(response.status).toBe(200);
        expect(response.data.transactions.length).toBeLessThanOrEqual(100);
        
        logger.testComplete('Transaction Search - Result Limits', 'PASSED');
      });
    });
  });
});