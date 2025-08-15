import { describe, test, expect, beforeAll, afterAll, beforeEach, afterEach } from '@jest/globals';
import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { createTestUser, generateTestId, waitForCondition, delay, compareAmounts } from '../utils/test-helpers';
import { logger } from '../utils/logger';
import { testConfig } from '../config/test-config';
import { TestUser, AuthResponse, AccountInfo, TransactionInfo } from '../types';

/**
 * Financial Transaction Workflow Tests
 * Tests complete financial transaction workflows with account validation and balance updates
 * Requirements: 5.2, 5.3, 5.4, 5.5
 */
describe('Financial Transaction Workflow Tests', () => {
  let accountClient: any;
  let transactionClient: any;
  let testUsers: TestUser[] = [];
  let createdAccounts: AccountInfo[] = [];
  let testRunId: string;

  beforeAll(async () => {
    testRunId = generateTestId('financial-workflow');
    logger.info(`🚀 Starting Financial Transaction Workflow Tests - Run ID: ${testRunId}`);

    // Initialize service clients
    accountClient = createAccountServiceClient();
    transactionClient = createTransactionServiceClient();

    // Wait for services to be ready
    const accountReady = await accountClient.waitForService();
    const transactionReady = await transactionClient.waitForService();

    if (!accountReady || !transactionReady) {
      throw new Error('Services not ready for testing');
    }

    logger.info('✅ Services are ready for financial workflow testing');
  });

  afterAll(async () => {
    logger.info(`🧹 Cleaning up financial workflow test data - Run ID: ${testRunId}`);
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

  describe('Deposit Workflow Tests', () => {
    test('should complete deposit workflow with account validation and balance updates', async () => {
      const testUser = createTestUser({
        username: `deposit_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 500.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Deposit workflow with validation and balance updates');

      // Setup: Register user and create account
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

      const accountData = testUser.accounts![0];
      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: accountData.accountType,
        initialBalance: accountData.initialBalance,
        currency: accountData.currency
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Step 1: Validate account exists and is active
      logger.info('🔍 Step 1: Validate account exists and is active');
      const accountValidationResponse = await accountClient.get(`/api/accounts/${account.id}`);
      
      expect(accountValidationResponse.status).toBe(200);
      expect(accountValidationResponse.data.id).toBe(account.id);
      expect(accountValidationResponse.data.status).toBe('ACTIVE');
      expect(accountValidationResponse.data.balance).toBe(accountData.initialBalance);
      
      const initialBalance = accountValidationResponse.data.balance;
      logger.info(`✅ Account validated - Initial balance: ${initialBalance}`);

      // Step 2: Process deposit transaction
      logger.info('💰 Step 2: Process deposit transaction');
      const depositAmount = 250.00;
      const depositResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: account.id,
        amount: depositAmount,
        currency: 'USD',
        description: 'Deposit workflow test'
      });

      expect(depositResponse.status).toBe(201);
      expect(depositResponse.data).toHaveProperty('id');
      expect(depositResponse.data.type).toBe('DEPOSIT');
      expect(depositResponse.data.amount).toBe(depositAmount);
      expect(depositResponse.data.accountId).toBe(account.id);
      expect(depositResponse.data.status).toBe('COMPLETED');
      expect(depositResponse.data.currency).toBe('USD');

      const depositTransaction: TransactionInfo = depositResponse.data;
      logger.info(`✅ Deposit transaction created: ${depositTransaction.id}`);

      // Step 3: Verify account balance update
      logger.info('🔍 Step 3: Verify account balance update');
      const updatedAccountResponse = await accountClient.get(`/api/accounts/${account.id}`);
      
      expect(updatedAccountResponse.status).toBe(200);
      const expectedBalance = initialBalance + depositAmount;
      expect(updatedAccountResponse.data.balance).toBe(expectedBalance);
      
      logger.info(`✅ Account balance updated: ${initialBalance} + ${depositAmount} = ${updatedAccountResponse.data.balance}`);

      // Step 4: Verify transaction appears in account history
      logger.info('📋 Step 4: Verify transaction appears in account history');
      const historyResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      
      expect(historyResponse.status).toBe(200);
      expect(historyResponse.data).toHaveProperty('content');
      
      const transactions = historyResponse.data.content;
      const depositTx = transactions.find((tx: TransactionInfo) => tx.id === depositTransaction.id);
      
      expect(depositTx).toBeTruthy();
      expect(depositTx.type).toBe('DEPOSIT');
      expect(depositTx.amount).toBe(depositAmount);
      expect(depositTx.status).toBe('COMPLETED');
      
      logger.info('✅ Transaction appears correctly in account history');

      // Step 5: Verify transaction consistency
      logger.info('🔍 Step 5: Verify transaction consistency');
      const transactionDetailResponse = await transactionClient.get(`/api/transactions/${depositTransaction.id}`);
      
      expect(transactionDetailResponse.status).toBe(200);
      expect(transactionDetailResponse.data.id).toBe(depositTransaction.id);
      expect(transactionDetailResponse.data.accountId).toBe(account.id);
      expect(transactionDetailResponse.data.amount).toBe(depositAmount);
      expect(transactionDetailResponse.data.type).toBe('DEPOSIT');
      
      logger.info('✅ Transaction consistency verified');
      logger.testComplete('Deposit workflow with validation and balance updates', 'PASSED');

      testUsers.push(testUser);
    }, 45000);

    test('should handle multiple deposits with cumulative balance updates', async () => {
      const testUser = createTestUser({
        username: `multi_deposit_user_${testRunId}`,
        accounts: [
          {
            accountType: 'SAVINGS',
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Multiple deposits with cumulative balance updates');

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
        accountType: 'SAVINGS',
        initialBalance: 1000.00,
        currency: 'USD'
      });

      const account: AccountInfo = accountResponse.data;
      createdAccounts.push(account);

      // Perform multiple deposits
      const deposits = [100.00, 250.00, 75.50, 300.00];
      let expectedBalance = 1000.00;
      const completedTransactions: TransactionInfo[] = [];

      for (let i = 0; i < deposits.length; i++) {
        const depositAmount = deposits[i];
        logger.info(`💰 Processing deposit ${i + 1}: ${depositAmount}`);

        const depositResponse = await transactionClient.post('/api/transactions/deposit', {
          accountId: account.id,
          amount: depositAmount,
          currency: 'USD',
          description: `Multiple deposit test ${i + 1}`
        });

        expect(depositResponse.status).toBe(201);
        expect(depositResponse.data.amount).toBe(depositAmount);
        completedTransactions.push(depositResponse.data);

        expectedBalance += depositAmount;

        // Verify balance after each deposit
        const balanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
        expect(balanceResponse.data.balance).toBe(expectedBalance);

        logger.info(`✅ Deposit ${i + 1} completed - Balance: ${balanceResponse.data.balance}`);
      }

      // Verify all transactions in history
      const historyResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      expect(historyResponse.data.content.length).toBeGreaterThanOrEqual(deposits.length);

      logger.info(`✅ All ${deposits.length} deposits completed successfully`);
      logger.testComplete('Multiple deposits with cumulative balance updates', 'PASSED');

      testUsers.push(testUser);
    }, 60000);
  });

  describe('Withdrawal Workflow Tests', () => {
    test('should complete withdrawal workflow with funds validation and balance updates', async () => {
      const testUser = createTestUser({
        username: `withdrawal_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Withdrawal workflow with funds validation and balance updates');

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

      // Step 1: Validate sufficient funds
      logger.info('🔍 Step 1: Validate sufficient funds');
      const initialBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const initialBalance = initialBalanceResponse.data.balance;
      const withdrawalAmount = 300.00;

      expect(initialBalance).toBeGreaterThan(withdrawalAmount);
      logger.info(`✅ Sufficient funds available: ${initialBalance} > ${withdrawalAmount}`);

      // Step 2: Process withdrawal transaction
      logger.info('💸 Step 2: Process withdrawal transaction');
      const withdrawalResponse = await transactionClient.post('/api/transactions/withdraw', {
        accountId: account.id,
        amount: withdrawalAmount,
        currency: 'USD',
        description: 'Withdrawal workflow test'
      });

      expect(withdrawalResponse.status).toBe(201);
      expect(withdrawalResponse.data.type).toBe('WITHDRAWAL');
      expect(withdrawalResponse.data.amount).toBe(withdrawalAmount);
      expect(withdrawalResponse.data.accountId).toBe(account.id);
      expect(withdrawalResponse.data.status).toBe('COMPLETED');

      const withdrawalTransaction: TransactionInfo = withdrawalResponse.data;
      logger.info(`✅ Withdrawal transaction created: ${withdrawalTransaction.id}`);

      // Step 3: Verify account balance update
      logger.info('🔍 Step 3: Verify account balance update');
      const updatedBalanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
      const expectedBalance = initialBalance - withdrawalAmount;
      
      expect(updatedBalanceResponse.data.balance).toBe(expectedBalance);
      logger.info(`✅ Account balance updated: ${initialBalance} - ${withdrawalAmount} = ${updatedBalanceResponse.data.balance}`);

      // Step 4: Verify transaction in history
      logger.info('📋 Step 4: Verify transaction in history');
      const historyResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const transactions = historyResponse.data.content;
      const withdrawalTx = transactions.find((tx: TransactionInfo) => tx.id === withdrawalTransaction.id);

      expect(withdrawalTx).toBeTruthy();
      expect(withdrawalTx.type).toBe('WITHDRAWAL');
      expect(withdrawalTx.amount).toBe(withdrawalAmount);

      logger.info('✅ Withdrawal workflow completed successfully');
      logger.testComplete('Withdrawal workflow with funds validation and balance updates', 'PASSED');

      testUsers.push(testUser);
    }, 45000);

    test('should reject withdrawal with insufficient funds', async () => {
      const testUser = createTestUser({
        username: `insufficient_funds_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 100.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Withdrawal rejection with insufficient funds');

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

      const initialBalance = account.balance;
      const withdrawalAmount = 500.00; // More than available balance

      logger.info(`💸 Attempting withdrawal of ${withdrawalAmount} from account with ${initialBalance}`);

      // Attempt withdrawal with insufficient funds
      try {
        const withdrawalResponse = await transactionClient.post('/api/transactions/withdraw', {
          accountId: account.id,
          amount: withdrawalAmount,
          currency: 'USD',
          description: 'Insufficient funds test'
        });

        // Should not reach here if validation works
        expect(withdrawalResponse.status).toBe(400);
      } catch (error: any) {
        expect(error.status).toBe(400);
        expect(error.data).toHaveProperty('message');
        logger.info('✅ Withdrawal properly rejected for insufficient funds');
      }

      // Verify balance unchanged
      const balanceAfterFailedWithdrawal = await accountClient.get(`/api/accounts/${account.id}`);
      expect(balanceAfterFailedWithdrawal.data.balance).toBe(initialBalance);

      logger.info('✅ Account balance unchanged after failed withdrawal');
      logger.testComplete('Withdrawal rejection with insufficient funds', 'PASSED');

      testUsers.push(testUser);
    }, 30000);
  });

  describe('Transfer Workflow Tests', () => {
    test('should complete transfer workflow with dual account validation and balance updates', async () => {
      const testUser = createTestUser({
        username: `transfer_user_${testRunId}`,
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

      logger.testStart('Transfer workflow with dual account validation and balance updates');

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

      // Create source account (checking)
      const sourceAccountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 1000.00,
        currency: 'USD'
      });

      const sourceAccount: AccountInfo = sourceAccountResponse.data;
      createdAccounts.push(sourceAccount);

      // Create destination account (savings)
      const destAccountResponse = await accountClient.post('/api/accounts', {
        accountType: 'SAVINGS',
        initialBalance: 500.00,
        currency: 'USD'
      });

      const destAccount: AccountInfo = destAccountResponse.data;
      createdAccounts.push(destAccount);

      // Step 1: Validate both accounts exist and are active
      logger.info('🔍 Step 1: Validate both accounts exist and are active');
      const sourceValidation = await accountClient.get(`/api/accounts/${sourceAccount.id}`);
      const destValidation = await accountClient.get(`/api/accounts/${destAccount.id}`);

      expect(sourceValidation.status).toBe(200);
      expect(destValidation.status).toBe(200);
      expect(sourceValidation.data.status).toBe('ACTIVE');
      expect(destValidation.data.status).toBe('ACTIVE');

      const initialSourceBalance = sourceValidation.data.balance;
      const initialDestBalance = destValidation.data.balance;

      logger.info(`✅ Source account balance: ${initialSourceBalance}`);
      logger.info(`✅ Destination account balance: ${initialDestBalance}`);

      // Step 2: Process transfer transaction
      logger.info('💱 Step 2: Process transfer transaction');
      const transferAmount = 200.00;
      const transferResponse = await transactionClient.post('/api/transactions/transfer', {
        fromAccountId: sourceAccount.id,
        toAccountId: destAccount.id,
        amount: transferAmount,
        currency: 'USD',
        description: 'Transfer workflow test'
      });

      expect(transferResponse.status).toBe(201);
      expect(transferResponse.data.type).toBe('TRANSFER');
      expect(transferResponse.data.amount).toBe(transferAmount);
      expect(transferResponse.data.status).toBe('COMPLETED');

      const transferTransaction: TransactionInfo = transferResponse.data;
      logger.info(`✅ Transfer transaction created: ${transferTransaction.id}`);

      // Step 3: Verify source account balance update
      logger.info('🔍 Step 3: Verify source account balance update');
      const updatedSourceResponse = await accountClient.get(`/api/accounts/${sourceAccount.id}`);
      const expectedSourceBalance = initialSourceBalance - transferAmount;

      expect(updatedSourceResponse.data.balance).toBe(expectedSourceBalance);
      logger.info(`✅ Source account balance: ${initialSourceBalance} - ${transferAmount} = ${updatedSourceResponse.data.balance}`);

      // Step 4: Verify destination account balance update
      logger.info('🔍 Step 4: Verify destination account balance update');
      const updatedDestResponse = await accountClient.get(`/api/accounts/${destAccount.id}`);
      const expectedDestBalance = initialDestBalance + transferAmount;

      expect(updatedDestResponse.data.balance).toBe(expectedDestBalance);
      logger.info(`✅ Destination account balance: ${initialDestBalance} + ${transferAmount} = ${updatedDestResponse.data.balance}`);

      // Step 5: Verify transfer appears in both account histories
      logger.info('📋 Step 5: Verify transfer appears in both account histories');
      const sourceHistoryResponse = await transactionClient.get(`/api/transactions/account/${sourceAccount.id}`);
      const destHistoryResponse = await transactionClient.get(`/api/transactions/account/${destAccount.id}`);

      const sourceTransactions = sourceHistoryResponse.data.content;
      const destTransactions = destHistoryResponse.data.content;

      // Find transfer-related transactions
      const sourceTransferTx = sourceTransactions.find((tx: TransactionInfo) => 
        tx.type === 'TRANSFER' && tx.amount === transferAmount
      );
      const destTransferTx = destTransactions.find((tx: TransactionInfo) => 
        tx.type === 'TRANSFER' && tx.amount === transferAmount
      );

      expect(sourceTransferTx).toBeTruthy();
      expect(destTransferTx).toBeTruthy();

      logger.info('✅ Transfer appears in both account histories');
      logger.testComplete('Transfer workflow with dual account validation and balance updates', 'PASSED');

      testUsers.push(testUser);
    }, 60000);

    test('should reject transfer between accounts of different users', async () => {
      // Create two different users
      const user1 = createTestUser({
        username: `transfer_user1_${testRunId}`,
        accounts: [{ accountType: 'CHECKING', initialBalance: 1000.00, currency: 'USD' }]
      });

      const user2 = createTestUser({
        username: `transfer_user2_${testRunId}`,
        accounts: [{ accountType: 'SAVINGS', initialBalance: 500.00, currency: 'USD' }]
      });

      logger.testStart('Transfer rejection between different users');

      // Setup user 1
      await accountClient.post('/api/auth/register', {
        username: user1.username,
        password: user1.password,
        email: user1.email
      });

      const login1Response = await accountClient.post('/api/auth/login', {
        username: user1.username,
        password: user1.password
      });

      const auth1Data: AuthResponse = login1Response.data;
      accountClient.setAuthToken(auth1Data.accessToken);
      transactionClient.setAuthToken(auth1Data.accessToken);

      const account1Response = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 1000.00,
        currency: 'USD'
      });

      const account1: AccountInfo = account1Response.data;
      createdAccounts.push(account1);

      // Setup user 2
      accountClient.clearAuthToken();
      transactionClient.clearAuthToken();

      await accountClient.post('/api/auth/register', {
        username: user2.username,
        password: user2.password,
        email: user2.email
      });

      const login2Response = await accountClient.post('/api/auth/login', {
        username: user2.username,
        password: user2.password
      });

      const auth2Data: AuthResponse = login2Response.data;
      accountClient.setAuthToken(auth2Data.accessToken);
      transactionClient.setAuthToken(auth2Data.accessToken);

      const account2Response = await accountClient.post('/api/accounts', {
        accountType: 'SAVINGS',
        initialBalance: 500.00,
        currency: 'USD'
      });

      const account2: AccountInfo = account2Response.data;
      createdAccounts.push(account2);

      // Attempt transfer from user1's account to user2's account while authenticated as user2
      logger.info('💱 Attempting cross-user transfer (should fail)');
      
      try {
        const transferResponse = await transactionClient.post('/api/transactions/transfer', {
          fromAccountId: account1.id,
          toAccountId: account2.id,
          amount: 100.00,
          currency: 'USD',
          description: 'Cross-user transfer test'
        });

        // Should not reach here if authorization works
        expect(transferResponse.status).toBe(403);
      } catch (error: any) {
        expect(error.status).toBe(403);
        logger.info('✅ Cross-user transfer properly rejected');
      }

      logger.testComplete('Transfer rejection between different users', 'PASSED');

      testUsers.push(user1);
      testUsers.push(user2);
    }, 45000);
  });

  describe('Transaction History Validation Throughout Workflows', () => {
    test('should maintain accurate transaction history throughout complete workflows', async () => {
      const testUser = createTestUser({
        username: `history_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('Transaction history validation throughout complete workflows');

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

      // Perform a series of transactions
      const transactionSequence = [
        { type: 'deposit', amount: 200.00, description: 'Initial deposit' },
        { type: 'withdraw', amount: 150.00, description: 'ATM withdrawal' },
        { type: 'deposit', amount: 300.00, description: 'Salary deposit' },
        { type: 'withdraw', amount: 75.00, description: 'Grocery shopping' },
        { type: 'deposit', amount: 100.00, description: 'Refund' }
      ];

      const completedTransactions: TransactionInfo[] = [];
      let expectedBalance = 1000.00;

      for (let i = 0; i < transactionSequence.length; i++) {
        const tx = transactionSequence[i];
        logger.info(`Processing transaction ${i + 1}: ${tx.type} ${tx.amount}`);

        const endpoint = tx.type === 'deposit' ? '/api/transactions/deposit' : '/api/transactions/withdraw';
        const response = await transactionClient.post(endpoint, {
          accountId: account.id,
          amount: tx.amount,
          currency: 'USD',
          description: tx.description
        });

        expect(response.status).toBe(201);
        completedTransactions.push(response.data);

        expectedBalance += tx.type === 'deposit' ? tx.amount : -tx.amount;

        // Verify history after each transaction
        const historyResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
        expect(historyResponse.data.content.length).toBe(i + 1);

        // Verify balance consistency
        const balanceResponse = await accountClient.get(`/api/accounts/${account.id}`);
        expect(balanceResponse.data.balance).toBe(expectedBalance);
      }

      // Final history validation
      logger.info('📋 Performing final history validation');
      const finalHistoryResponse = await transactionClient.get(`/api/transactions/account/${account.id}`);
      const finalTransactions = finalHistoryResponse.data.content;

      expect(finalTransactions.length).toBe(transactionSequence.length);

      // Verify each transaction in history
      for (let i = 0; i < transactionSequence.length; i++) {
        const expectedTx = transactionSequence[i];
        const actualTx = finalTransactions.find((tx: TransactionInfo) => 
          tx.description === expectedTx.description
        );

        expect(actualTx).toBeTruthy();
        expect(actualTx.type).toBe(expectedTx.type.toUpperCase());
        expect(actualTx.amount).toBe(expectedTx.amount);
        expect(actualTx.status).toBe('COMPLETED');
      }

      // Verify chronological order
      const sortedTransactions = [...finalTransactions].sort((a, b) => 
        new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      );

      for (let i = 0; i < sortedTransactions.length - 1; i++) {
        const current = new Date(sortedTransactions[i].createdAt);
        const next = new Date(sortedTransactions[i + 1].createdAt);
        expect(current.getTime()).toBeLessThanOrEqual(next.getTime());
      }

      logger.info('✅ Transaction history validation completed successfully');
      logger.testComplete('Transaction history validation throughout complete workflows', 'PASSED');

      testUsers.push(testUser);
    }, 90000);

    test('should provide accurate user transaction statistics after workflows', async () => {
      const testUser = createTestUser({
        username: `stats_user_${testRunId}`,
        accounts: [
          {
            accountType: 'CHECKING',
            initialBalance: 500.00,
            currency: 'USD'
          }
        ]
      });

      logger.testStart('User transaction statistics after workflows');

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

      // Perform known transactions for statistics validation
      const deposits = [100.00, 200.00, 150.00]; // Total: 450.00
      const withdrawals = [50.00, 75.00]; // Total: 125.00

      // Process deposits
      for (const amount of deposits) {
        await transactionClient.post('/api/transactions/deposit', {
          accountId: account.id,
          amount,
          currency: 'USD',
          description: 'Statistics test deposit'
        });
      }

      // Process withdrawals
      for (const amount of withdrawals) {
        await transactionClient.post('/api/transactions/withdraw', {
          accountId: account.id,
          amount,
          currency: 'USD',
          description: 'Statistics test withdrawal'
        });
      }

      // Get user transaction statistics
      logger.info('📊 Retrieving user transaction statistics');
      const statsResponse = await transactionClient.get('/api/transactions/user/stats');

      expect(statsResponse.status).toBe(200);
      expect(statsResponse.data).toHaveProperty('totalTransactions');
      expect(statsResponse.data).toHaveProperty('totalDeposits');
      expect(statsResponse.data).toHaveProperty('totalWithdrawals');

      const stats = statsResponse.data;
      
      expect(stats.totalTransactions).toBe(deposits.length + withdrawals.length);
      expect(stats.totalDeposits).toBe(deposits.length);
      expect(stats.totalWithdrawals).toBe(withdrawals.length);

      // Verify amounts if provided in stats
      if (stats.totalDepositAmount !== undefined) {
        const expectedDepositTotal = deposits.reduce((sum, amount) => sum + amount, 0);
        expect(stats.totalDepositAmount).toBe(expectedDepositTotal);
      }

      if (stats.totalWithdrawalAmount !== undefined) {
        const expectedWithdrawalTotal = withdrawals.reduce((sum, amount) => sum + amount, 0);
        expect(stats.totalWithdrawalAmount).toBe(expectedWithdrawalTotal);
      }

      logger.info('✅ User transaction statistics validated');
      logger.testComplete('User transaction statistics after workflows', 'PASSED');

      testUsers.push(testUser);
    }, 60000);
  });
});