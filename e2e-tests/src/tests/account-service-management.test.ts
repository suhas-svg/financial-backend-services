/**
 * Account Service Account Management Endpoint Tests
 * Tests account CRUD operations, retrieval, listing, updates, and deletion
 */

import { createAccountServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { generateTestId, createTestUser } from '../utils/test-helpers';
import { AccountInfo } from '../types';

describe('Account Service Account Management Endpoints', () => {
  let accountClient: ReturnType<typeof createAccountServiceClient>;
  let testUserId: string;
  let authToken: string;
  let testUser: { username: string; password: string };

  beforeAll(async () => {
    accountClient = createAccountServiceClient();
    testUserId = generateTestId();
    
    // Wait for service to be ready
    const isReady = await accountClient.waitForService();
    if (!isReady) {
      throw new Error('Account Service is not ready for testing');
    }

    // Register and login a test user for authenticated requests
    testUser = createTestUser({ username: `mgmttest_${testUserId}` });
    
    // Register user
    const registrationData = {
      username: testUser.username,
      password: testUser.password
    };
    const regResponse = await accountClient.post('/api/auth/register', registrationData);
    expect(regResponse.status).toBe(201);

    // Login to get token
    const loginData = {
      username: testUser.username,
      password: testUser.password
    };
    const loginResponse = await accountClient.post('/api/auth/login', loginData);
    expect(loginResponse.status).toBe(200);
    authToken = loginResponse.data.accessToken;
    
    // Set auth token for subsequent requests
    accountClient.setAuthToken(authToken);
    
    logger.info('Account Service Management Tests - Setup Complete');
  });

  afterAll(async () => {
    accountClient.clearAuthToken();
    logger.info('Account Service Management Tests - Cleanup Complete');
  });

  describe('Account Creation Endpoint', () => {
    describe('Valid Account Creation Scenarios', () => {
      it('should create a checking account with valid data', async () => {
        logger.testStart('Account Creation - Checking Account');
        
        const accountData = {
          ownerId: testUser.username,
          balance: 1000.00,
          accountType: 'CHECKING'
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/accounts', accountData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(201);
        expect(response.data).toBeDefined();
        expect(response.data.id).toBeDefined();
        expect(response.data.ownerId).toBe(accountData.ownerId);
        expect(parseFloat(response.data.balance)).toBe(accountData.balance);
        expect(response.data.accountType).toBe(accountData.accountType);
        expect(response.data.createdAt).toBeDefined();
        expect(response.duration).toBeGreaterThan(0);
        expect(response.duration).toBeLessThan(5000);

        logger.testComplete('Account Creation - Checking Account', 'PASSED', duration);
      }, 30000);

      it('should create a savings account with valid data', async () => {
        logger.testStart('Account Creation - Savings Account');
        
        const accountData = {
          ownerId: testUser.username,
          balance: 5000.00,
          accountType: 'SAVINGS',
          interestRate: 2.5
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/accounts', accountData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(201);
        expect(response.data).toBeDefined();
        expect(response.data.id).toBeDefined();
        expect(response.data.ownerId).toBe(accountData.ownerId);
        expect(parseFloat(response.data.balance)).toBe(accountData.balance);
        expect(response.data.accountType).toBe(accountData.accountType);
        expect(response.data.createdAt).toBeDefined();

        logger.testComplete('Account Creation - Savings Account', 'PASSED', duration);
      }, 30000);

      it('should create a credit account with valid data', async () => {
        logger.testStart('Account Creation - Credit Account');
        
        const accountData = {
          ownerId: testUser.username,
          balance: 0.00,
          accountType: 'CREDIT',
          creditLimit: 10000.00
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/accounts', accountData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(201);
        expect(response.data).toBeDefined();
        expect(response.data.id).toBeDefined();
        expect(response.data.ownerId).toBe(accountData.ownerId);
        expect(parseFloat(response.data.balance)).toBe(accountData.balance);
        expect(response.data.accountType).toBe(accountData.accountType);
        expect(response.data.createdAt).toBeDefined();

        logger.testComplete('Account Creation - Credit Account', 'PASSED', duration);
      }, 30000);

      it('should create multiple accounts for the same user', async () => {
        logger.testStart('Account Creation - Multiple Accounts');
        
        const accounts = [
          {
            ownerId: testUser.username,
            balance: 2000.00,
            accountType: 'CHECKING'
          },
          {
            ownerId: testUser.username,
            balance: 8000.00,
            accountType: 'SAVINGS',
            interestRate: 3.0
          }
        ];

        const startTime = Date.now();
        const createdAccounts = [];
        
        for (const accountData of accounts) {
          const response = await accountClient.post('/api/accounts', accountData);
          expect(response.status).toBe(201);
          expect(response.data.ownerId).toBe(accountData.ownerId);
          expect(response.data.accountType).toBe(accountData.accountType);
          createdAccounts.push(response.data);
        }

        // Verify all accounts were created with unique IDs
        const accountIds = createdAccounts.map(acc => acc.id);
        const uniqueIds = new Set(accountIds);
        expect(uniqueIds.size).toBe(accounts.length);

        const duration = Date.now() - startTime;
        logger.testComplete('Account Creation - Multiple Accounts', 'PASSED', duration);
      }, 45000);
    });

    describe('Invalid Account Creation Scenarios', () => {
      it('should reject account creation with missing ownerId', async () => {
        logger.testStart('Account Creation - Missing OwnerId');
        
        const invalidData = {
          balance: 1000.00,
          accountType: 'CHECKING'
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/accounts', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/ownerId.*required|Owner ID.*required/i);

        logger.testComplete('Account Creation - Missing OwnerId', 'PASSED', duration);
      }, 15000);

      it('should reject account creation with null balance', async () => {
        logger.testStart('Account Creation - Null Balance');
        
        const invalidData = {
          ownerId: testUser.username,
          balance: null,
          accountType: 'CHECKING'
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/accounts', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/balance.*required|Balance.*null/i);

        logger.testComplete('Account Creation - Null Balance', 'PASSED', duration);
      }, 15000);

      it('should reject account creation with negative balance', async () => {
        logger.testStart('Account Creation - Negative Balance');
        
        const invalidData = {
          ownerId: testUser.username,
          balance: -100.00,
          accountType: 'CHECKING'
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/accounts', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/balance.*positive|Balance.*zero.*positive/i);

        logger.testComplete('Account Creation - Negative Balance', 'PASSED', duration);
      }, 15000);

      it('should reject account creation with invalid account type', async () => {
        logger.testStart('Account Creation - Invalid Account Type');
        
        const invalidData = {
          ownerId: testUser.username,
          balance: 1000.00,
          accountType: 'INVALID_TYPE'
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/accounts', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/invalid.*account.*type|unknown.*type/i);

        logger.testComplete('Account Creation - Invalid Account Type', 'PASSED', duration);
      }, 15000);
    });
  });

  describe('Account Retrieval by ID Endpoint', () => {
    let testAccountId: string;

    beforeAll(async () => {
      // Create a test account for retrieval tests
      const accountData = {
        ownerId: testUser.username,
        balance: 1500.00,
        accountType: 'CHECKING'
      };

      const response = await accountClient.post('/api/accounts', accountData);
      expect(response.status).toBe(201);
      testAccountId = response.data.id;
    });

    describe('Valid Account Retrieval Scenarios', () => {
      it('should retrieve account by valid ID', async () => {
        logger.testStart('Account Retrieval - Valid ID');
        
        const startTime = Date.now();
        const response = await accountClient.get(`/api/accounts/${testAccountId}`);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        expect(response.data.id).toBe(testAccountId);
        expect(response.data.ownerId).toBe(testUser.username);
        expect(response.data.balance).toBeDefined();
        expect(response.data.accountType).toBeDefined();
        expect(response.data.createdAt).toBeDefined();
        expect(response.duration).toBeGreaterThan(0);
        expect(response.duration).toBeLessThan(3000);

        logger.testComplete('Account Retrieval - Valid ID', 'PASSED', duration);
      }, 15000);

      it('should retrieve account with all expected fields', async () => {
        logger.testStart('Account Retrieval - Complete Data Structure');
        
        const startTime = Date.now();
        const response = await accountClient.get(`/api/accounts/${testAccountId}`);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        const account = response.data;
        
        // Validate required fields
        expect(account.id).toBeDefined();
        expect(account.ownerId).toBeDefined();
        expect(account.balance).toBeDefined();
        expect(account.accountType).toBeDefined();
        expect(account.createdAt).toBeDefined();
        
        // Validate data types
        expect(typeof account.id).toBe('number');
        expect(typeof account.ownerId).toBe('string');
        expect(typeof account.balance).toBe('number');
        expect(typeof account.accountType).toBe('string');
        expect(typeof account.createdAt).toBe('string');

        logger.testComplete('Account Retrieval - Complete Data Structure', 'PASSED', duration);
      }, 15000);
    });

    describe('Invalid Account Retrieval Scenarios', () => {
      it('should return 404 for non-existent account ID', async () => {
        logger.testStart('Account Retrieval - Non-existent ID');
        
        const nonExistentId = 999999;
        const startTime = Date.now();
        const response = await accountClient.get(`/api/accounts/${nonExistentId}`);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(404);
        expect(response.data.message || response.data.error).toMatch(/not found|does not exist/i);

        logger.testComplete('Account Retrieval - Non-existent ID', 'PASSED', duration);
      }, 15000);

      it('should return 400 for invalid account ID format', async () => {
        logger.testStart('Account Retrieval - Invalid ID Format');
        
        const invalidId = 'invalid-id';
        const startTime = Date.now();
        const response = await accountClient.get(`/api/accounts/${invalidId}`);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/invalid.*id|bad.*request/i);

        logger.testComplete('Account Retrieval - Invalid ID Format', 'PASSED', duration);
      }, 15000);
    });
  });

  describe('Account Listing Endpoint', () => {
    let testAccounts: any[] = [];

    beforeAll(async () => {
      // Create multiple test accounts for listing tests
      const accountsData = [
        {
          ownerId: testUser.username,
          balance: 1000.00,
          accountType: 'CHECKING'
        },
        {
          ownerId: testUser.username,
          balance: 5000.00,
          accountType: 'SAVINGS'
        },
        {
          ownerId: `${testUser.username}_other`,
          balance: 2000.00,
          accountType: 'CHECKING'
        }
      ];

      for (const accountData of accountsData) {
        const response = await accountClient.post('/api/accounts', accountData);
        expect(response.status).toBe(201);
        testAccounts.push(response.data);
      }
    });

    describe('Basic Account Listing', () => {
      it('should list all accounts with default pagination', async () => {
        logger.testStart('Account Listing - Default Pagination');
        
        const startTime = Date.now();
        const response = await accountClient.get('/api/accounts');
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        expect(response.data.content).toBeDefined();
        expect(Array.isArray(response.data.content)).toBe(true);
        expect(response.data.content.length).toBeGreaterThan(0);
        
        // Validate pagination metadata
        expect(response.data.totalElements).toBeDefined();
        expect(response.data.totalPages).toBeDefined();
        expect(response.data.size).toBeDefined();
        expect(response.data.number).toBeDefined();

        logger.testComplete('Account Listing - Default Pagination', 'PASSED', duration);
      }, 15000);

      it('should list accounts with custom page size', async () => {
        logger.testStart('Account Listing - Custom Page Size');
        
        const pageSize = 2;
        const startTime = Date.now();
        const response = await accountClient.get(`/api/accounts?size=${pageSize}`);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data.content).toBeDefined();
        expect(response.data.content.length).toBeLessThanOrEqual(pageSize);
        expect(response.data.size).toBe(pageSize);

        logger.testComplete('Account Listing - Custom Page Size', 'PASSED', duration);
      }, 15000);

      it('should list accounts with pagination navigation', async () => {
        logger.testStart('Account Listing - Pagination Navigation');
        
        const pageSize = 1;
        const startTime = Date.now();
        
        // Get first page
        const firstPageResponse = await accountClient.get(`/api/accounts?page=0&size=${pageSize}`);
        expect(firstPageResponse.status).toBe(200);
        expect(firstPageResponse.data.number).toBe(0);
        
        // Get second page if available
        if (firstPageResponse.data.totalPages > 1) {
          const secondPageResponse = await accountClient.get(`/api/accounts?page=1&size=${pageSize}`);
          expect(secondPageResponse.status).toBe(200);
          expect(secondPageResponse.data.number).toBe(1);
          
          // Ensure different content
          expect(firstPageResponse.data.content[0].id).not.toBe(secondPageResponse.data.content[0].id);
        }

        const duration = Date.now() - startTime;
        logger.testComplete('Account Listing - Pagination Navigation', 'PASSED', duration);
      }, 20000);
    });

    describe('Account Listing with Sorting', () => {
      it('should list accounts sorted by balance ascending', async () => {
        logger.testStart('Account Listing - Sort by Balance ASC');
        
        const startTime = Date.now();
        const response = await accountClient.get('/api/accounts?sort=balance,asc');
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data.content).toBeDefined();
        
        // Verify sorting
        const balances = response.data.content.map((acc: any) => parseFloat(acc.balance));
        for (let i = 1; i < balances.length; i++) {
          expect(balances[i]).toBeGreaterThanOrEqual(balances[i - 1]);
        }

        logger.testComplete('Account Listing - Sort by Balance ASC', 'PASSED', duration);
      }, 15000);

      it('should list accounts sorted by balance descending', async () => {
        logger.testStart('Account Listing - Sort by Balance DESC');
        
        const startTime = Date.now();
        const response = await accountClient.get('/api/accounts?sort=balance,desc');
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data.content).toBeDefined();
        
        // Verify sorting
        const balances = response.data.content.map((acc: any) => parseFloat(acc.balance));
        for (let i = 1; i < balances.length; i++) {
          expect(balances[i]).toBeLessThanOrEqual(balances[i - 1]);
        }

        logger.testComplete('Account Listing - Sort by Balance DESC', 'PASSED', duration);
      }, 15000);

      it('should list accounts sorted by creation date', async () => {
        logger.testStart('Account Listing - Sort by Creation Date');
        
        const startTime = Date.now();
        const response = await accountClient.get('/api/accounts?sort=createdAt,desc');
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data.content).toBeDefined();
        
        // Verify sorting by date
        const dates = response.data.content.map((acc: any) => new Date(acc.createdAt));
        for (let i = 1; i < dates.length; i++) {
          expect(dates[i].getTime()).toBeLessThanOrEqual(dates[i - 1].getTime());
        }

        logger.testComplete('Account Listing - Sort by Creation Date', 'PASSED', duration);
      }, 15000);
    });
  });

  describe('Account Update Endpoint', () => {
    let testAccountId: string;

    beforeAll(async () => {
      // Create a test account for update tests
      const accountData = {
        ownerId: testUser.username,
        balance: 2000.00,
        accountType: 'CHECKING'
      };

      const response = await accountClient.post('/api/accounts', accountData);
      expect(response.status).toBe(201);
      testAccountId = response.data.id;
    });

    describe('Valid Account Update Scenarios', () => {
      it('should update account balance', async () => {
        logger.testStart('Account Update - Balance Update');
        
        const updateData = {
          ownerId: testUser.username,
          balance: 2500.00,
          accountType: 'CHECKING'
        };

        const startTime = Date.now();
        const response = await accountClient.put(`/api/accounts/${testAccountId}`, updateData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        expect(parseFloat(response.data.balance)).toBe(updateData.balance);
        expect(response.data.id).toBe(testAccountId);

        // Verify the update persisted
        const getResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
        expect(getResponse.status).toBe(200);
        expect(parseFloat(getResponse.data.balance)).toBe(updateData.balance);

        logger.testComplete('Account Update - Balance Update', 'PASSED', duration);
      }, 20000);

      it('should update account with validation', async () => {
        logger.testStart('Account Update - Data Validation');
        
        const updateData = {
          ownerId: testUser.username,
          balance: 3000.00,
          accountType: 'CHECKING'
        };

        const startTime = Date.now();
        const response = await accountClient.put(`/api/accounts/${testAccountId}`, updateData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data.ownerId).toBe(updateData.ownerId);
        expect(parseFloat(response.data.balance)).toBe(updateData.balance);
        expect(response.data.accountType).toBe(updateData.accountType);

        logger.testComplete('Account Update - Data Validation', 'PASSED', duration);
      }, 15000);
    });

    describe('Invalid Account Update Scenarios', () => {
      it('should reject update with negative balance', async () => {
        logger.testStart('Account Update - Negative Balance');
        
        const invalidData = {
          ownerId: testUser.username,
          balance: -500.00,
          accountType: 'CHECKING'
        };

        const startTime = Date.now();
        const response = await accountClient.put(`/api/accounts/${testAccountId}`, invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/balance.*positive|Balance.*zero.*positive/i);

        logger.testComplete('Account Update - Negative Balance', 'PASSED', duration);
      }, 15000);

      it('should reject update for non-existent account', async () => {
        logger.testStart('Account Update - Non-existent Account');
        
        const nonExistentId = 999999;
        const updateData = {
          ownerId: testUser.username,
          balance: 1000.00,
          accountType: 'CHECKING'
        };

        const startTime = Date.now();
        const response = await accountClient.put(`/api/accounts/${nonExistentId}`, updateData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(404);
        expect(response.data.message || response.data.error).toMatch(/not found|does not exist/i);

        logger.testComplete('Account Update - Non-existent Account', 'PASSED', duration);
      }, 15000);
    });
  });

  describe('Account Balance Update Endpoint', () => {
    let testAccountId: string;

    beforeAll(async () => {
      // Create a test account for balance update tests
      const accountData = {
        ownerId: testUser.username,
        balance: 1000.00,
        accountType: 'CHECKING'
      };

      const response = await accountClient.post('/api/accounts', accountData);
      expect(response.status).toBe(201);
      testAccountId = response.data.id;
    });

    describe('Valid Balance Update Scenarios', () => {
      it('should update account balance with valid positive amount', async () => {
        logger.testStart('Balance Update - Valid Positive Amount');
        
        const newBalance = 1500.00;
        const updateData = {
          balance: newBalance
        };

        const startTime = Date.now();
        const response = await accountClient.put(`/api/accounts/${testAccountId}/balance`, updateData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.duration).toBeGreaterThan(0);
        expect(response.duration).toBeLessThan(3000);

        // Verify the balance was updated by retrieving the account
        const getResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
        expect(getResponse.status).toBe(200);
        expect(parseFloat(getResponse.data.balance)).toBe(newBalance);

        logger.testComplete('Balance Update - Valid Positive Amount', 'PASSED', duration);
      }, 20000);

      it('should update account balance to zero', async () => {
        logger.testStart('Balance Update - Zero Balance');
        
        const newBalance = 0.00;
        const updateData = {
          balance: newBalance
        };

        const startTime = Date.now();
        const response = await accountClient.put(`/api/accounts/${testAccountId}/balance`, updateData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);

        // Verify the balance was updated
        const getResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
        expect(getResponse.status).toBe(200);
        expect(parseFloat(getResponse.data.balance)).toBe(newBalance);

        logger.testComplete('Balance Update - Zero Balance', 'PASSED', duration);
      }, 20000);

      it('should update account balance with decimal precision', async () => {
        logger.testStart('Balance Update - Decimal Precision');
        
        const newBalance = 1234.56;
        const updateData = {
          balance: newBalance
        };

        const startTime = Date.now();
        const response = await accountClient.put(`/api/accounts/${testAccountId}/balance`, updateData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);

        // Verify the balance was updated with correct precision
        const getResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
        expect(getResponse.status).toBe(200);
        expect(parseFloat(getResponse.data.balance)).toBe(newBalance);

        logger.testComplete('Balance Update - Decimal Precision', 'PASSED', duration);
      }, 20000);

      it('should update account balance multiple times sequentially', async () => {
        logger.testStart('Balance Update - Sequential Updates');
        
        const balanceUpdates = [2000.00, 2500.00, 1800.00, 3000.00];
        const startTime = Date.now();

        for (const newBalance of balanceUpdates) {
          const updateData = { balance: newBalance };
          const response = await accountClient.put(`/api/accounts/${testAccountId}/balance`, updateData);
          expect(response.status).toBe(200);

          // Verify each update
          const getResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
          expect(getResponse.status).toBe(200);
          expect(parseFloat(getResponse.data.balance)).toBe(newBalance);
        }

        const duration = Date.now() - startTime;
        logger.testComplete('Balance Update - Sequential Updates', 'PASSED', duration);
      }, 30000);
    });

    describe('Invalid Balance Update Scenarios', () => {
      it('should reject balance update with negative amount', async () => {
        logger.testStart('Balance Update - Negative Amount');
        
        const invalidBalance = -100.00;
        const updateData = {
          balance: invalidBalance
        };

        const startTime = Date.now();
        try {
          const response = await accountClient.put(`/api/accounts/${testAccountId}/balance`, updateData);
          // If no exception is thrown, the test should fail
          expect(response.status).toBe(400);
        } catch (error: any) {
          // Expected error case
          expect(error.status).toBe(400);
          expect(error.data.message || error.data.error).toMatch(/balance.*zero.*positive|Balance.*positive/i);
        }
        const duration = Date.now() - startTime;

        // Verify the balance was not changed
        const getResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
        expect(getResponse.status).toBe(200);
        expect(parseFloat(getResponse.data.balance)).not.toBe(invalidBalance);

        logger.testComplete('Balance Update - Negative Amount', 'PASSED', duration);
      }, 20000);

      it('should reject balance update with null balance', async () => {
        logger.testStart('Balance Update - Null Balance');
        
        const updateData = {
          balance: null
        };

        const startTime = Date.now();
        try {
          const response = await accountClient.put(`/api/accounts/${testAccountId}/balance`, updateData);
          expect(response.status).toBe(400);
        } catch (error: any) {
          expect(error.status).toBe(400);
          expect(error.data.message || error.data.error).toMatch(/balance.*required|Balance.*required/i);
        }
        const duration = Date.now() - startTime;

        logger.testComplete('Balance Update - Null Balance', 'PASSED', duration);
      }, 15000);

      it('should reject balance update with missing balance field', async () => {
        logger.testStart('Balance Update - Missing Balance Field');
        
        const updateData = {};

        const startTime = Date.now();
        try {
          const response = await accountClient.put(`/api/accounts/${testAccountId}/balance`, updateData);
          expect(response.status).toBe(400);
        } catch (error: any) {
          expect(error.status).toBe(400);
          expect(error.data.message || error.data.error).toMatch(/balance.*required|Balance.*required/i);
        }
        const duration = Date.now() - startTime;

        logger.testComplete('Balance Update - Missing Balance Field', 'PASSED', duration);
      }, 15000);

      it('should reject balance update with invalid balance format', async () => {
        logger.testStart('Balance Update - Invalid Balance Format');
        
        const updateData = {
          balance: 'invalid-amount'
        };

        const startTime = Date.now();
        try {
          const response = await accountClient.put(`/api/accounts/${testAccountId}/balance`, updateData);
          expect(response.status).toBe(400);
        } catch (error: any) {
          expect(error.status).toBe(400);
          expect(error.data.message || error.data.error).toMatch(/invalid.*format|bad.*request|number.*required|Malformed.*JSON|not.*valid.*representation/i);
        }
        const duration = Date.now() - startTime;

        logger.testComplete('Balance Update - Invalid Balance Format', 'PASSED', duration);
      }, 15000);

      it('should reject balance update for non-existent account', async () => {
        logger.testStart('Balance Update - Non-existent Account');
        
        const nonExistentId = 999999;
        const updateData = {
          balance: 1000.00
        };

        const startTime = Date.now();
        try {
          const response = await accountClient.put(`/api/accounts/${nonExistentId}/balance`, updateData);
          expect(response.status).toBe(404);
        } catch (error: any) {
          expect(error.status).toBe(404);
          expect(error.data.message || error.data.error).toMatch(/not found|does not exist/i);
        }
        const duration = Date.now() - startTime;

        logger.testComplete('Balance Update - Non-existent Account', 'PASSED', duration);
      }, 15000);

      it('should reject balance update with invalid account ID format', async () => {
        logger.testStart('Balance Update - Invalid Account ID Format');
        
        const invalidId = 'invalid-id';
        const updateData = {
          balance: 1000.00
        };

        const startTime = Date.now();
        try {
          const response = await accountClient.put(`/api/accounts/${invalidId}/balance`, updateData);
          expect(response.status).toBe(400);
        } catch (error: any) {
          expect([400, 500]).toContain(error.status);
          expect(error.data.message || error.data.error).toMatch(/invalid.*id|bad.*request|Failed.*convert.*value|Internal.*Server.*Error/i);
        }
        const duration = Date.now() - startTime;

        logger.testComplete('Balance Update - Invalid Account ID Format', 'PASSED', duration);
      }, 15000);
    });

    describe('Concurrent Balance Update Tests', () => {
      let concurrentTestAccountIds: string[] = [];

      beforeAll(async () => {
        // Create multiple test accounts for concurrent testing
        const accountsData = [
          { ownerId: testUser.username, balance: 1000.00, accountType: 'CHECKING' },
          { ownerId: testUser.username, balance: 2000.00, accountType: 'SAVINGS' },
          { ownerId: testUser.username, balance: 3000.00, accountType: 'CHECKING' }
        ];

        for (const accountData of accountsData) {
          const response = await accountClient.post('/api/accounts', accountData);
          expect(response.status).toBe(201);
          concurrentTestAccountIds.push(response.data.id);
        }
      });

      it('should handle concurrent balance updates on different accounts', async () => {
        logger.testStart('Balance Update - Concurrent Different Accounts');
        
        const startTime = Date.now();
        const updatePromises = concurrentTestAccountIds.map((accountId, index) => {
          const newBalance = (index + 1) * 1500.00;
          const updateData = { balance: newBalance };
          return accountClient.put(`/api/accounts/${accountId}/balance`, updateData);
        });

        const responses = await Promise.all(updatePromises);
        const duration = Date.now() - startTime;

        // Verify all updates succeeded
        responses.forEach(response => {
          expect(response.status).toBe(200);
        });

        // Verify final balances
        for (let i = 0; i < concurrentTestAccountIds.length; i++) {
          const getResponse = await accountClient.get(`/api/accounts/${concurrentTestAccountIds[i]}`);
          expect(getResponse.status).toBe(200);
          expect(parseFloat(getResponse.data.balance)).toBe((i + 1) * 1500.00);
        }

        logger.testComplete('Balance Update - Concurrent Different Accounts', 'PASSED', duration);
      }, 30000);

      it('should handle concurrent balance updates on same account with data consistency', async () => {
        logger.testStart('Balance Update - Concurrent Same Account');
        
        const targetAccountId = concurrentTestAccountIds[0];
        const finalBalance = 5000.00;
        const updateData = { balance: finalBalance };
        
        const startTime = Date.now();
        
        // Perform multiple concurrent updates to the same account
        const concurrentUpdates = Array(5).fill(null).map(() => 
          accountClient.put(`/api/accounts/${targetAccountId}/balance`, updateData)
        );

        const responses = await Promise.all(concurrentUpdates);
        const duration = Date.now() - startTime;

        // All requests should succeed (or some might fail due to race conditions, which is acceptable)
        const successfulResponses = responses.filter(response => response.status === 200);
        expect(successfulResponses.length).toBeGreaterThan(0);

        // Verify final balance is consistent
        const getResponse = await accountClient.get(`/api/accounts/${targetAccountId}`);
        expect(getResponse.status).toBe(200);
        expect(parseFloat(getResponse.data.balance)).toBe(finalBalance);

        logger.testComplete('Balance Update - Concurrent Same Account', 'PASSED', duration);
      }, 30000);

      it('should maintain data consistency during rapid sequential updates', async () => {
        logger.testStart('Balance Update - Rapid Sequential Updates');
        
        const targetAccountId = concurrentTestAccountIds[1];
        const balanceSequence = [1000.00, 1100.00, 1200.00, 1300.00, 1400.00, 1500.00];
        
        const startTime = Date.now();

        // Perform rapid sequential updates
        for (const balance of balanceSequence) {
          const updateData = { balance };
          const response = await accountClient.put(`/api/accounts/${targetAccountId}/balance`, updateData);
          expect(response.status).toBe(200);
        }

        // Verify final balance
        const getResponse = await accountClient.get(`/api/accounts/${targetAccountId}`);
        expect(getResponse.status).toBe(200);
        expect(parseFloat(getResponse.data.balance)).toBe(balanceSequence[balanceSequence.length - 1]);

        const duration = Date.now() - startTime;
        logger.testComplete('Balance Update - Rapid Sequential Updates', 'PASSED', duration);
      }, 25000);
    });
  });

  describe('Account Deletion Endpoint', () => {
    let testAccountId: string;

    beforeEach(async () => {
      // Create a fresh test account for each deletion test
      const accountData = {
        ownerId: testUser.username,
        balance: 1000.00,
        accountType: 'CHECKING'
      };

      const response = await accountClient.post('/api/accounts', accountData);
      expect(response.status).toBe(201);
      testAccountId = response.data.id;
    });

    describe('Valid Account Deletion Scenarios', () => {
      it('should delete account by valid ID', async () => {
        logger.testStart('Account Deletion - Valid ID');
        
        const startTime = Date.now();
        const response = await accountClient.delete(`/api/accounts/${testAccountId}`);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(204);
        expect(response.duration).toBeGreaterThan(0);
        expect(response.duration).toBeLessThan(3000);

        // Verify account is deleted
        const getResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
        expect(getResponse.status).toBe(404);

        logger.testComplete('Account Deletion - Valid ID', 'PASSED', duration);
      }, 20000);

      it('should handle deletion with proper cleanup validation', async () => {
        logger.testStart('Account Deletion - Cleanup Validation');
        
        // Verify account exists before deletion
        const preDeleteResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
        expect(preDeleteResponse.status).toBe(200);

        const startTime = Date.now();
        const deleteResponse = await accountClient.delete(`/api/accounts/${testAccountId}`);
        const duration = Date.now() - startTime;

        expect(deleteResponse.status).toBe(204);

        // Verify account no longer exists
        const postDeleteResponse = await accountClient.get(`/api/accounts/${testAccountId}`);
        expect(postDeleteResponse.status).toBe(404);

        // Verify account doesn't appear in listings
        const listResponse = await accountClient.get('/api/accounts');
        expect(listResponse.status).toBe(200);
        const accountExists = listResponse.data.content.some((acc: any) => acc.id === testAccountId);
        expect(accountExists).toBe(false);

        logger.testComplete('Account Deletion - Cleanup Validation', 'PASSED', duration);
      }, 25000);
    });

    describe('Invalid Account Deletion Scenarios', () => {
      it('should return 404 for non-existent account deletion', async () => {
        logger.testStart('Account Deletion - Non-existent Account');
        
        const nonExistentId = 999999;
        const startTime = Date.now();
        const response = await accountClient.delete(`/api/accounts/${nonExistentId}`);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(404);
        expect(response.data.message || response.data.error).toMatch(/not found|does not exist/i);

        logger.testComplete('Account Deletion - Non-existent Account', 'PASSED', duration);
      }, 15000);

      it('should return 400 for invalid account ID format', async () => {
        logger.testStart('Account Deletion - Invalid ID Format');
        
        const invalidId = 'invalid-id';
        const startTime = Date.now();
        const response = await accountClient.delete(`/api/accounts/${invalidId}`);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/invalid.*id|bad.*request/i);

        logger.testComplete('Account Deletion - Invalid ID Format', 'PASSED', duration);
      }, 15000);

      it('should handle double deletion gracefully', async () => {
        logger.testStart('Account Deletion - Double Deletion');
        
        // First deletion should succeed
        const firstDeleteResponse = await accountClient.delete(`/api/accounts/${testAccountId}`);
        expect(firstDeleteResponse.status).toBe(204);

        // Second deletion should return 404
        const startTime = Date.now();
        const secondDeleteResponse = await accountClient.delete(`/api/accounts/${testAccountId}`);
        const duration = Date.now() - startTime;

        expect(secondDeleteResponse.status).toBe(404);
        expect(secondDeleteResponse.data.message || secondDeleteResponse.data.error).toMatch(/not found|does not exist/i);

        logger.testComplete('Account Deletion - Double Deletion', 'PASSED', duration);
      }, 20000);
    });
  });
});