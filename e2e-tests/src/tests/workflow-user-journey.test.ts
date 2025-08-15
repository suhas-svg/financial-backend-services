import { describe, test, expect, beforeAll, afterAll, beforeEach, afterEach } from '@jest/globals';
import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { createTestUser, generateTestId, waitForCondition, delay } from '../utils/test-helpers';
import { logger } from '../utils/logger';
import { testConfig } from '../config/test-config';
import { TestUser, AuthResponse, AccountInfo, TransactionInfo } from '../types';

/**
 * Complete User Journey Tests
 * Tests full user registration to transaction processing workflows
 * Requirements: 5.1
 */
describe('End-to-End User Journey Tests', () => {
  let accountClient: any;
  let transactionClient: any;
  let testUsers: TestUser[] = [];
  let createdAccounts: AccountInfo[] = [];
  let testRunId: string;

  beforeAll(async () => {
    testRunId = generateTestId('user-journey');
    logger.info(`🚀 Starting User Journey Tests - Run ID: ${testRunId}`);

    // Initialize service clients
    accountClient = createAccountServiceClient();
    transactionClient = createTransactionServiceClient();

    // Wait for services to be ready
    const accountReady = await accountClient.waitForService();
    const transactionReady = await transactionClient.waitForService();

    if (!accountReady || !transactionReady) {
      throw new Error('Services not ready for testing');
    }

    logger.info('✅ Services are ready for user journey testing');
  });

  afterAll(async () => {
    logger.info(`🧹 Cleaning up user journey test data - Run ID: ${testRunId}`);
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

  describe('Complete User Registration to Transaction Processing Workflow', () => {
    test('should complete full user lifecycle from registration to transaction processing', async () => {
      const testUser = createTestUser({
        username: `journey_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Complete user lifecycle workflow');

      // Step 1: User Registration
      logger.info('📝 Step 1: User Registration');
      const registrationResponse = await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      expect(registrationResponse.status).toBe(201);
      expect(registrationResponse.data).toHaveProperty('message');
      logger.info('✅ User registration successful');

      // Step 2: User Login
      logger.info('🔐 Step 2: User Login');
      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      expect(loginResponse.status).toBe(200);
      expect(loginResponse.data).toHaveProperty('accessToken');
      expect(loginResponse.data.accessToken).toBeTruthy();

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);
      logger.info('✅ User login successful');

      // Step 3: Account Creation
      logger.info('🏦 Step 3: Account Creation');
      const accountData = testUser.accounts![0];
      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: accountData.accountType,
        initialBalance: accountData.initialBalance,
        currency: accountData.currency
      });

      expect(accountResponse.status).toBe(201);
      expect(accountResponse.data).toHaveProperty('id');
      expect(accountResponse.data.accountType).toBe(accountData.accountType);
      expect(accountResponse.data.balance).toBe(accountData.initialBalance);

      const createdAccount: AccountInfo = accountResponse.data;
      createdAccounts.push(createdAccount);
      logger.info(`✅ Account created with ID: ${createdAccount.id}`);

      // Step 4: Initial Deposit Transaction
      logger.info('💰 Step 4: Initial Deposit Transaction');
      const depositAmount = 500.00;
      const depositResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: createdAccount.id,
        amount: depositAmount,
        currency: 'USD',
        description: 'Initial deposit for user journey test'
      });

      expect(depositResponse.status).toBe(201);
      expect(depositResponse.data).toHaveProperty('id');
      expect(depositResponse.data.type).toBe('DEPOSIT');
      expect(depositResponse.data.amount).toBe(depositAmount);
      expect(depositResponse.data.status).toBe('COMPLETED');

      const depositTransaction: TransactionInfo = depositResponse.data;
      logger.info(`✅ Deposit transaction completed: ${depositTransaction.id}`);

      // Step 5: Verify Account Balance Update
      logger.info('🔍 Step 5: Verify Account Balance Update');
      const updatedAccountResponse = await accountClient.get(`/api/accounts/${createdAccount.id}`);
      
      expect(updatedAccountResponse.status).toBe(200);
      expect(updatedAccountResponse.data.balance).toBe(accountData.initialBalance + depositAmount);
      logger.info(`✅ Account balance updated to: ${updatedAccountResponse.data.balance}`);

      // Step 6: Withdrawal Transaction
      logger.info('💸 Step 6: Withdrawal Transaction');
      const withdrawalAmount = 200.00;
      const withdrawalResponse = await transactionClient.post('/api/transactions/withdraw', {
        accountId: createdAccount.id,
        amount: withdrawalAmount,
        currency: 'USD',
        description: 'Withdrawal for user journey test'
      });

      expect(withdrawalResponse.status).toBe(201);
      expect(withdrawalResponse.data.type).toBe('WITHDRAWAL');
      expect(withdrawalResponse.data.amount).toBe(withdrawalAmount);
      expect(withdrawalResponse.data.status).toBe('COMPLETED');

      const withdrawalTransaction: TransactionInfo = withdrawalResponse.data;
      logger.info(`✅ Withdrawal transaction completed: ${withdrawalTransaction.id}`);

      // Step 7: Verify Final Account Balance
      logger.info('🔍 Step 7: Verify Final Account Balance');
      const finalAccountResponse = await accountClient.get(`/api/accounts/${createdAccount.id}`);
      
      const expectedBalance = accountData.initialBalance + depositAmount - withdrawalAmount;
      expect(finalAccountResponse.status).toBe(200);
      expect(finalAccountResponse.data.balance).toBe(expectedBalance);
      logger.info(`✅ Final account balance verified: ${finalAccountResponse.data.balance}`);

      // Step 8: Verify Transaction History
      logger.info('📋 Step 8: Verify Transaction History');
      const historyResponse = await transactionClient.get(`/api/transactions/account/${createdAccount.id}`);
      
      expect(historyResponse.status).toBe(200);
      expect(historyResponse.data).toHaveProperty('content');
      expect(historyResponse.data.content.length).toBeGreaterThanOrEqual(2);
      
      const transactions = historyResponse.data.content;
      const depositTx = transactions.find((tx: TransactionInfo) => tx.type === 'DEPOSIT');
      const withdrawalTx = transactions.find((tx: TransactionInfo) => tx.type === 'WITHDRAWAL');
      
      expect(depositTx).toBeTruthy();
      expect(withdrawalTx).toBeTruthy();
      expect(depositTx.amount).toBe(depositAmount);
      expect(withdrawalTx.amount).toBe(withdrawalAmount);
      
      logger.info('✅ Transaction history verified');
      logger.testComplete('Complete user lifecycle workflow', 'PASSED');

      testUsers.push(testUser);
    }, 60000); // 60 second timeout for complete workflow

    test('should handle user onboarding workflow with account creation and initial deposit', async () => {
      const testUser = createTestUser({
        username: `onboarding_user_${testRunId}`,
        accounts: [
          {
            accountType: 'SAVINGS',
            initialBalance: 2000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('User onboarding workflow');

      // Step 1: Register new user
      logger.info('📝 Registering new user for onboarding');
      const registrationResponse = await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });

      expect(registrationResponse.status).toBe(201);

      // Step 2: Login immediately after registration
      logger.info('🔐 Logging in new user');
      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      expect(loginResponse.status).toBe(200);
      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      // Step 3: Create savings account as part of onboarding
      logger.info('🏦 Creating savings account for onboarding');
      const accountData = testUser.accounts![0];
      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: accountData.accountType,
        initialBalance: accountData.initialBalance,
        currency: accountData.currency
      });

      expect(accountResponse.status).toBe(201);
      expect(accountResponse.data.accountType).toBe('SAVINGS');
      
      const savingsAccount: AccountInfo = accountResponse.data;
      createdAccounts.push(savingsAccount);

      // Step 4: Make initial deposit to activate account
      logger.info('💰 Making initial deposit to activate account');
      const initialDeposit = 1000.00;
      const depositResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: savingsAccount.id,
        amount: initialDeposit,
        currency: 'USD',
        description: 'Initial onboarding deposit'
      });

      expect(depositResponse.status).toBe(201);
      expect(depositResponse.data.type).toBe('DEPOSIT');
      expect(depositResponse.data.amount).toBe(initialDeposit);

      // Step 5: Verify account is now active with correct balance
      logger.info('🔍 Verifying account activation and balance');
      const activatedAccountResponse = await accountClient.get(`/api/accounts/${savingsAccount.id}`);
      
      expect(activatedAccountResponse.status).toBe(200);
      expect(activatedAccountResponse.data.balance).toBe(accountData.initialBalance + initialDeposit);
      expect(activatedAccountResponse.data.status).toBe('ACTIVE');

      logger.info('✅ User onboarding workflow completed successfully');
      logger.testComplete('User onboarding workflow', 'PASSED');

      testUsers.push(testUser);
    }, 45000);

    test('should handle multi-step user workflow with error recovery scenarios', async () => {
      const testUser = createTestUser({
        username: `recovery_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 500.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Multi-step workflow with error recovery');

      // Step 1: Register and login user
      logger.info('📝 Setting up user for error recovery test');
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

      // Step 2: Create account
      const accountData = testUser.accounts![0];
      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: accountData.accountType,
        initialBalance: accountData.initialBalance,
        currency: accountData.currency
      });

      const testAccount: AccountInfo = accountResponse.data;
      createdAccounts.push(testAccount);

      // Step 3: Attempt invalid transaction (insufficient funds)
      logger.info('❌ Attempting invalid withdrawal (insufficient funds)');
      const invalidWithdrawalAmount = 1000.00; // More than account balance
      
      try {
        const invalidWithdrawalResponse = await transactionClient.post('/api/transactions/withdraw', {
          accountId: testAccount.id,
          amount: invalidWithdrawalAmount,
          currency: 'USD',
          description: 'Invalid withdrawal test'
        });

        // Should not reach here if validation works
        expect(invalidWithdrawalResponse.status).toBe(400);
      } catch (error: any) {
        // Expect error for insufficient funds
        expect(error.status).toBe(400);
        logger.info('✅ Invalid withdrawal properly rejected');
      }

      // Step 4: Verify account balance unchanged after failed transaction
      logger.info('🔍 Verifying account balance unchanged after failed transaction');
      const balanceAfterFailedTx = await accountClient.get(`/api/accounts/${testAccount.id}`);
      expect(balanceAfterFailedTx.data.balance).toBe(accountData.initialBalance);

      // Step 5: Perform valid transaction after error recovery
      logger.info('✅ Performing valid transaction after error recovery');
      const validWithdrawalAmount = 100.00;
      const validWithdrawalResponse = await transactionClient.post('/api/transactions/withdraw', {
        accountId: testAccount.id,
        amount: validWithdrawalAmount,
        currency: 'USD',
        description: 'Valid withdrawal after recovery'
      });

      expect(validWithdrawalResponse.status).toBe(201);
      expect(validWithdrawalResponse.data.type).toBe('WITHDRAWAL');
      expect(validWithdrawalResponse.data.status).toBe('COMPLETED');

      // Step 6: Verify final balance is correct
      logger.info('🔍 Verifying final balance after recovery');
      const finalBalanceResponse = await accountClient.get(`/api/accounts/${testAccount.id}`);
      const expectedFinalBalance = accountData.initialBalance - validWithdrawalAmount;
      expect(finalBalanceResponse.data.balance).toBe(expectedFinalBalance);

      // Step 7: Test authentication error recovery
      logger.info('🔐 Testing authentication error recovery');
      
      // Clear auth token to simulate expired token
      accountClient.clearAuthToken();
      transactionClient.clearAuthToken();

      // Attempt transaction without auth (should fail)
      try {
        await transactionClient.post('/api/transactions/deposit', {
          accountId: testAccount.id,
          amount: 50.00,
          currency: 'USD'
        });
        fail('Should have failed without authentication');
      } catch (error: any) {
        expect(error.status).toBe(401);
        logger.info('✅ Unauthenticated request properly rejected');
      }

      // Re-authenticate and retry
      logger.info('🔐 Re-authenticating and retrying transaction');
      const reLoginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });

      const newAuthData: AuthResponse = reLoginResponse.data;
      accountClient.setAuthToken(newAuthData.accessToken);
      transactionClient.setAuthToken(newAuthData.accessToken);

      // Retry the transaction
      const retryDepositResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: 50.00,
        currency: 'USD',
        description: 'Deposit after re-authentication'
      });

      expect(retryDepositResponse.status).toBe(201);
      expect(retryDepositResponse.data.type).toBe('DEPOSIT');

      logger.info('✅ Multi-step workflow with error recovery completed successfully');
      logger.testComplete('Multi-step workflow with error recovery', 'PASSED');

      testUsers.push(testUser);
    }, 60000);
  });

  describe('User Journey Performance and Reliability', () => {
    test('should complete user journey within acceptable time limits', async () => {
      const testUser = createTestUser({
        username: `perf_user_${testRunId}`
      });

      logger.testStart('User journey performance test');
      const startTime = Date.now();

      // Complete user journey with timing
      const registrationResponse = await accountClient.post('/api/auth/register', {
        username: testUser.username,
        password: testUser.password,
        email: testUser.email
      });
      expect(registrationResponse.status).toBe(201);

      const loginResponse = await accountClient.post('/api/auth/login', {
        username: testUser.username,
        password: testUser.password
      });
      expect(loginResponse.status).toBe(200);

      const authData: AuthResponse = loginResponse.data;
      accountClient.setAuthToken(authData.accessToken);
      transactionClient.setAuthToken(authData.accessToken);

      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 1000.00,
        currency: 'USD'
      });
      expect(accountResponse.status).toBe(201);

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      const transactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: account.id,
        amount: 100.00,
        currency: 'USD'
      });
      expect(transactionResponse.status).toBe(201);

      const totalTime = Date.now() - startTime;
      const maxAcceptableTime = 10000; // 10 seconds

      expect(totalTime).toBeLessThan(maxAcceptableTime);
      logger.info(`✅ User journey completed in ${totalTime}ms (limit: ${maxAcceptableTime}ms)`);
      logger.testComplete('User journey performance test', 'PASSED');

      testUsers.push(testUser);
    }, 15000);

    test('should handle multiple sequential operations reliably', async () => {
      const testUser = createTestUser({
        username: `sequential_user_${testRunId}`
      });

      logger.testStart('Sequential operations reliability test');

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

      // Perform multiple sequential transactions
      const transactions = [
        { type: 'deposit', amount: 100.00 },
        { type: 'withdraw', amount: 50.00 },
        { type: 'deposit', amount: 200.00 },
        { type: 'withdraw', amount: 75.00 }
      ];

      let expectedBalance = 1000.00;
      
      for (let i = 0; i < transactions.length; i++) {
        const tx = transactions[i];
        logger.info(`Performing transaction ${i + 1}: ${tx.type} ${tx.amount}`);

        const endpoint = tx.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
        const response = await transactionClient.post(endpoint, {
          accountId: account.id,
          amount: tx.amount,
          currency: 'USD',
          description: `Sequential ${tx.type} ${i + 1}`
        });

        expect(response.status).toBe(201);
        expect(response.data.status).toBe('COMPLETED');

        // Update expected balance
        expectedBalance += tx.type === 'deposit' ? tx.amount : -tx.amount;

        // Verify balance after each transaction
        const balanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
        expect(balanceResponse.data.balance).toBe(expectedBalance);

        // Small delay between transactions to simulate real usage
        await delay(100);
      }

      logger.info(`✅ All ${transactions.length} sequential transactions completed successfully`);
      logger.testComplete('Sequential operations reliability test', 'PASSED');

      testUsers.push(testUser);
    }, 30000);
  });
});