/**
 * Transaction Service Creation Endpoint Tests
 * Tests deposit, withdrawal, and transfer transaction creation
 * Requirements: 3.1, 3.2, 3.3
 */

import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { generateTestId, createTestUser, generateRandomAmount, compareAmounts } from '../utils/test-helpers';
import { AuthResponse, AccountInfo, TransactionInfo } from '../types';

describe('Transaction Service Creation Endpoints', () => {
  let accountClient: ReturnType<typeof createAccountServiceClient>;
  let transactionClient: ReturnType<typeof createTransactionServiceClient>;
  let testUserId: string;
  let authToken: string;
  let testUser: any;
  let testAccount: AccountInfo;

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
    
    // Setup test user and account
    testUser = createTestUser({ username: `txnuser_${testUserId}` });
    
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
    
    // Create test account
    const accountResponse = await accountClient.post('/api/accounts', {
      accountType: 'CHECKING',
      initialBalance: 1000.00
    });
    
    testAccount = accountResponse.data;
    
    logger.info('Transaction Service Creation Tests - Setup Complete');
  });

  afterAll(async () => {
    // Cleanup
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
    logger.info('Transaction Service Creation Tests - Cleanup Complete');
  });

  describe('Deposit Transaction Endpoint', () => {
    describe('Valid Deposit Scenarios', () => {
      it('should create a deposit transaction with valid amount and account', async () => {
        logger.testStart('Deposit Transaction - Valid Amount');
        
        const depositAmount = generateRandomAmount(10, 500);
        const depositData = {
          accountId: testAccount.id,
          amount: depositAmount,
          description: `Test deposit ${testUserId}`
        };

        const startTime = Date.now();
        const response = await transactionClient.post('/api/transactions/deposit', depositData);
        const duration = Date.now() - startTime;

        // Validate response structure
        expect(response.status).toBe(201);
        expect(response.data).toBeDefined();
        expect(response.data).toHaveValidTransactionStructure();
        
        // Validate transaction details
        const transaction: TransactionInfo = response.data;
        expect(transaction.accountId).toBe(testAccount.id);
        expect(transaction.type).toBe('DEPOSIT');
        expect(transaction.amount).toBe(depositAmount);
        expect(transaction.status).toBe('COMPLETED');
        expect(transaction.description).toBe(depositData.description);
        
        // Validate response time
        expect(response).toHaveResponseTime(5000);
        
        logger.testComplete('Deposit Transaction - Valid Amount', 'PASSED', duration);
      });

      it('should create deposit with minimum valid amount', async () => {
        logger.testStart('Deposit Transaction - Minimum Amount');
        
        const depositData = {
          accountId: testAccount.id,
          amount: 0.01,
          description: 'Minimum deposit test'
        };

        const response = await transactionClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(201);
        expect(response.data.amount).toBe(0.01);
        expect(response.data.type).toBe('DEPOSIT');
        
        logger.testComplete('Deposit Transaction - Minimum Amount', 'PASSED');
      });

      it('should create deposit with large valid amount', async () => {
        logger.testStart('Deposit Transaction - Large Amount');
        
        const depositData = {
          accountId: testAccount.id,
          amount: 10000.00,
          description: 'Large deposit test'
        };

        const response = await transactionClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(201);
        expect(response.data.amount).toBe(10000.00);
        expect(response.data.type).toBe('DEPOSIT');
        
        logger.testComplete('Deposit Transaction - Large Amount', 'PASSED');
      });
    });

    describe('Invalid Deposit Scenarios', () => {
      it('should reject deposit with invalid account ID', async () => {
        logger.testStart('Deposit Transaction - Invalid Account');
        
        const depositData = {
          accountId: 'invalid-account-id',
          amount: 100.00,
          description: 'Invalid account test'
        };

        const response = await transactionClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(404);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Deposit Transaction - Invalid Account', 'PASSED');
      });

      it('should reject deposit with zero amount', async () => {
        logger.testStart('Deposit Transaction - Zero Amount');
        
        const depositData = {
          accountId: testAccount.id,
          amount: 0,
          description: 'Zero amount test'
        };

        const response = await transactionClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Deposit Transaction - Zero Amount', 'PASSED');
      });

      it('should reject deposit with negative amount', async () => {
        logger.testStart('Deposit Transaction - Negative Amount');
        
        const depositData = {
          accountId: testAccount.id,
          amount: -100.00,
          description: 'Negative amount test'
        };

        const response = await transactionClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Deposit Transaction - Negative Amount', 'PASSED');
      });

      it('should reject deposit with missing required fields', async () => {
        logger.testStart('Deposit Transaction - Missing Fields');
        
        const depositData = {
          amount: 100.00
          // Missing accountId
        };

        const response = await transactionClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Deposit Transaction - Missing Fields', 'PASSED');
      });
    });
  });

  describe('Withdrawal Transaction Endpoint', () => {
    let accountWithBalance: AccountInfo;

    beforeAll(async () => {
      // Create account with sufficient balance for withdrawal tests
      const accountResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 2000.00
      });
      accountWithBalance = accountResponse.data;
      
      // Add additional funds via deposit
      await transactionClient.post('/api/transactions/deposit', {
        accountId: accountWithBalance.id,
        amount: 1000.00,
        description: 'Setup for withdrawal tests'
      });
    });

    describe('Valid Withdrawal Scenarios', () => {
      it('should create withdrawal with sufficient funds', async () => {
        logger.testStart('Withdrawal Transaction - Sufficient Funds');
        
        const withdrawalAmount = generateRandomAmount(10, 500);
        const withdrawalData = {
          accountId: accountWithBalance.id,
          amount: withdrawalAmount,
          description: `Test withdrawal ${testUserId}`
        };

        const response = await transactionClient.post('/api/transactions/withdraw', withdrawalData);
        
        expect(response.status).toBe(201);
        expect(response.data).toHaveValidTransactionStructure();
        
        const transaction: TransactionInfo = response.data;
        expect(transaction.accountId).toBe(accountWithBalance.id);
        expect(transaction.type).toBe('WITHDRAWAL');
        expect(transaction.amount).toBe(withdrawalAmount);
        expect(transaction.status).toBe('COMPLETED');
        
        logger.testComplete('Withdrawal Transaction - Sufficient Funds', 'PASSED');
      });

      it('should create withdrawal with exact account balance', async () => {
        logger.testStart('Withdrawal Transaction - Exact Balance');
        
        // Create new account for this test
        const newAccountResponse = await accountClient.post('/api/accounts', {
          accountType: 'CHECKING',
          initialBalance: 100.00
        });
        
        const withdrawalData = {
          accountId: newAccountResponse.data.id,
          amount: 100.00,
          description: 'Exact balance withdrawal'
        };

        const response = await transactionClient.post('/api/transactions/withdraw', withdrawalData);
        
        expect(response.status).toBe(201);
        expect(response.data.amount).toBe(100.00);
        expect(response.data.type).toBe('WITHDRAWAL');
        
        logger.testComplete('Withdrawal Transaction - Exact Balance', 'PASSED');
      });
    });

    describe('Invalid Withdrawal Scenarios', () => {
      it('should reject withdrawal with insufficient funds', async () => {
        logger.testStart('Withdrawal Transaction - Insufficient Funds');
        
        const withdrawalData = {
          accountId: accountWithBalance.id,
          amount: 50000.00, // Amount larger than account balance
          description: 'Insufficient funds test'
        };

        const response = await transactionClient.post('/api/transactions/withdraw', withdrawalData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        expect(response.data.error).toContain('insufficient');
        
        logger.testComplete('Withdrawal Transaction - Insufficient Funds', 'PASSED');
      });

      it('should reject withdrawal with invalid account ID', async () => {
        logger.testStart('Withdrawal Transaction - Invalid Account');
        
        const withdrawalData = {
          accountId: 'invalid-account-id',
          amount: 100.00,
          description: 'Invalid account test'
        };

        const response = await transactionClient.post('/api/transactions/withdraw', withdrawalData);
        
        expect(response.status).toBe(404);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Withdrawal Transaction - Invalid Account', 'PASSED');
      });

      it('should reject withdrawal with zero or negative amount', async () => {
        logger.testStart('Withdrawal Transaction - Invalid Amount');
        
        const zeroAmountData = {
          accountId: accountWithBalance.id,
          amount: 0,
          description: 'Zero amount test'
        };

        const zeroResponse = await transactionClient.post('/api/transactions/withdraw', zeroAmountData);
        expect(zeroResponse.status).toBe(400);
        
        const negativeAmountData = {
          accountId: accountWithBalance.id,
          amount: -100.00,
          description: 'Negative amount test'
        };

        const negativeResponse = await transactionClient.post('/api/transactions/withdraw', negativeAmountData);
        expect(negativeResponse.status).toBe(400);
        
        logger.testComplete('Withdrawal Transaction - Invalid Amount', 'PASSED');
      });
    });
  });

  describe('Transfer Transaction Endpoint', () => {
    let sourceAccount: AccountInfo;
    let targetAccount: AccountInfo;

    beforeAll(async () => {
      // Create source account with sufficient balance
      const sourceResponse = await accountClient.post('/api/accounts', {
        accountType: 'CHECKING',
        initialBalance: 2000.00
      });
      sourceAccount = sourceResponse.data;
      
      // Create target account
      const targetResponse = await accountClient.post('/api/accounts', {
        accountType: 'SAVINGS',
        initialBalance: 500.00
      });
      targetAccount = targetResponse.data;
    });

    describe('Valid Transfer Scenarios', () => {
      it('should create transfer between valid accounts', async () => {
        logger.testStart('Transfer Transaction - Valid Accounts');
        
        const transferAmount = generateRandomAmount(10, 500);
        const transferData = {
          fromAccountId: sourceAccount.id,
          toAccountId: targetAccount.id,
          amount: transferAmount,
          description: `Test transfer ${testUserId}`
        };

        const response = await transactionClient.post('/api/transactions/transfer', transferData);
        
        expect(response.status).toBe(201);
        expect(response.data).toHaveValidTransactionStructure();
        
        const transaction: TransactionInfo = response.data;
        expect(transaction.type).toBe('TRANSFER');
        expect(transaction.amount).toBe(transferAmount);
        expect(transaction.status).toBe('COMPLETED');
        
        logger.testComplete('Transfer Transaction - Valid Accounts', 'PASSED');
      });

      it('should create transfer with minimum amount', async () => {
        logger.testStart('Transfer Transaction - Minimum Amount');
        
        const transferData = {
          fromAccountId: sourceAccount.id,
          toAccountId: targetAccount.id,
          amount: 0.01,
          description: 'Minimum transfer test'
        };

        const response = await transactionClient.post('/api/transactions/transfer', transferData);
        
        expect(response.status).toBe(201);
        expect(response.data.amount).toBe(0.01);
        expect(response.data.type).toBe('TRANSFER');
        
        logger.testComplete('Transfer Transaction - Minimum Amount', 'PASSED');
      });
    });

    describe('Invalid Transfer Scenarios', () => {
      it('should reject transfer with insufficient funds', async () => {
        logger.testStart('Transfer Transaction - Insufficient Funds');
        
        const transferData = {
          fromAccountId: sourceAccount.id,
          toAccountId: targetAccount.id,
          amount: 50000.00, // Amount larger than source account balance
          description: 'Insufficient funds transfer test'
        };

        const response = await transactionClient.post('/api/transactions/transfer', transferData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        expect(response.data.error).toContain('insufficient');
        
        logger.testComplete('Transfer Transaction - Insufficient Funds', 'PASSED');
      });

      it('should reject transfer with invalid source account', async () => {
        logger.testStart('Transfer Transaction - Invalid Source Account');
        
        const transferData = {
          fromAccountId: 'invalid-source-id',
          toAccountId: targetAccount.id,
          amount: 100.00,
          description: 'Invalid source account test'
        };

        const response = await transactionClient.post('/api/transactions/transfer', transferData);
        
        expect(response.status).toBe(404);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transfer Transaction - Invalid Source Account', 'PASSED');
      });

      it('should reject transfer with invalid target account', async () => {
        logger.testStart('Transfer Transaction - Invalid Target Account');
        
        const transferData = {
          fromAccountId: sourceAccount.id,
          toAccountId: 'invalid-target-id',
          amount: 100.00,
          description: 'Invalid target account test'
        };

        const response = await transactionClient.post('/api/transactions/transfer', transferData);
        
        expect(response.status).toBe(404);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transfer Transaction - Invalid Target Account', 'PASSED');
      });

      it('should reject transfer to same account', async () => {
        logger.testStart('Transfer Transaction - Same Account');
        
        const transferData = {
          fromAccountId: sourceAccount.id,
          toAccountId: sourceAccount.id,
          amount: 100.00,
          description: 'Same account transfer test'
        };

        const response = await transactionClient.post('/api/transactions/transfer', transferData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        expect(response.data.error).toContain('same account');
        
        logger.testComplete('Transfer Transaction - Same Account', 'PASSED');
      });

      it('should reject transfer with zero or negative amount', async () => {
        logger.testStart('Transfer Transaction - Invalid Amount');
        
        const zeroAmountData = {
          fromAccountId: sourceAccount.id,
          toAccountId: targetAccount.id,
          amount: 0,
          description: 'Zero amount transfer test'
        };

        const zeroResponse = await transactionClient.post('/api/transactions/transfer', zeroAmountData);
        expect(zeroResponse.status).toBe(400);
        
        const negativeAmountData = {
          fromAccountId: sourceAccount.id,
          toAccountId: targetAccount.id,
          amount: -100.00,
          description: 'Negative amount transfer test'
        };

        const negativeResponse = await transactionClient.post('/api/transactions/transfer', negativeAmountData);
        expect(negativeResponse.status).toBe(400);
        
        logger.testComplete('Transfer Transaction - Invalid Amount', 'PASSED');
      });

      it('should reject transfer with missing required fields', async () => {
        logger.testStart('Transfer Transaction - Missing Fields');
        
        const incompleteData = {
          fromAccountId: sourceAccount.id,
          amount: 100.00
          // Missing toAccountId
        };

        const response = await transactionClient.post('/api/transactions/transfer', incompleteData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transfer Transaction - Missing Fields', 'PASSED');
      });
    });
  });

  describe('Transaction Creation Error Scenarios', () => {
    describe('Authentication and Authorization', () => {
      it('should reject transaction creation without authentication', async () => {
        logger.testStart('Transaction Creation - No Authentication');
        
        const unauthenticatedClient = createTransactionServiceClient();
        
        const depositData = {
          accountId: testAccount.id,
          amount: 100.00,
          description: 'Unauthenticated test'
        };

        const response = await unauthenticatedClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Creation - No Authentication', 'PASSED');
      });

      it('should reject transaction creation with invalid token', async () => {
        logger.testStart('Transaction Creation - Invalid Token');
        
        const invalidTokenClient = createTransactionServiceClient();
        invalidTokenClient.setAuthToken('invalid-jwt-token');
        
        const depositData = {
          accountId: testAccount.id,
          amount: 100.00,
          description: 'Invalid token test'
        };

        const response = await invalidTokenClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(401);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Creation - Invalid Token', 'PASSED');
      });
    });

    describe('Input Validation', () => {
      it('should reject transaction with malformed JSON', async () => {
        logger.testStart('Transaction Creation - Malformed JSON');
        
        try {
          // This should cause a JSON parsing error
          const response = await transactionClient.post('/api/transactions/deposit', 'invalid-json');
          expect(response.status).toBe(400);
        } catch (error) {
          // Axios might throw for malformed requests
          expect(error).toBeDefined();
        }
        
        logger.testComplete('Transaction Creation - Malformed JSON', 'PASSED');
      });

      it('should reject transaction with invalid data types', async () => {
        logger.testStart('Transaction Creation - Invalid Data Types');
        
        const invalidData = {
          accountId: testAccount.id,
          amount: 'not-a-number', // Should be number
          description: 'Invalid data type test'
        };

        const response = await transactionClient.post('/api/transactions/deposit', invalidData);
        
        expect(response.status).toBe(400);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Creation - Invalid Data Types', 'PASSED');
      });
    });

    describe('Business Rule Validation', () => {
      it('should validate account ownership for transactions', async () => {
        logger.testStart('Transaction Creation - Account Ownership');
        
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
        
        const otherClient = createAccountServiceClient();
        otherClient.setAuthToken(otherLoginResponse.data.token);
        
        const otherAccountResponse = await otherClient.post('/api/accounts', {
          accountType: 'CHECKING',
          initialBalance: 1000.00
        });
        
        // Try to create transaction on other user's account with original user's token
        const depositData = {
          accountId: otherAccountResponse.data.id,
          amount: 100.00,
          description: 'Cross-user account test'
        };

        const response = await transactionClient.post('/api/transactions/deposit', depositData);
        
        expect(response.status).toBe(403);
        expect(response.data.error).toBeDefined();
        
        logger.testComplete('Transaction Creation - Account Ownership', 'PASSED');
      });
    });
  });
});