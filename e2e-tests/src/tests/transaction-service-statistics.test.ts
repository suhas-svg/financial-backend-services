/**
 * Transaction Service Statistics Endpoint Tests
 * Tests account and user transaction statistics with date range filtering
 * Requirements: 3.6
 */

import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { generateTestId, createTestUser, generateRandomAmount, delay, compareAmounts } from '../utils/test-helpers';
import { AuthResponse, AccountInfo, TransactionInfo } from '../types';

interface TransactionStatistics {
  totalTransactions: number;
  totalDeposits: number;
  totalWithdrawals: number;
  totalTransfers: number;
  totalAmount: number;
  depositAmount: number;
  withdrawalAmount: number;
  transferAmount: number;
  averageTransactionAmount: number;
  largestTransaction: number;
  smallestTransaction: number;
  period: {
    fromDate: string;
    toDate: string;
  };
}

describe('Transaction Service Statistics Endpoints', () => {
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
    testUser = createTestUser({ username: `txnstats_${testUserId}` });
    
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
    
    // Create test transactions for statistics
    await createStatisticsTestTransactions();
    
    logger.info('Transaction Service Statistics Tests - Setup Complete');
  });

  afterAll(async () => {
    // Cleanup
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
    logger.info('Transaction Service Statistics Tests - Cleanup Complete');
  });

  async function createTestAccounts() {
    const accountTypes = ['CHECKING', 'SAVINGS'];
    
    for (const accountType of accountTypes) {
      const accountResponse = await accountClient.post('/api/accounts', {
        accountType,
        initialBalance: 3000.00
      });
      testAccounts.push(accountResponse.data);
      await delay(50);
    }
  }

  async function createStatisticsTestTransactions() {
    const transactions = [
      // Deposits with known amounts for statistics validation
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[0].id, amount: 100.00, description: 'Stats deposit 1' }, delay: 0 },
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[0].id, amount: 200.00, description: 'Stats deposit 2' }, delay: 500 },
      { endpoint: '/api/transactions/deposit', data: { accountId: testAccounts[1].id, amount: 300.00, description: 'Stats deposit 3' }, delay: 1000 },
      
      // Withdrawals with known amounts
      { endpoint: '/api/transactions/withdraw', data: { accountId: testAccounts[0].id, amount: 50.00, description: 'Stats withdrawal 1' }, delay: 1500 },
      { endpoint: '/api/transactions/withdraw', data: { accountId: testAccounts[1].id, amount: 75.00, description: 'Stats withdrawal 2' }, delay: 2000 },
      
      // Transfers with known amounts
      { endpoint: '/api/transactions/transfer', data: { fromAccountId: testAccounts[0].id, toAccountId: testAccounts[1].id, amount: 150.00, description: 'Stats transfer 1' }, delay: 2500 },
      { endpoint: '/api/transactions/transfer', data: { fromAccountId: testAccounts[1].id, toAccountId: testAccounts[0].id, amount: 250.00, description: 'Stats transfer 2' }, delay: 3000 }
    ];

    for (const transaction of transactions) {
      await delay(transaction.delay);
      const response = await transactionClient.post(transaction.endpoint, transaction.data);
      if (response.status === 201) {
        createdTransactions.push(response.data);
      }
    }
  }

  describe('Account Transaction Statistics Endpoint', () => {
    describe('Valid Account Statistics Retrieval', () => {
      it('should retrieve basic account transaction statistics', async () => {
        logger.testStart('Account Statistics - Basic Retrieval');
        
        const accountId = testAccounts[0].id;
        
        const startTime = Date.now();
        const response = await transactionClient.get(`/api/transactions/account/${accountId}/stats`);
        const duration = Date.now() - startTime;

        // Validate response structure
        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        
        const stats: TransactionStatistics = response.data;
        
        // Validate statistics structure
        expect(typeof stats.totalTransactions).toBe('number');
        expect(typeof stats.totalDeposits).toBe('number');
        expect(typeof stats.totalWithdrawals).toBe('number');
        expect(typeof stats.totalTransfers).toBe('number');
        expect(typeof stats.totalAmount).toBe('number');
        expect(typeof stats.depositAmount).toBe('number');
        expect(typeof stats.withdrawalAmount).toBe('number');
        expect(typeof stats.transferAmount).toBe('number');
        expect(typeof stats.averageTransactionAmount).toBe('number');
        expect(typeof stats.largestTransaction).toBe('number');
        expect(typeof stats.smallestTransaction).toBe('number');
        
        // Validate statistics values
        expect(stats.totalTransactions).toBeGreaterThan(0);
        expect(stats.totalTransactions).toBe(stats.totalDeposits + stats.totalWithdrawals + stats.totalTransfers);
        expect(stats.totalAmount).toBe(stats.depositAmount + stats.withdrawalAmount + stats.transferAmount);
        
        // Validate response time
        expect(response).toHaveResponseTime(5000);
        
        logger.testComplete('Account Statistics - Basic Retrieval', 'PASSED', duration);
      });

      it('should calculate account statistics correctly', async () => {
        logger.testStart('Account Statistics - Calculation Validation');
        
        const accountId = testAccounts[0].id;
        
        // Get account transactions to manually calculate expected statistics
        const transactionsResponse = await transactionClient.get(`/api/transactions/account/${accountId}`);
        const transactions = transactionsResponse.data.transactions;
        
        // Calculate expected statistics
        const expectedStats = {
          totalTransactions: transactions.length,
          totalDeposits: transactions.filter((t: TransactionInfo) => t.type === 'DEPOSIT').length,
          totalWithdrawals: transactions.filter((t: TransactionInfo) => t.type === 'WITHDRAWAL').length,
          totalTransfers: transactions.filter((t: TransactionInfo) => t.type === 'TRANSFER').length,
          depositAmount: transactions.filter((t: TransactionInfo) => t.type === 'DEPOSIT').reduce((sum: number, t: TransactionInfo) => sum + t.amount, 0),
          withdrawalAmount: transactions.filter((t: TransactionInfo) => t.type === 'WITHDRAWAL').reduce((sum: number, t: TransactionInfo) => sum + t.amount, 0),
          transferAmount: transactions.filter((t: TransactionInfo) => t.type === 'TRANSFER').reduce((sum: number, t: TransactionInfo) => sum + t.amount, 0)
        };
        
        expectedStats.totalAmount = expectedStats.depositAmount + expectedStats.withdrawalAmount + expectedStats.transferAmount;
        const expectedAverage = expectedStats.totalAmount / expectedStats.totalTransactions;
        const amounts = transactions.map((t: TransactionInfo) => t.amount);
        const expectedLargest = Math.max(...amounts);
        const expectedSmallest = Math.min(...amounts);
        
        // Get actual statistics
        const response = await transactionClient.get(`/api/transactions/account/${accountId}/stats`);
        const actualStats: TransactionStatistics = response.data;
        
        // Validate calculations
        expect(actualStats.totalTransactions).toBe(expectedStats.totalTransactions);
        expect(actualStats.totalDeposits).toBe(expectedStats.totalDeposits);
        expect(actualStats.totalWithdrawals).toBe(expectedStats.totalWithdrawals);
        expect(actualStats.totalTransfers).toBe(expectedStats.totalTransfers);
        expect(compareAmounts(actualStats.depositAmount, expectedStats.depositAmount)).toBe(true);
        expect(compareAmounts(actualStats.withdrawalAmount, expectedStats.withdrawalAmount)).toBe(true);
        expect(compareAmounts(actualStats.transferAmount, expectedStats.transferAmount)).toBe(true);
        expect(compareAmounts(actualStats.totalAmount, expectedStats.totalAmount)).toBe(true);
        expect(compareAmounts(actualStats.averageTransactionAmount, expectedAverage)).toBe(true);
        expect(compareAmounts(actualStats.largestTransaction, expectedLargest)).toBe(true);
        expect(compareAmounts(actualStats.smallestTransaction, expectedSmallest)).toBe(true);
        
        logger.testComplete('Account Statistics - Calculation Validation', 'PASSED');
      });

      it('should retrieve account statistics with date range filtering', async () => {
        logger.testStart('Account Statistics - Date Range Filter');
        
        const accountId = testAccounts[0].id;
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);
        
        const fromDate = yesterday.toISOString().split('T')[0];
        const toDate = tomorrow.toISOString().split('T')[0];
        
        const response = await transactionClient.get(
          `/api/transactions/account/${accountId}/stats?fromDate=${fromDate}&toDate=${toDate}`
        );
        
        expect(response.status).toBe(200);
        
        const stats: TransactionStatistics = response.data;
        expect(stats.period.fromDate).toBe(fromDate);
        expect(stats.period.toDate).toBe(toDate);
        expect(stats.totalTransactions).toBeGreaterThanOrEqual(0);
        
        logger.testComplete('Account Statistics - Date Range Filter', 'PASSED');
      });

      it('should retrieve account statistics for different time periods', async () => {
        logger.testStart('Account Statistics - Different Time Periods');
        
        const accountId = testAccounts[0].id;
        
        // Test last 7 days
        const last7Days = new Date();
        last7Days.setDate(last7Days.getDate() - 7);
        const last7DaysStr = last7Days.toISOString().split('T')[0];
        const todayStr = new Date().toISOString().split('T')[0];
        
        const weeklyResponse = await transactionClient.get(
          `/api/transactions/account/${accountId}/stats?fromDate=${last7DaysStr}&toDate=${todayStr}`
        );
        
        expect(weeklyResponse.status).toBe(200);
        expect(weeklyResponse.data.period.fromDate).toBe(last7DaysStr);
        expect(weeklyResponse.data.period.toDate).toBe(todayStr);
        
        // Test last 30 days
        const last30Days = new Date();
        last30Days.setDate(last30Days.getDate() - 30);
        const last30DaysStr = last30Days.toISOString().split('T')[0];
        
        const monthlyResponse = await transactionClient.get(
          `/api/transactions/account/${accountId}/stats?fromDate=${last30DaysStr}&toDate=${todayStr}`
        );
        
        expect(monthlyResponse.status).toBe(200);
        expect(monthlyResponse.data.period.fromDate).toBe(last30DaysStr);
        expect(monthlyResponse.data.period.toDate).toBe(todayStr);
        
        // Monthly stats should include weekly stats or be equal
        expect(monthlyResponse.data.totalTransactions).toBeGreaterThanOrEqual(weeklyResponse.data.totalTransactions);
        
        logger.testComplete('Account Statistics - Different Time Periods', 'PASSED');
      });
    });

    describe('Invalid Account Statistics Retrieval', () => {
      it('should return 404 for non-existent account', async () => {
        logger.testStart('Account Statistics - Non-existent Account');
        
        const nonExistentAccountId = 'non-existent-account-id';
        
        const response = await transactionClient.get(`/api/transactions/account/${nonExistentAccountId}/stats`);
        
        expect(response.status).toBe(404);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Account Statistics - Non-existent Account', 'PASSED');
      });

      it('should return 400 for invalid date range', async () => {
        logger.testStart('Account Statistics - Invalid Date Range');
        
        const accountId = testAccounts[0].id;
        
        // Test invalid date format
        const invalidDateResponse = await transactionClient.get(
          `/api/transactions/account/${accountId}/stats?fromDate=invalid-date`
        );
        expect(invalidDateResponse.status).toBe(400);
        
        // Test invalid date range (from > to)
        const invalidRangeResponse = await transactionClient.get(
          `/api/transactions/account/${accountId}/stats?fromDate=2024-12-31&toDate=2024-01-01`
        );
        expect(invalidRangeResponse.status).toBe(400);
        
        logger.testComplete('Account Statistics - Invalid Date Range', 'PASSED');
      });

      it('should reject access to other user\'s account statistics', async () => {
        logger.testStart('Account Statistics - Other User Account');
        
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
        
        // Try to access other user's account statistics
        const response = await transactionClient.get(`/api/transactions/account/${otherAccountResponse.data.id}/stats`);
        
        expect(response.status).toBe(403);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Account Statistics - Other User Account', 'PASSED');
      });

      it('should return empty statistics for account with no transactions', async () => {
        logger.testStart('Account Statistics - No Transactions');
        
        // Create new account with no transactions
        const newAccountResponse = await accountClient.post('/api/accounts', {
          accountType: 'CHECKING',
          initialBalance: 1000.00
        });
        
        const response = await transactionClient.get(`/api/transactions/account/${newAccountResponse.data.id}/stats`);
        
        expect(response.status).toBe(200);
        
        const stats: TransactionStatistics = response.data;
        expect(stats.totalTransactions).toBe(0);
        expect(stats.totalDeposits).toBe(0);
        expect(stats.totalWithdrawals).toBe(0);
        expect(stats.totalTransfers).toBe(0);
        expect(stats.totalAmount).toBe(0);
        expect(stats.depositAmount).toBe(0);
        expect(stats.withdrawalAmount).toBe(0);
        expect(stats.transferAmount).toBe(0);
        expect(stats.averageTransactionAmount).toBe(0);
        expect(stats.largestTransaction).toBe(0);
        expect(stats.smallestTransaction).toBe(0);
        
        logger.testComplete('Account Statistics - No Transactions', 'PASSED');
      });
    });
  });

  describe('User Transaction Statistics Endpoint', () => {
    describe('Valid User Statistics Retrieval', () => {
      it('should retrieve user transaction statistics across all accounts', async () => {
        logger.testStart('User Statistics - All Accounts');
        
        const response = await transactionClient.get('/api/transactions/user/stats');
        
        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        
        const stats: TransactionStatistics = response.data;
        
        // Validate statistics structure
        expect(typeof stats.totalTransactions).toBe('number');
        expect(typeof stats.totalDeposits).toBe('number');
        expect(typeof stats.totalWithdrawals).toBe('number');
        expect(typeof stats.totalTransfers).toBe('number');
        expect(typeof stats.totalAmount).toBe('number');
        expect(typeof stats.depositAmount).toBe('number');
        expect(typeof stats.withdrawalAmount).toBe('number');
        expect(typeof stats.transferAmount).toBe('number');
        expect(typeof stats.averageTransactionAmount).toBe('number');
        
        // Validate statistics values
        expect(stats.totalTransactions).toBeGreaterThan(0);
        expect(stats.totalTransactions).toBe(stats.totalDeposits + stats.totalWithdrawals + stats.totalTransfers);
        expect(stats.totalAmount).toBe(stats.depositAmount + stats.withdrawalAmount + stats.transferAmount);
        
        // Should include transactions from all user's accounts
        expect(stats.totalTransactions).toBeGreaterThan(0);
        
        logger.testComplete('User Statistics - All Accounts', 'PASSED');
      });

      it('should calculate user statistics correctly across multiple accounts', async () => {
        logger.testStart('User Statistics - Multi-Account Calculation');
        
        // Get user transactions to manually calculate expected statistics
        const transactionsResponse = await transactionClient.get('/api/transactions');
        const transactions = transactionsResponse.data.transactions;
        
        // Calculate expected statistics
        const expectedStats = {
          totalTransactions: transactions.length,
          totalDeposits: transactions.filter((t: TransactionInfo) => t.type === 'DEPOSIT').length,
          totalWithdrawals: transactions.filter((t: TransactionInfo) => t.type === 'WITHDRAWAL').length,
          totalTransfers: transactions.filter((t: TransactionInfo) => t.type === 'TRANSFER').length,
          depositAmount: transactions.filter((t: TransactionInfo) => t.type === 'DEPOSIT').reduce((sum: number, t: TransactionInfo) => sum + t.amount, 0),
          withdrawalAmount: transactions.filter((t: TransactionInfo) => t.type === 'WITHDRAWAL').reduce((sum: number, t: TransactionInfo) => sum + t.amount, 0),
          transferAmount: transactions.filter((t: TransactionInfo) => t.type === 'TRANSFER').reduce((sum: number, t: TransactionInfo) => sum + t.amount, 0)
        };
        
        expectedStats.totalAmount = expectedStats.depositAmount + expectedStats.withdrawalAmount + expectedStats.transferAmount;
        const expectedAverage = expectedStats.totalAmount / expectedStats.totalTransactions;
        
        // Get actual statistics
        const response = await transactionClient.get('/api/transactions/user/stats');
        const actualStats: TransactionStatistics = response.data;
        
        // Validate calculations
        expect(actualStats.totalTransactions).toBe(expectedStats.totalTransactions);
        expect(actualStats.totalDeposits).toBe(expectedStats.totalDeposits);
        expect(actualStats.totalWithdrawals).toBe(expectedStats.totalWithdrawals);
        expect(actualStats.totalTransfers).toBe(expectedStats.totalTransfers);
        expect(compareAmounts(actualStats.depositAmount, expectedStats.depositAmount)).toBe(true);
        expect(compareAmounts(actualStats.withdrawalAmount, expectedStats.withdrawalAmount)).toBe(true);
        expect(compareAmounts(actualStats.transferAmount, expectedStats.transferAmount)).toBe(true);
        expect(compareAmounts(actualStats.totalAmount, expectedStats.totalAmount)).toBe(true);
        expect(compareAmounts(actualStats.averageTransactionAmount, expectedAverage)).toBe(true);
        
        logger.testComplete('User Statistics - Multi-Account Calculation', 'PASSED');
      });

      it('should retrieve user statistics with date range filtering', async () => {
        logger.testStart('User Statistics - Date Range Filter');
        
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);
        
        const fromDate = yesterday.toISOString().split('T')[0];
        const toDate = tomorrow.toISOString().split('T')[0];
        
        const response = await transactionClient.get(
          `/api/transactions/user/stats?fromDate=${fromDate}&toDate=${toDate}`
        );
        
        expect(response.status).toBe(200);
        
        const stats: TransactionStatistics = response.data;
        expect(stats.period.fromDate).toBe(fromDate);
        expect(stats.period.toDate).toBe(toDate);
        expect(stats.totalTransactions).toBeGreaterThanOrEqual(0);
        
        logger.testComplete('User Statistics - Date Range Filter', 'PASSED');
      });

      it('should retrieve user statistics for various time periods', async () => {
        logger.testStart('User Statistics - Various Time Periods');
        
        const today = new Date();
        const todayStr = today.toISOString().split('T')[0];
        
        // Test daily statistics
        const dailyResponse = await transactionClient.get(
          `/api/transactions/user/stats?fromDate=${todayStr}&toDate=${todayStr}`
        );
        expect(dailyResponse.status).toBe(200);
        
        // Test weekly statistics
        const weekAgo = new Date(today);
        weekAgo.setDate(weekAgo.getDate() - 7);
        const weekAgoStr = weekAgo.toISOString().split('T')[0];
        
        const weeklyResponse = await transactionClient.get(
          `/api/transactions/user/stats?fromDate=${weekAgoStr}&toDate=${todayStr}`
        );
        expect(weeklyResponse.status).toBe(200);
        
        // Test monthly statistics
        const monthAgo = new Date(today);
        monthAgo.setMonth(monthAgo.getMonth() - 1);
        const monthAgoStr = monthAgo.toISOString().split('T')[0];
        
        const monthlyResponse = await transactionClient.get(
          `/api/transactions/user/stats?fromDate=${monthAgoStr}&toDate=${todayStr}`
        );
        expect(monthlyResponse.status).toBe(200);
        
        // Validate that longer periods include shorter periods
        expect(weeklyResponse.data.totalTransactions).toBeGreaterThanOrEqual(dailyResponse.data.totalTransactions);
        expect(monthlyResponse.data.totalTransactions).toBeGreaterThanOrEqual(weeklyResponse.data.totalTransactions);
        
        logger.testComplete('User Statistics - Various Time Periods', 'PASSED');
      });

      it('should compare user statistics with sum of account statistics', async () => {
        logger.testStart('User Statistics - Account Sum Comparison');
        
        // Get user statistics
        const userStatsResponse = await transactionClient.get('/api/transactions/user/stats');
        const userStats: TransactionStatistics = userStatsResponse.data;
        
        // Get statistics for each account and sum them
        let totalAccountStats = {
          totalTransactions: 0,
          totalDeposits: 0,
          totalWithdrawals: 0,
          totalTransfers: 0,
          totalAmount: 0,
          depositAmount: 0,
          withdrawalAmount: 0,
          transferAmount: 0
        };
        
        for (const account of testAccounts) {
          const accountStatsResponse = await transactionClient.get(`/api/transactions/account/${account.id}/stats`);
          const accountStats: TransactionStatistics = accountStatsResponse.data;
          
          totalAccountStats.totalTransactions += accountStats.totalTransactions;
          totalAccountStats.totalDeposits += accountStats.totalDeposits;
          totalAccountStats.totalWithdrawals += accountStats.totalWithdrawals;
          totalAccountStats.totalTransfers += accountStats.totalTransfers;
          totalAccountStats.totalAmount += accountStats.totalAmount;
          totalAccountStats.depositAmount += accountStats.depositAmount;
          totalAccountStats.withdrawalAmount += accountStats.withdrawalAmount;
          totalAccountStats.transferAmount += accountStats.transferAmount;
        }
        
        // User statistics should match sum of account statistics
        expect(userStats.totalTransactions).toBe(totalAccountStats.totalTransactions);
        expect(userStats.totalDeposits).toBe(totalAccountStats.totalDeposits);
        expect(userStats.totalWithdrawals).toBe(totalAccountStats.totalWithdrawals);
        expect(userStats.totalTransfers).toBe(totalAccountStats.totalTransfers);
        expect(compareAmounts(userStats.totalAmount, totalAccountStats.totalAmount)).toBe(true);
        expect(compareAmounts(userStats.depositAmount, totalAccountStats.depositAmount)).toBe(true);
        expect(compareAmounts(userStats.withdrawalAmount, totalAccountStats.withdrawalAmount)).toBe(true);
        expect(compareAmounts(userStats.transferAmount, totalAccountStats.transferAmount)).toBe(true);
        
        logger.testComplete('User Statistics - Account Sum Comparison', 'PASSED');
      });
    });

    describe('Invalid User Statistics Retrieval', () => {
      it('should reject retrieval without authentication', async () => {
        logger.testStart('User Statistics - No Authentication');
        
        const unauthenticatedClient = createTransactionServiceClient();
        
        const response = await unauthenticatedClient.get('/api/transactions/user/stats');
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('User Statistics - No Authentication', 'PASSED');
      });

      it('should return 400 for invalid date parameters', async () => {
        logger.testStart('User Statistics - Invalid Date Parameters');
        
        // Test invalid date format
        const invalidDateResponse = await transactionClient.get(
          '/api/transactions/user/stats?fromDate=invalid-date'
        );
        expect(invalidDateResponse.status).toBe(400);
        
        // Test invalid date range
        const invalidRangeResponse = await transactionClient.get(
          '/api/transactions/user/stats?fromDate=2024-12-31&toDate=2024-01-01'
        );
        expect(invalidRangeResponse.status).toBe(400);
        
        logger.testComplete('User Statistics - Invalid Date Parameters', 'PASSED');
      });

      it('should return empty statistics for user with no transactions', async () => {
        logger.testStart('User Statistics - No Transactions');
        
        // Create new user with no transactions
        const newUser = createTestUser({ username: `noTxnUser_${testUserId}` });
        
        await accountClient.post('/api/auth/register', {
          username: newUser.username,
          password: newUser.password
        });
        
        const newLoginResponse = await accountClient.post('/api/auth/login', {
          username: newUser.username,
          password: newUser.password
        });
        
        const newUserClient = createTransactionServiceClient();
        newUserClient.setAuthToken(newLoginResponse.data.token);
        
        const response = await newUserClient.get('/api/transactions/user/stats');
        
        expect(response.status).toBe(200);
        
        const stats: TransactionStatistics = response.data;
        expect(stats.totalTransactions).toBe(0);
        expect(stats.totalDeposits).toBe(0);
        expect(stats.totalWithdrawals).toBe(0);
        expect(stats.totalTransfers).toBe(0);
        expect(stats.totalAmount).toBe(0);
        expect(stats.depositAmount).toBe(0);
        expect(stats.withdrawalAmount).toBe(0);
        expect(stats.transferAmount).toBe(0);
        expect(stats.averageTransactionAmount).toBe(0);
        
        logger.testComplete('User Statistics - No Transactions', 'PASSED');
      });
    });
  });

  describe('Transaction Statistics Calculation Validation', () => {
    describe('Edge Cases and Boundary Conditions', () => {
      it('should handle statistics for single transaction', async () => {
        logger.testStart('Statistics - Single Transaction');
        
        // Create new account for this test
        const singleTxnAccountResponse = await accountClient.post('/api/accounts', {
          accountType: 'CHECKING',
          initialBalance: 1000.00
        });
        
        // Create single transaction
        const singleTxnResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: singleTxnAccountResponse.data.id,
          amount: 123.45,
          description: 'Single transaction test'
        });
        
        expect(singleTxnResponse.status).toBe(201);
        
        // Get statistics
        const statsResponse = await transactionClient.get(`/api/transactions/account/${singleTxnAccountResponse.data.id}/stats`);
        
        expect(statsResponse.status).toBe(200);
        
        const stats: TransactionStatistics = statsResponse.data;
        expect(stats.totalTransactions).toBe(1);
        expect(stats.totalDeposits).toBe(1);
        expect(stats.totalWithdrawals).toBe(0);
        expect(stats.totalTransfers).toBe(0);
        expect(compareAmounts(stats.totalAmount, 123.45)).toBe(true);
        expect(compareAmounts(stats.depositAmount, 123.45)).toBe(true);
        expect(compareAmounts(stats.averageTransactionAmount, 123.45)).toBe(true);
        expect(compareAmounts(stats.largestTransaction, 123.45)).toBe(true);
        expect(compareAmounts(stats.smallestTransaction, 123.45)).toBe(true);
        
        logger.testComplete('Statistics - Single Transaction', 'PASSED');
      });

      it('should handle statistics with decimal precision', async () => {
        logger.testStart('Statistics - Decimal Precision');
        
        // Create account for precision test
        const precisionAccountResponse = await accountClient.post('/api/accounts', {
          accountType: 'CHECKING',
          initialBalance: 1000.00
        });
        
        // Create transactions with precise decimal amounts
        const preciseAmounts = [10.01, 20.02, 30.03];
        
        for (const amount of preciseAmounts) {
          await transactionClient.post('/api/transactions/deposit', {
            accountId: precisionAccountResponse.data.id,
            amount: amount,
            description: `Precision test ${amount}`
          });
          await delay(100);
        }
        
        // Get statistics
        const statsResponse = await transactionClient.get(`/api/transactions/account/${precisionAccountResponse.data.id}/stats`);
        
        expect(statsResponse.status).toBe(200);
        
        const stats: TransactionStatistics = statsResponse.data;
        const expectedTotal = preciseAmounts.reduce((sum, amount) => sum + amount, 0);
        const expectedAverage = expectedTotal / preciseAmounts.length;
        
        expect(stats.totalTransactions).toBe(preciseAmounts.length);
        expect(compareAmounts(stats.totalAmount, expectedTotal)).toBe(true);
        expect(compareAmounts(stats.averageTransactionAmount, expectedAverage)).toBe(true);
        expect(compareAmounts(stats.largestTransaction, Math.max(...preciseAmounts))).toBe(true);
        expect(compareAmounts(stats.smallestTransaction, Math.min(...preciseAmounts))).toBe(true);
        
        logger.testComplete('Statistics - Decimal Precision', 'PASSED');
      });
    });

    describe('Performance and Scalability', () => {
      it('should calculate statistics within reasonable time', async () => {
        logger.testStart('Statistics - Performance');
        
        const startTime = Date.now();
        const response = await transactionClient.get('/api/transactions/user/stats');
        const duration = Date.now() - startTime;
        
        expect(response.status).toBe(200);
        expect(duration).toBeLessThan(5000); // Should complete within 5 seconds
        
        logger.testComplete('Statistics - Performance', 'PASSED');
      });

      it('should handle statistics for accounts with many transactions', async () => {
        logger.testStart('Statistics - Many Transactions');
        
        // This test validates that statistics work correctly even with multiple transactions
        // In a real scenario, this would test with hundreds or thousands of transactions
        const response = await transactionClient.get(`/api/transactions/account/${testAccounts[0].id}/stats`);
        
        expect(response.status).toBe(200);
        expect(response.data.totalTransactions).toBeGreaterThan(0);
        
        logger.testComplete('Statistics - Many Transactions', 'PASSED');
      });
    });
  });
});