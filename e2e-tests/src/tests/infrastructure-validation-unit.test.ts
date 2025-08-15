import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { DatabaseValidator } from '../utils/database-validator';
import { RedisValidator } from '../utils/redis-validator';
import { ServiceValidator } from '../utils/service-validator';

/**
 * Infrastructure Validation Unit Tests
 * Tests the infrastructure validation classes and configuration without requiring actual infrastructure
 */
describe('Infrastructure Validation Unit Tests', () => {
  const config = testConfig.getConfig();
  
  beforeAll(async () => {
    logger.suiteStart('Infrastructure Validation Unit Tests');
  });

  afterAll(async () => {
    logger.suiteComplete('Infrastructure Validation Unit Tests', {
      message: 'Infrastructure validation unit tests completed'
    });
  });

  describe('Configuration Validation', () => {
    it('should have valid test configuration', () => {
      expect(config).toBeDefined();
      expect(config.services).toBeDefined();
      expect(config.databases).toBeDefined();
      expect(config.cache).toBeDefined();
    });

    it('should validate configuration successfully', () => {
      expect(() => testConfig.validateConfiguration()).not.toThrow();
    });

    it('should have valid database configurations', () => {
      expect(config.databases.accountDb).toBeDefined();
      expect(config.databases.accountDb.host).toBe('localhost');
      expect(config.databases.accountDb.port).toBeGreaterThan(5000);
      expect(config.databases.accountDb.database).toContain('account');

      expect(config.databases.transactionDb).toBeDefined();
      expect(config.databases.transactionDb.host).toBe('localhost');
      expect(config.databases.transactionDb.port).toBeGreaterThan(5000);
      expect(config.databases.transactionDb.database).toContain('transaction');
    });

    it('should have valid Redis configuration', () => {
      expect(config.cache.redis).toBeDefined();
      expect(config.cache.redis.host).toBe('localhost');
      expect(config.cache.redis.port).toBeGreaterThan(6000);
    });

    it('should have valid service configurations', () => {
      expect(config.services.accountService).toBeDefined();
      expect(config.services.accountService.url).toContain('localhost');
      expect(config.services.accountService.healthEndpoint).toBe('/actuator/health');

      expect(config.services.transactionService).toBeDefined();
      expect(config.services.transactionService.url).toContain('localhost');
      expect(config.services.transactionService.healthEndpoint).toBe('/actuator/health');
    });
  });

  describe('Database Validator Class Tests', () => {
    it('should create database validator instances', () => {
      const accountValidator = new DatabaseValidator(config.databases.accountDb);
      const transactionValidator = new DatabaseValidator(config.databases.transactionDb);

      expect(accountValidator).toBeInstanceOf(DatabaseValidator);
      expect(transactionValidator).toBeInstanceOf(DatabaseValidator);
    });

    it('should handle connection errors gracefully', async () => {
      // Use invalid configuration that will definitely fail
      const invalidConfig = {
        host: 'invalid-host-that-does-not-exist',
        port: 9999,
        database: 'nonexistent_db',
        username: 'invalid_user',
        password: 'invalid_password',
        connectionTimeout: 1000
      };
      
      const validator = new DatabaseValidator(invalidConfig);
      
      // This should fail gracefully and throw an error with proper message
      await expect(validator.validateConnectivity(1, 100)).rejects.toThrow(
        /Database connectivity failed after 1 attempts/
      );
    });
  });

  describe('Redis Validator Class Tests', () => {
    it('should create Redis validator instance', () => {
      const validator = new RedisValidator(config.cache.redis);
      expect(validator).toBeInstanceOf(RedisValidator);
    });

    it('should handle connection errors gracefully', async () => {
      // Use invalid configuration that will definitely fail quickly
      const invalidConfig = {
        host: 'localhost',
        port: 9999, // Invalid port that should fail quickly
        password: 'invalid_password',
        connectionTimeout: 1000 // Short timeout
      };
      
      const validator = new RedisValidator(invalidConfig);
      
      // This should fail gracefully and throw an error with proper message
      await expect(validator.validateConnectivity(1, 100)).rejects.toThrow(
        /Redis connectivity failed after 1 attempts/
      );
    }, 3000); // 3 second timeout for this test
  });

  describe('Service Validator Class Tests', () => {
    it('should create service validator instances', () => {
      const accountValidator = new ServiceValidator(config.services.accountService, 'account-service');
      const transactionValidator = new ServiceValidator(config.services.transactionService, 'transaction-service');

      expect(accountValidator).toBeInstanceOf(ServiceValidator);
      expect(transactionValidator).toBeInstanceOf(ServiceValidator);
    });

    it('should handle service unavailability gracefully', async () => {
      // Use invalid configuration that will definitely fail
      const invalidConfig = {
        url: 'http://invalid-service-host-that-does-not-exist:9999',
        healthEndpoint: '/actuator/health',
        startupTimeout: 1000,
        requestTimeout: 1000
      };
      
      const validator = new ServiceValidator(invalidConfig, 'test-service');
      
      // This should fail gracefully and throw an error with proper message
      await expect(validator.validateHealthEndpoint(1, 100)).rejects.toThrow(
        /Service test-service health check failed after 1 attempts/
      );
    });
  });

  describe('Validator Factory Methods', () => {
    it('should create validators through factory methods', () => {
      const { DatabaseValidatorFactory } = require('../utils/database-validator');
      const { RedisValidatorFactory } = require('../utils/redis-validator');
      const { ServiceValidatorFactory } = require('../utils/service-validator');

      const accountDbValidator = DatabaseValidatorFactory.createAccountDbValidator(config.databases.accountDb);
      const transactionDbValidator = DatabaseValidatorFactory.createTransactionDbValidator(config.databases.transactionDb);
      const redisValidator = RedisValidatorFactory.createValidator(config.cache.redis);
      const accountServiceValidator = ServiceValidatorFactory.createAccountServiceValidator(config.services.accountService);
      const transactionServiceValidator = ServiceValidatorFactory.createTransactionServiceValidator(config.services.transactionService);

      expect(accountDbValidator).toBeInstanceOf(DatabaseValidator);
      expect(transactionDbValidator).toBeInstanceOf(DatabaseValidator);
      expect(redisValidator).toBeInstanceOf(RedisValidator);
      expect(accountServiceValidator).toBeInstanceOf(ServiceValidator);
      expect(transactionServiceValidator).toBeInstanceOf(ServiceValidator);
    });
  });

  describe('Error Handling and Logging', () => {
    it('should log infrastructure status correctly', () => {
      const logSpy = jest.spyOn(logger, 'infrastructureStatus');
      
      logger.infrastructureStatus('test-component', 'UP', { test: 'data' });
      logger.infrastructureStatus('test-component', 'DOWN', { error: 'test error' });

      expect(logSpy).toHaveBeenCalledWith('test-component', 'UP', { test: 'data' });
      expect(logSpy).toHaveBeenCalledWith('test-component', 'DOWN', { error: 'test error' });
      
      logSpy.mockRestore();
    });

    it('should log test lifecycle events', () => {
      const testStartSpy = jest.spyOn(logger, 'testStart');
      const testCompleteSpy = jest.spyOn(logger, 'testComplete');
      
      logger.testStart('Test Infrastructure Validation');
      logger.testComplete('Test Infrastructure Validation', 'PASSED', 1000);

      expect(testStartSpy).toHaveBeenCalledWith('Test Infrastructure Validation');
      expect(testCompleteSpy).toHaveBeenCalledWith('Test Infrastructure Validation', 'PASSED', 1000);
      
      testStartSpy.mockRestore();
      testCompleteSpy.mockRestore();
    });
  });

  describe('Timeout and Retry Configuration', () => {
    it('should have proper timeout configurations', () => {
      expect(config.timeouts.testTimeout).toBe(30000);
      expect(config.timeouts.setupTimeout).toBe(60000);
      expect(config.timeouts.teardownTimeout).toBe(30000);
      expect(config.timeouts.httpTimeout).toBe(10000);
      expect(config.timeouts.databaseTimeout).toBe(15000);
    });

    it('should have proper service timeout configurations', () => {
      expect(config.services.accountService.startupTimeout).toBe(180000);
      expect(config.services.accountService.requestTimeout).toBe(30000);
      expect(config.services.transactionService.startupTimeout).toBe(180000);
      expect(config.services.transactionService.requestTimeout).toBe(30000);
    });

    it('should have proper database connection timeouts', () => {
      expect(config.databases.accountDb.connectionTimeout).toBe(10000);
      expect(config.databases.transactionDb.connectionTimeout).toBe(10000);
    });

    it('should have proper Redis connection timeout', () => {
      expect(config.cache.redis.connectionTimeout).toBe(5000);
    });
  });
});