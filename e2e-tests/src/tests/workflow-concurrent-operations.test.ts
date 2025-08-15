import { describe, test, expect, beforeAll, afterAll, beforeEach, afterEach } from '@jest/globals';
import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { createTestUser, generateTestId, waitForCondition, delay, compareAmounts } from '../utils/test-helpers';
import { logger } from '../utils/logger';
import { testConfig } from '../config/test-config';
import { TestUser, AuthResponse, AccountInfo, TransactionInfo } from '../types';

/**
 * Concurrent Operation Tests
 * Tests concurrent user registration, account creation, and transaction processing with data consistency validation
 * Requirements: 5.7
 */
describe('Concurrent Operation Tests', () => {
  let accountClient: any;
  let transactionClient: any;
  let testUsers: TestUser[] = [];
  let createdAccounts: AccountInfo[] = [];
  let testRunId: string;

  beforeAll(async () => {
    testRunId = generateTestId('concurrent-ops');
    logger.info(`🚀 Starting Concurrent Operation Tests - Run ID: ${testRunId}`);

    // Initialize service clients
    accountClient = createAccountServiceClient();
    transactionClient = createTransactionServiceClient();

    // Wait for services to be ready
    const accountReady = await accountClient.waitForService();
    const transactionReady = await transactionClient.waitForService();

    if (!accountReady || !transactionReady) {
      throw new Error('Services not ready for testing');
    }

    logger.info('✅ Services are ready for concurrent operation testing');
  });

  afterAll(async () => {
    logger.info(`🧹 Cleaning up concurrent operation test data - Run ID: ${testRunId}`);
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

  describe('Concurrent User Registration and Account Creation Tests', () => {
    test('should handle concurrent user registrations without conflicts', async () => {
      logger.testStart('Concurrent user registrations without conflicts');

      const concurrentUserCount = 5;
      const users = Array.from({ length: concurrentUserCount }, (_, index) => 
        createTestUser({
          username: `concurrent_reg_user_${testRunId}_${index}`,
          email: `concurrent_reg_user_${testRunId}_${index}@test.com`
        })
      );

      logger.info(`🔄 Registering ${concurrentUserCount} users concurrently`);

      // Register all users concurrently
      const registrationPromises = users.map(async (user, index) => {
        try {
          const startTime = Date.now();
          const response = await accountClient.post('/api/auth/register', {
            username: user.username,
            password: user.password,
            email: user.email
          });
          const duration = Date.now() - startTime;
          
          return {
            success: true,
            user,
            response,
            duration,
            index
          };
        } catch (error) {
          return {
            success: false,
            user,
            error,
            index
          };
        }
      });

      const registrationResults = await Promise.all(registrationPromises);

      // Verify all registrations succeeded
      const successfulRegistrations = registrationResults.filter(r => r.success);
      const failedRegistrations = registrationResults.filter(r => !r.success);

      logger.info(`Registration results - Successful: ${successfulRegistrations.length}, Failed: ${failedRegistrations.length}`);

      expect(successfulRegistrations.length).toBe(concurrentUserCount);
      expect(failedRegistrations.length).toBe(0);

      // Verify each registration response
      successfulRegistrations.forEach((result, index) => {
        expect((result as any).response.status).toBe(201);
        expect((result as any).response.data).toHaveProperty('message');
        logger.info(`✅ User ${index + 1} registered successfully in ${(result as any).duration}ms`);
      });

      // Verify all users can login after concurrent registration
      logger.info('🔐 Verifying all users can login after concurrent registration');
      const loginPromises = users.map(async (user) => {
        try {
          const response = await accountClient.post('/api/auth/login', {
            username: user.username,
            password: user.password
          });
          return { success: true, user, response };
        } catch (error) {
          return { success: false, user, error };
        }
      });

      const loginResults = await Promise.all(loginPromises);
      const successfulLogins = loginResults.filter(r => r.success);

      expect(successfulLogins.length).toBe(concurrentUserCount);

      logger.info('✅ All concurrently registered users can login successfully');
      logger.testComplete('Concurrent user registrations without conflicts', 'PASSED');

      testUsers.push(...users);
    }, 60000);

    test('should handle concurrent account creation for same user', async () => {
      const testUser = createTestUser({
        username: `concurrent_account_user_${testRunId}`
      });

      logger.testStart('Concurrent account creation for same user');

      // Register and login user
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

      // Create multiple accounts concurrently for the same user
      const accountTypes = ['CHECKING', 'SAVINGS', 'CHECKING', 'SAVINGS', 'CHECKING'] as const;
      const initialBalances = [1000.00, 2000.00, 500.00, 1500.00, 750.00];

      logger.info(`🏦 Creating ${accountTypes.length} accounts concurrently for same user`);

      const accountCreationPromises = accountTypes.map(async (accountType, index) => {
        try {
          const startTime = Date.now();
          const response = await accountClient.post('/api/accounts', {
            accountType,
            initialBalance: initialBalances[index],
            currency: 'USD'
          });
          const duration = Date.now() - startTime;

          return {
            success: true,
            accountType,
            initialBalance: initialBalances[index],
            response,
            duration,
            index
          };
        } catch (error) {
          return {
            success: false,
            accountType,
            initialBalance: initialBalances[index],
            error,
            index
          };
        }
      });

      const accountResults = await Promise.all(accountCreationPromises);

      // Verify all account creations succeeded
      const successfulAccounts = accountResults.filter(r => r.success);
      const failedAccounts = accountResults.filter(r => !r.success);

      logger.info(`Account creation results - Successful: ${successfulAccounts.length}, Failed: ${failedAccounts.length}`);

      expect(successfulAccounts.length).toBe(accountTypes.length);
      expect(failedAccounts.length).toBe(0);

      // Verify each account was created correctly
      const createdAccountIds: string[] = [];
      successfulAccounts.forEach((result, index) => {
        const accountData = (result as any).response.data;
        expect(accountData).toHaveProperty('id');
        expect(accountData.accountType).toBe((result as any).accountType);
        expect(accountData.balance).toBe((result as any).initialBalance);
        expect(accountData.status).toBe('ACTIVE');

        createdAccountIds.push(accountData.id);
        createdAccounts.push(accountData);
        logger.info(`✅ Account ${index + 1} (${(result as any).accountType}) created successfully in ${(result as any).duration}ms`);
      });

      // Verify all accounts have unique IDs
      const uniqueAccountIds = [...new Set(createdAccountIds)];
      expect(uniqueAccountIds.length).toBe(createdAccountIds.length);

      // Verify user can retrieve all created accounts
      logger.info('📋 Verifying user can retrieve all created accounts');
      const accountsListResponse = await accountClient.get('/api/accounts');
      
      expect(accountsListResponse.status).toBe(200);
      expect(accountsListResponse.data).toHaveProperty('content');
      expect(accountsListResponse.data.content.length).toBeGreaterThanOrEqual(accountTypes.length);

      // Verify all created accounts appear in the list
      const retrievedAccountIds = accountsListResponse.data.content.map((acc: AccountInfo) => acc.id);
      createdAccountIds.forEach(id => {
        expect(retrievedAccountIds).toContain(id);
      });

      logger.info('✅ All concurrently created accounts are retrievable');
      logger.testComplete('Concurrent account creation for same user', 'PASSED');

      testUsers.push(testUser);
    }, 60000);

    test('should handle concurrent user registration and account creation workflow', async () => {
      logger.testStart('Concurrent user registration and account creation workflow');

      const concurrentUserCount = 3;
      const users = Array.from({ length: concurrentUserCount }, (_, index) => 
        createTestUser({
          username: `concurrent_workflow_user_${testRunId}_${index}`,
          accounts: [
            {
              accountType: 'CHECKING',
              initialBalance: 1000.00 + (index * 100),
              currency: 'USD'
            }
          ]
        })
      );

      logger.info(`🔄 Running complete registration and account creation workflow for ${concurrentUserCount} users concurrently`);

      // Run complete workflow for each user concurrently
      const workflowPromises = users.map(async (user, index) => {
        try {
          const workflowStartTime = Date.now();

          // Step 1: Register user
          const registrationResponse = await accountClient.post('/api/auth/register', {
            username: user.username,
            password: user.password,
            email: user.email
          });

          if (registrationResponse.status !== 201) {
            throw new Error(`Registration failed for user ${index}`);
          }

          // Step 2: Login user
          const loginResponse = await accountClient.post('/api/auth/login', {
            username: user.username,
            password: user.password
          });

          if (loginResponse.status !== 200) {
            throw new Error(`Login failed for user ${index}`);
          }

          const authData: AuthResponse = loginResponse.data;

          // Create a new client instance for this user to avoid token conflicts
          const userAccountClient = createAccountServiceClient();
          userAccountClient.setAuthToken(authData.accessToken);

          // Step 3: Create account
          const accountData = user.accounts![0];
          const accountResponse = await userAccountClient.post('/api/accounts', {
            accountType: accountData.accountType,
            initialBalance: accountData.initialBalance,
            currency: accountData.currency
          });

          if (accountResponse.status !== 201) {
            throw new Error(`Account creation failed for user ${index}`);
          }

          const workflowDuration = Date.now() - workflowStartTime;

          return {
            success: true,
            user,
            account: accountResponse.data,
            duration: workflowDuration,
            index
          };
        } catch (error) {
          return {
            success: false,
            user,
            error,
            index
          };
        }
      });

      const workflowResults = await Promise.all(workflowPromises);

      // Verify all workflows succeeded
      const successfulWorkflows = workflowResults.filter(r => r.success);
      const failedWorkflows = workflowResults.filter(r => !r.success);

      logger.info(`Workflow results - Successful: ${successfulWorkflows.length}, Failed: ${failedWorkflows.length}`);

      expect(successfulWorkflows.length).toBe(concurrentUserCount);
      expect(failedWorkflows.length).toBe(0);

      // Verify each workflow result
      successfulWorkflows.forEach((result, index) => {
        const account = (result as any).account;
        expect(account).toHaveProperty('id');
        expect(account.accountType).toBe('CHECKING');
        expect(account.balance).toBe(1000.00 + (index * 100));
        expect(account.status).toBe('ACTIVE');

        createdAccounts.push(account);
        logger.info(`✅ User ${index + 1} complete workflow finished in ${(result as any).duration}ms`);
      });

      // Verify all accounts have unique IDs
      const accountIds = successfulWorkflows.map(r => (r as any).account.id);
      const uniqueAccountIds = [...new Set(accountIds)];
      expect(uniqueAccountIds.length).toBe(accountIds.length);

      logger.info('✅ All concurrent user registration and account creation workflows completed successfully');
      logger.testComplete('Concurrent user registration and account creation workflow', 'PASSED');

      testUsers.push(...users);
    }, 90000);
  });

  describe('Concurrent Transaction Processing Tests', () => {
    test('should handle concurrent transaction processing with data consistency validation', async () => {
      const testUser = createTestUser({
        username: `concurrent_tx_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 2000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Concurrent transaction processing with data consistency validation');

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
        initialBalance: 2000.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Define concurrent transactions
      const concurrentTransactions = [
        { type: 'deposit', amount: 100.00, description: 'Concurrent deposit 1' },
        { type: 'deposit', amount: 150.00, description: 'Concurrent deposit 2' },
        { type: 'withdraw', amount: 75.00, description: 'Concurrent withdrawal 1' },
        { type: 'deposit', amount: 200.00, description: 'Concurrent deposit 3' },
        { type: 'withdraw', amount: 50.00, description: 'Concurrent withdrawal 2' },
        { type: 'deposit', amount: 125.00, description: 'Concurrent deposit 4' },
        { type: 'withdraw', amount: 100.00, description: 'Concurrent withdrawal 3' }
      ];

      logger.info(`💰 Processing ${concurrentTransactions.length} transactions concurrently`);

      // Process all transactions concurrently
      const transactionPromises = concurrentTransactions.map(async (tx, index) => {
        try {
          const startTime = Date.now();
          const endpoint = tx.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
          
          const response = await transactionClient.post(endpoint, {
            accountId: account.id,
            amount: tx.amount,
            currency: 'USD',
            description: tx.description
          });
          
          const duration = Date.now() - startTime;

          return {
            success: true,
            transaction: response.data,
            type: tx.type,
            amount: tx.amount,
            duration,
            index
          };
        } catch (error) {
          return {
            success: false,
            error,
            type: tx.type,
            amount: tx.amount,
            index
          };
        }
      });

      const transactionResults = await Promise.all(transactionPromises);

      // Analyze results
      const successfulTransactions = transactionResults.filter(r => r.success);
      const failedTransactions = transactionResults.filter(r => !r.success);

      logger.info(`Transaction results - Successful: ${successfulTransactions.length}, Failed: ${failedTransactions.length}`);

      // All transactions should succeed since we have sufficient funds
      expect(successfulTransactions.length).toBe(concurrentTransactions.length);
      expect(failedTransactions.length).toBe(0);

      // Calculate expected balance
      let expectedBalance = 2000.00; // Initial balance
      for (const result of successfulTransactions) {
        if (result.success) {
          expectedBalance += (result as any).type === 'deposit' ? (result as any).amount : -(result as any).amount;
        }
      }

      // Wait for all transactions to be fully processed
      await delay(3000);

      // Verify final balance consistency
      logger.info('🔍 Verifying final balance consistency');
      const finalBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const actualBalance = finalBalanceResponse.data.balance;

      expect(actualBalance).toBe(expectedBalance);
      logger.info(`✅ Balance consistency verified - Expected: ${expectedBalance}, Actual: ${actualBalance}`);

      // Verify transaction history consistency
      logger.info('📋 Verifying transaction history consistency');
      const historyResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const transactionHistory = historyResponse.data.content;

      // All transactions should appear in history
      expect(transactionHistory.length).toBe(concurrentTransactions.length);

      // Verify all transactions are completed
      const completedTransactions = transactionHistory.filter((tx: TransactionInfo) => tx.status === 'COMPLETED');
      expect(completedTransactions.length).toBe(concurrentTransactions.length);

      // Verify no duplicate transactions
      const transactionIds = transactionHistory.map((tx: TransactionInfo) => tx.id);
      const uniqueTransactionIds = [...new Set(transactionIds)];
      expect(transactionIds.length).toBe(uniqueTransactionIds.length);

      // Verify transaction amounts match
      const totalDeposits = transactionHistory
        .filter((tx: TransactionInfo) => tx.type === 'DEPOSIT')
        .reduce((sum: number, tx: TransactionInfo) => sum + tx.amount, 0);
      
      const totalWithdrawals = transactionHistory
        .filter((tx: TransactionInfo) => tx.type === 'WITHDRAWAL')
        .reduce((sum: number, tx: TransactionInfo) => sum + tx.amount, 0);

      const expectedDeposits = concurrentTransactions
        .filter(tx => tx.type === 'deposit')
        .reduce((sum, tx) => sum + tx.amount, 0);
      
      const expectedWithdrawals = concurrentTransactions
        .filter(tx => tx.type === 'withdraw')
        .reduce((sum, tx) => sum + tx.amount, 0);

      expect(totalDeposits).toBe(expectedDeposits);
      expect(totalWithdrawals).toBe(expectedWithdrawals);

      logger.info('✅ Transaction history consistency verified');
      logger.testComplete('Concurrent transaction processing with data consistency validation', 'PASSED');

      testUsers.push(testUser);
    }, 90000);

    test('should handle concurrent transactions with insufficient funds correctly', async () => {
      const testUser = createTestUser({
        username: `concurrent_insufficient_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 200.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Concurrent transactions with insufficient funds');

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

      // Define concurrent withdrawals that exceed available funds
      const concurrentWithdrawals = [
        { amount: 100.00, description: 'Concurrent withdrawal 1' },
        { amount: 80.00, description: 'Concurrent withdrawal 2' },
        { amount: 90.00, description: 'Concurrent withdrawal 3' },
        { amount: 70.00, description: 'Concurrent withdrawal 4' },
        { amount: 60.00, description: 'Concurrent withdrawal 5' }
      ];

      // Total withdrawal attempts: 400.00, but only 200.00 available
      const totalWithdrawalAttempts = concurrentWithdrawals.reduce((sum, tx) => sum + tx.amount, 0);
      logger.info(`💸 Attempting ${concurrentWithdrawals.length} concurrent withdrawals totaling ${totalWithdrawalAttempts} from account with 200.00`);

      // Process all withdrawals concurrently
      const withdrawalPromises = concurrentWithdrawals.map(async (tx, index) => {
        try {
          const response = await transactionClient.post('/api/transactions/withdraw', {
            accountId: account.id,
            amount: tx.amount,
            currency: 'USD',
            description: tx.description
          });

          return {
            success: true,
            transaction: response.data,
            amount: tx.amount,
            index
          };
        } catch (error) {
          return {
            success: false,
            error,
            amount: tx.amount,
            index
          };
        }
      });

      const withdrawalResults = await Promise.all(withdrawalPromises);

      // Analyze results
      const successfulWithdrawals = withdrawalResults.filter(r => r.success);
      const failedWithdrawals = withdrawalResults.filter(r => !r.success);

      logger.info(`Withdrawal results - Successful: ${successfulWithdrawals.length}, Failed: ${failedWithdrawals.length}`);

      // Some withdrawals should succeed, others should fail due to insufficient funds
      expect(successfulWithdrawals.length).toBeGreaterThan(0);
      expect(failedWithdrawals.length).toBeGreaterThan(0);
      expect(successfulWithdrawals.length + failedWithdrawals.length).toBe(concurrentWithdrawals.length);

      // Calculate total successful withdrawal amount
      const totalSuccessfulWithdrawals = successfulWithdrawals.reduce(
        (sum, result) => sum + (result as any).amount, 0
      );

      // Verify total successful withdrawals don't exceed initial balance
      expect(totalSuccessfulWithdrawals).toBeLessThanOrEqual(200.00);

      // Wait for all transactions to be processed
      await delay(2000);

      // Verify final balance
      logger.info('🔍 Verifying final balance after concurrent insufficient funds scenario');
      const finalBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const finalBalance = finalBalanceResponse.data.balance;
      const expectedFinalBalance = 200.00 - totalSuccessfulWithdrawals;

      expect(finalBalance).toBe(expectedFinalBalance);
      expect(finalBalance).toBeGreaterThanOrEqual(0);

      logger.info(`✅ Final balance verified - Expected: ${expectedFinalBalance}, Actual: ${finalBalance}`);

      // Verify only successful transactions appear in history
      const historyResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const completedTransactions = historyResponse.data.content.filter(
        (tx: TransactionInfo) => tx.status === 'COMPLETED'
      );

      expect(completedTransactions.length).toBe(successfulWithdrawals.length);

      // Verify no failed transactions in history
      const failedTransactionsInHistory = historyResponse.data.content.filter(
        (tx: TransactionInfo) => tx.status === 'FAILED'
      );
      expect(failedTransactionsInHistory.length).toBe(0);

      logger.info('✅ Concurrent transactions with insufficient funds handled correctly');
      logger.testComplete('Concurrent transactions with insufficient funds', 'PASSED');

      testUsers.push(testUser);
    }, 60000);
  });

  describe('Race Condition Tests', () => {
    test('should handle race conditions with proper locking and synchronization validation', async () => {
      const testUser = createTestUser({
        username: `race_condition_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Race conditions with proper locking and synchronization validation');

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

      // Test race condition scenario: Multiple rapid transactions on same account
      logger.info('🏁 Testing race condition with rapid concurrent transactions');
      
      const raceConditionTransactions = Array.from({ length: 10 }, (_, index) => ({
        type: index % 2 === 0 ? 'deposit' : 'withdraw',
        amount: 50.00,
        description: `Race condition test ${index + 1}`
      }));

      // Execute all transactions as quickly as possible to create race conditions
      const racePromises = raceConditionTransactions.map(async (tx, index) => {
        try {
          const endpoint = tx.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
          
          // Add minimal random delay to increase chance of race conditions
          await delay(Math.random() * 10);
          
          const response = await transactionClient.post(endpoint, {
            accountId: account.id,
            amount: tx.amount,
            currency: 'USD',
            description: tx.description
          });

          return {
            success: true,
            transaction: response.data,
            type: tx.type,
            amount: tx.amount,
            index
          };
        } catch (error) {
          return {
            success: false,
            error,
            type: tx.type,
            amount: tx.amount,
            index
          };
        }
      });

      const raceResults = await Promise.all(racePromises);

      // Analyze race condition results
      const successfulRaceTransactions = raceResults.filter(r => r.success);
      const failedRaceTransactions = raceResults.filter(r => !r.success);

      logger.info(`Race condition results - Successful: ${successfulRaceTransactions.length}, Failed: ${failedRaceTransactions.length}`);

      // Calculate expected balance from successful transactions
      let expectedBalance = 1000.00;
      for (const result of successfulRaceTransactions) {
        if (result.success) {
          expectedBalance += (result as any).type === 'deposit' ? (result as any).amount : -(result as any).amount;
        }
      }

      // Wait for all transactions to be fully processed
      await delay(3000);

      // Verify data consistency after race conditions
      logger.info('🔍 Verifying data consistency after race conditions');
      const balanceAfterRace = await accountClient.get(`/api/accounts/${account.id}`);
      const actualBalance = balanceAfterRace.data.balance;

      expect(actualBalance).toBe(expectedBalance);
      logger.info(`✅ Balance consistency maintained - Expected: ${expectedBalance}, Actual: ${actualBalance}`);

      // Verify transaction history integrity
      const historyAfterRace = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const completedRaceTransactions = historyAfterRace.data.content.filter(
        (tx: TransactionInfo) => tx.status === 'COMPLETED'
      );

      expect(completedRaceTransactions.length).toBe(successfulRaceTransactions.length);

      // Verify no duplicate transactions (critical for race condition testing)
      const transactionIds = completedRaceTransactions.map((tx: TransactionInfo) => tx.id);
      const uniqueTransactionIds = [...new Set(transactionIds)];
      expect(transactionIds.length).toBe(uniqueTransactionIds.length);

      // Verify balance calculation from transaction history matches account balance
      let calculatedBalance = 1000.00;
      for (const tx of completedRaceTransactions) {
        if (tx.type === 'DEPOSIT') {
          calculatedBalance += tx.amount;
        } else if (tx.type === 'WITHDRAWAL') {
          calculatedBalance -= tx.amount;
        }
      }

      expect(calculatedBalance).toBe(actualBalance);

      logger.info('✅ Race condition handling verified - no data corruption detected');
      logger.testComplete('Race conditions with proper locking and synchronization validation', 'PASSED');

      testUsers.push(testUser);
    }, 90000);

    test('should handle concurrent transfers between accounts with proper synchronization', async () => {
      const testUser = createTestUser({
        username: `concurrent_transfer_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 1000.00,
            currency: 'USD'
          },
          {
            accountType: 'SAVINGS',
            initialBalance: 500.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Concurrent transfers between accounts with proper synchronization');

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

      // Create checking account
      const checkingAccountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 1000.00,
        currency: 'USD'
      });

      const checkingAccount: AccountInfo = checkingAccountResponse.data;
      createdAccounts.push(checkingAccount);

      // Create savings account
      const savingsAccountResponse = await accountClient.post('/api/accounts', {
        accountType: 'SAVINGS',
        initialBalance: 500.00,
        currency: 'USD'
      });

      const savingsAccount: AccountInfo = savingsAccountResponse.data;
      createdAccounts.push(savingsAccount);

      // Define concurrent transfers in both directions
      const concurrentTransfers = [
        { from: checkingAccount.id, to: savingsAccount.id, amount: 100.00, description: 'Transfer 1: Checking to Savings' },
        { from: savingsAccount.id, to: checkingAccount.id, amount: 75.00, description: 'Transfer 2: Savings to Checking' },
        { from: checkingAccount.id, to: savingsAccount.id, amount: 150.00, description: 'Transfer 3: Checking to Savings' },
        { from: savingsAccount.id, to: checkingAccount.id, amount: 50.00, description: 'Transfer 4: Savings to Checking' },
        { from: checkingAccount.id, to: savingsAccount.id, amount: 200.00, description: 'Transfer 5: Checking to Savings' }
      ];

      logger.info(`💱 Processing ${concurrentTransfers.length} concurrent transfers between accounts`);

      // Execute all transfers concurrently
      const transferPromises = concurrentTransfers.map(async (transfer, index) => {
        try {
          const response = await transactionClient.post('/api/transactions/transfer', {
            fromAccountId: transfer.from,
            toAccountId: transfer.to,
            amount: transfer.amount,
            currency: 'USD',
            description: transfer.description
          });

          return {
            success: true,
            transfer: response.data,
            fromAccountId: transfer.from,
            toAccountId: transfer.to,
            amount: transfer.amount,
            index
          };
        } catch (error) {
          return {
            success: false,
            error,
            fromAccountId: transfer.from,
            toAccountId: transfer.to,
            amount: transfer.amount,
            index
          };
        }
      });

      const transferResults = await Promise.all(transferPromises);

      // Analyze transfer results
      const successfulTransfers = transferResults.filter(r => r.success);
      const failedTransfers = transferResults.filter(r => !r.success);

      logger.info(`Transfer results - Successful: ${successfulTransfers.length}, Failed: ${failedTransfers.length}`);

      // Calculate expected balances
      let expectedCheckingBalance = 1000.00;
      let expectedSavingsBalance = 500.00;

      for (const result of successfulTransfers) {
        if (result.success) {
          const transfer = result as any;
          if (transfer.fromAccountId === checkingAccount.id) {
            expectedCheckingBalance -= transfer.amount;
            expectedSavingsBalance += transfer.amount;
          } else {
            expectedSavingsBalance -= transfer.amount;
            expectedCheckingBalance += transfer.amount;
          }
        }
      }

      // Wait for all transfers to be processed
      await delay(3000);

      // Verify final balances
      logger.info('🔍 Verifying final balances after concurrent transfers');
      const finalCheckingResponse = await accountClient.get(`/api/accounts/${checkingAccount.id}`);
      const finalSavingsResponse = await accountClient.get(`/api/accounts/${savingsAccount.id}`);

      const actualCheckingBalance = finalCheckingResponse.data.balance;
      const actualSavingsBalance = finalSavingsResponse.data.balance;

      expect(actualCheckingBalance).toBe(expectedCheckingBalance);
      expect(actualSavingsBalance).toBe(expectedSavingsBalance);

      logger.info(`✅ Checking account balance - Expected: ${expectedCheckingBalance}, Actual: ${actualCheckingBalance}`);
      logger.info(`✅ Savings account balance - Expected: ${expectedSavingsBalance}, Actual: ${actualSavingsBalance}`);

      // Verify total money in system is conserved
      const totalInitialBalance = 1000.00 + 500.00;
      const totalFinalBalance = actualCheckingBalance + actualSavingsBalance;
      expect(totalFinalBalance).toBe(totalInitialBalance);

      logger.info(`✅ Total system balance conserved - Initial: ${totalInitialBalance}, Final: ${totalFinalBalance}`);

      // Verify transaction histories
      const checkingHistoryResponse = await transactionClient.get(`/api/transactions/account/${checkingAccount.id}`);
      const savingsHistoryResponse = await transactionClient.get(`/api/transactions/account/${savingsAccount.id}`);

      const checkingTransfers = checkingHistoryResponse.data.content.filter(
        (tx: TransactionInfo) => tx.type === 'TRANSFER' && tx.status === 'COMPLETED'
      );
      const savingsTransfers = savingsHistoryResponse.data.content.filter(
        (tx: TransactionInfo) => tx.type === 'TRANSFER' && tx.status === 'COMPLETED'
      );

      // Each successful transfer should appear in both account histories
      expect(checkingTransfers.length).toBe(successfulTransfers.length);
      expect(savingsTransfers.length).toBe(successfulTransfers.length);

      logger.info('✅ Concurrent transfers handled with proper synchronization');
      logger.testComplete('Concurrent transfers between accounts with proper synchronization', 'PASSED');

      testUsers.push(testUser);
    }, 120000);

    test('should maintain data integrity under high concurrency load', async () => {
      const testUser = createTestUser({
        username: `high_concurrency_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 5000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Data integrity under high concurrency load');

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
        initialBalance: 5000.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Generate high volume of concurrent transactions
      const highConcurrencyTransactions = Array.from({ length: 20 }, (_, index) => ({
        type: index % 3 === 0 ? 'deposit' : 'withdraw',
        amount: 25.00 + (index * 5), // Varying amounts
        description: `High concurrency test ${index + 1}`
      }));

      logger.info(`🚀 Processing ${highConcurrencyTransactions.length} high concurrency transactions`);

      // Execute all transactions with minimal delays to maximize concurrency
      const highConcurrencyPromises = highConcurrencyTransactions.map(async (tx, index) => {
        try {
          // Random micro-delay to create more realistic concurrency patterns
          await delay(Math.random() * 5);
          
          const endpoint = tx.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
          
          const response = await transactionClient.post(endpoint, {
            accountId: account.id,
            amount: tx.amount,
            currency: 'USD',
            description: tx.description
          });

          return {
            success: true,
            transaction: response.data,
            type: tx.type,
            amount: tx.amount,
            index
          };
        } catch (error) {
          return {
            success: false,
            error,
            type: tx.type,
            amount: tx.amount,
            index
          };
        }
      });

      const highConcurrencyResults = await Promise.all(highConcurrencyPromises);

      // Analyze high concurrency results
      const successfulHighConcurrency = highConcurrencyResults.filter(r => r.success);
      const failedHighConcurrency = highConcurrencyResults.filter(r => !r.success);

      logger.info(`High concurrency results - Successful: ${successfulHighConcurrency.length}, Failed: ${failedHighConcurrency.length}`);

      // Calculate expected balance
      let expectedBalance = 5000.00;
      for (const result of successfulHighConcurrency) {
        if (result.success) {
          expectedBalance += (result as any).type === 'deposit' ? (result as any).amount : -(result as any).amount;
        }
      }

      // Wait for all transactions to be fully processed
      await delay(5000);

      // Verify data integrity after high concurrency load
      logger.info('🔍 Verifying data integrity after high concurrency load');
      const finalBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const actualBalance = finalBalanceResponse.data.balance;

      expect(actualBalance).toBe(expectedBalance);
      expect(actualBalance).toBeGreaterThanOrEqual(0); // Balance should never go negative

      logger.info(`✅ Balance integrity maintained - Expected: ${expectedBalance}, Actual: ${actualBalance}`);

      // Verify transaction history integrity
      const finalHistoryResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const allTransactions = finalHistoryResponse.data.content;
      const completedTransactions = allTransactions.filter((tx: TransactionInfo) => tx.status === 'COMPLETED');

      expect(completedTransactions.length).toBe(successfulHighConcurrency.length);

      // Verify no duplicate transactions
      const allTransactionIds = allTransactions.map((tx: TransactionInfo) => tx.id);
      const uniqueTransactionIds = [...new Set(allTransactionIds)];
      expect(allTransactionIds.length).toBe(uniqueTransactionIds.length);

      // Verify balance calculation from history matches account balance
      let calculatedBalance = 5000.00;
      for (const tx of completedTransactions) {
        if (tx.type === 'DEPOSIT') {
          calculatedBalance += tx.amount;
        } else if (tx.type === 'WITHDRAWAL') {
          calculatedBalance -= tx.amount;
        }
      }

      expect(calculatedBalance).toBe(actualBalance);

      // Verify all transactions have proper timestamps and are in chronological order
      const timestamps = completedTransactions.map((tx: TransactionInfo) => new Date(tx.createdAt).getTime());
      const sortedTimestamps = [...timestamps].sort((a, b) => a - b);
      
      // Allow for some timestamp variance due to concurrent processing
      const timestampVariance = timestamps.every((ts, index) => 
        Math.abs(ts - sortedTimestamps[index]) < 10000 // 10 second tolerance
      );
      
      expect(timestampVariance).toBe(true);

      logger.info('✅ Data integrity maintained under high concurrency load');
      logger.testComplete('Data integrity under high concurrency load', 'PASSED');

      testUsers.push(testUser);
    }, 150000);
  });
});