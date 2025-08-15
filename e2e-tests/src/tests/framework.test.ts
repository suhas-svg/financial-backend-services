/**
 * Framework validation tests
 * Verifies that the E2E testing framework is properly set up and configured
 */

import { testConfig } from '../config/test-config';
import { E2ETestRunner } from '../core/test-runner';
import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { logger } from '../utils/logger';
import { generateTestId, createTestUser, isValidJwtToken } from '../utils/test-helpers';

describe('E2E Testing Framework', () => {
  describe('Configuration Management', () => {
    test('should load and validate test configuration', () => {
      const config = testConfig.getConfig();
      
      expect(config).toBeDefined();
      expect(config.services).toBeDefined();
      expect(config.services.accountService).toBeDefined();
      expect(config.services.transactionService).toBeDefined();
      expect(config.databases).toBeDefined();
      expect(config.cache).toBeDefined();
      expect(config.testData).toBeDefined();
      expect(config.performance).toBeDefined();
      expect(config.reporting).toBeDefined();
      expect(config.timeouts).toBeDefined();
    });

    test('should validate service URLs', () => {
      const serviceConfig = testConfig.getServiceConfig();
      
      expect(serviceConfig.accountService.url).toMatch(/^https?:\/\/.+/);
      expect(serviceConfig.transactionService.url).toMatch(/^https?:\/\/.+/);
      expect(serviceConfig.accountService.healthEndpoint).toBeDefined();
      expect(serviceConfig.transactionService.healthEndpoint).toBeDefined();
    });

    test('should validate database configuration', () => {
      const dbConfig = testConfig.getDatabaseConfig();
      
      expect(dbConfig.accountDb.host).toBeDefined();
      expect(dbConfig.accountDb.port).toBeGreaterThan(0);
      expect(dbConfig.accountDb.port).toBeLessThan(65536);
      expect(dbConfig.accountDb.database).toBeDefined();
      expect(dbConfig.accountDb.username).toBeDefined();
      
      expect(dbConfig.transactionDb.host).toBeDefined();
      expect(dbConfig.transactionDb.port).toBeGreaterThan(0);
      expect(dbConfig.transactionDb.port).toBeLessThan(65536);
      expect(dbConfig.transactionDb.database).toBeDefined();
      expect(dbConfig.transactionDb.username).toBeDefined();
    });

    test('should validate cache configuration', () => {
      const cacheConfig = testConfig.getCacheConfig();
      
      expect(cacheConfig.redis.host).toBeDefined();
      expect(cacheConfig.redis.port).toBeGreaterThan(0);
      expect(cacheConfig.redis.port).toBeLessThan(65536);
    });

    test('should validate performance configuration', () => {
      const perfConfig = testConfig.getPerformanceConfig();
      
      expect(perfConfig.concurrentUsers).toBeGreaterThan(0);
      expect(perfConfig.testDuration).toBeGreaterThan(0);
      expect(perfConfig.rampUpTime).toBeGreaterThanOrEqual(0);
      expect(perfConfig.thresholds.averageResponseTime).toBeGreaterThan(0);
      expect(perfConfig.thresholds.p95ResponseTime).toBeGreaterThan(0);
      expect(perfConfig.thresholds.errorRate).toBeGreaterThanOrEqual(0);
      expect(perfConfig.thresholds.errorRate).toBeLessThanOrEqual(1);
    });
  });

  describe('HTTP Client', () => {
    test('should create account service client', () => {
      const client = createAccountServiceClient();
      
      expect(client).toBeDefined();
      expect(client.getBaseURL()).toMatch(/^https?:\/\/.+/);
    });

    test('should create transaction service client', () => {
      const client = createTransactionServiceClient();
      
      expect(client).toBeDefined();
      expect(client.getBaseURL()).toMatch(/^https?:\/\/.+/);
    });
  });

  describe('Test Runner', () => {
    test('should create test runner instance', () => {
      const runner = new E2ETestRunner();
      
      expect(runner).toBeDefined();
      expect(runner.getTestRunId()).toBeDefined();
      expect(runner.getTestRunId()).toMatch(/^run_\d+_[a-z0-9]+$/);
    });

    test('should initialize and run framework validation', async () => {
      const runner = new E2ETestRunner();
      const results = await runner.runAllTests();
      
      expect(results).toBeDefined();
      expect(results.testRun).toBeDefined();
      expect(results.testRun.id).toBeDefined();
      expect(results.testRun.startTime).toBeDefined();
      expect(results.testRun.endTime).toBeDefined();
      expect(results.testRun.duration).toBeGreaterThanOrEqual(0);
      expect(results.suites).toBeDefined();
      expect(results.metrics).toBeDefined();
      expect(results.errors).toBeDefined();
    });
  });

  describe('Test Helpers', () => {
    test('should generate unique test IDs', () => {
      const id1 = generateTestId();
      const id2 = generateTestId();
      
      expect(id1).toBeDefined();
      expect(id2).toBeDefined();
      expect(id1).not.toBe(id2);
      expect(id1).toMatch(/^test_\d+_[a-z0-9]+$/);
    });

    test('should create test user data', () => {
      const user = createTestUser();
      
      expect(user).toBeDefined();
      expect(user.username).toBeDefined();
      expect(user.password).toBeDefined();
      expect(user.email).toBeDefined();
      expect(user.accounts).toBeDefined();
      expect(user.accounts).toHaveLength(1);
      expect(user.accounts![0].accountType).toBe('CHECKING');
      expect(user.accounts![0].initialBalance).toBe(1000.00);
    });

    test('should validate JWT token format', () => {
      const validToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c';
      const invalidToken = 'invalid.token';
      
      expect(isValidJwtToken(validToken)).toBe(true);
      expect(isValidJwtToken(invalidToken)).toBe(false);
      expect(isValidJwtToken('')).toBe(false);
      expect(isValidJwtToken(null as any)).toBe(false);
    });
  });

  describe('Custom Jest Matchers', () => {
    test('should have custom API response matcher', () => {
      const validResponse = {
        status: 200,
        statusText: 'OK',
        headers: { 'content-type': 'application/json' },
        duration: 150,
        data: { message: 'success' }
      };

      expect(validResponse).toHaveValidApiResponse();
    });

    test('should have custom HTTP status matcher', () => {
      const response = {
        status: 200,
        statusText: 'OK',
        headers: {},
        duration: 100
      };

      expect(response).toHaveHttpStatus(200);
    });

    test('should have custom response time matcher', () => {
      const fastResponse = {
        status: 200,
        statusText: 'OK',
        headers: {},
        duration: 100
      };

      expect(fastResponse).toHaveResponseTime(500);
    });

    test('should have custom JWT token matcher', () => {
      const validToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c';
      
      expect(validToken).toHaveValidJwtToken();
    });

    test('should have custom account structure matcher', () => {
      const validAccount = {
        id: 'acc_123',
        ownerId: 'user_456',
        accountType: 'CHECKING',
        balance: 1000.00,
        currency: 'USD',
        status: 'ACTIVE',
        createdAt: '2024-01-15T10:30:00Z',
        updatedAt: '2024-01-15T10:30:00Z'
      };

      expect(validAccount).toHaveValidAccountStructure();
    });

    test('should have custom transaction structure matcher', () => {
      const validTransaction = {
        id: 'txn_123',
        accountId: 'acc_456',
        type: 'DEPOSIT',
        amount: 100.00,
        currency: 'USD',
        status: 'COMPLETED',
        description: 'Test deposit',
        createdAt: '2024-01-15T10:30:00Z',
        updatedAt: '2024-01-15T10:30:00Z'
      };

      expect(validTransaction).toHaveValidTransactionStructure();
    });

    test('should have custom range matcher', () => {
      expect(50).toBeWithinRange(1, 100);
      expect(0).toBeWithinRange(0, 10);
      expect(100).toBeWithinRange(50, 100);
    });
  });

  describe('Logger', () => {
    test('should log test events', () => {
      // Test that logger doesn't throw errors
      expect(() => {
        logger.info('Test info message');
        logger.warn('Test warning message');
        logger.debug('Test debug message');
        logger.testStart('sample-test', 'framework-suite');
        logger.testComplete('sample-test', 'PASSED', 100);
        logger.suiteStart('framework-suite');
        logger.suiteComplete('framework-suite', { status: 'PASSED' });
      }).not.toThrow();
    });
  });
});