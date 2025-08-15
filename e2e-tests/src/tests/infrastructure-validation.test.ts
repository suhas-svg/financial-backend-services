import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { DatabaseValidatorFactory } from '../utils/database-validator';
import { RedisValidatorFactory } from '../utils/redis-validator';
import { ServiceValidatorFactory } from '../utils/service-validator';

/**
 * Infrastructure Validation Test Suite
 * Tests database connectivity, Redis cache functionality, and service health checks
 * Requirement 1.1: PostgreSQL database validation
 * Requirement 1.2: Redis cache validation  
 * Requirement 1.4: Service health check validation
 */
describe('Infrastructure Validation', () => {
  const config = testConfig.getConfig();
  
  beforeAll(async () => {
    logger.suiteStart('Infrastructure Validation');
    
    // Validate configuration before running tests
    testConfig.validateConfiguration();
  });

  afterAll(async () => {
    logger.suiteComplete('Infrastructure Validation', {
      message: 'Infrastructure validation test suite completed'
    });
  });

  describe('Database Connectivity Validation', () => {
    describe('Account Database', () => {
      it('should connect to account database successfully', async () => {
        logger.testStart('Account Database Connectivity');
        
        const validator = DatabaseValidatorFactory.createAccountDbValidator(config.databases.accountDb);
        
        const startTime = Date.now();
        const isConnected = await validator.validateConnectivity();
        const duration = Date.now() - startTime;
        
        expect(isConnected).toBe(true);
        logger.testComplete('Account Database Connectivity', 'PASSED', duration);
      }, 30000);

      it('should validate account database schema', async () => {
        logger.testStart('Account Database Schema Validation');
        
        const validator = DatabaseValidatorFactory.createAccountDbValidator(config.databases.accountDb);
        const expectedTables = ['users', 'accounts'];
        
        const startTime = Date.now();
        const isValidSchema = await validator.validateSchema(expectedTables);
        const duration = Date.now() - startTime;
        
        expect(isValidSchema).toBe(true);
        logger.testComplete('Account Database Schema Validation', 'PASSED', duration);
      }, 30000);

      it('should perform account database health check', async () => {
        logger.testStart('Account Database Health Check');
        
        const validator = DatabaseValidatorFactory.createAccountDbValidator(config.databases.accountDb);
        
        const startTime = Date.now();
        const healthResult = await validator.healthCheck();
        const duration = Date.now() - startTime;
        
        expect(healthResult.status).toBe('UP');
        expect(healthResult.details.connectivity).toBe(true);
        expect(healthResult.details.responseTime).toBeGreaterThan(0);
        expect(healthResult.details.version).toBeTruthy();
        
        logger.testComplete('Account Database Health Check', 'PASSED', duration);
      }, 30000);
    });

    describe('Transaction Database', () => {
      it('should connect to transaction database successfully', async () => {
        logger.testStart('Transaction Database Connectivity');
        
        const validator = DatabaseValidatorFactory.createTransactionDbValidator(config.databases.transactionDb);
        
        const startTime = Date.now();
        const isConnected = await validator.validateConnectivity();
        const duration = Date.now() - startTime;
        
        expect(isConnected).toBe(true);
        logger.testComplete('Transaction Database Connectivity', 'PASSED', duration);
      }, 30000);

      it('should validate transaction database schema', async () => {
        logger.testStart('Transaction Database Schema Validation');
        
        const validator = DatabaseValidatorFactory.createTransactionDbValidator(config.databases.transactionDb);
        const expectedTables = ['transactions', 'transaction_limits'];
        
        const startTime = Date.now();
        const isValidSchema = await validator.validateSchema(expectedTables);
        const duration = Date.now() - startTime;
        
        expect(isValidSchema).toBe(true);
        logger.testComplete('Transaction Database Schema Validation', 'PASSED', duration);
      }, 30000);

      it('should perform transaction database health check', async () => {
        logger.testStart('Transaction Database Health Check');
        
        const validator = DatabaseValidatorFactory.createTransactionDbValidator(config.databases.transactionDb);
        
        const startTime = Date.now();
        const healthResult = await validator.healthCheck();
        const duration = Date.now() - startTime;
        
        expect(healthResult.status).toBe('UP');
        expect(healthResult.details.connectivity).toBe(true);
        expect(healthResult.details.responseTime).toBeGreaterThan(0);
        expect(healthResult.details.version).toBeTruthy();
        
        logger.testComplete('Transaction Database Health Check', 'PASSED', duration);
      }, 30000);
    });

    describe('Comprehensive Database Validation', () => {
      it('should validate all databases comprehensively', async () => {
        logger.testStart('Comprehensive Database Validation');
        
        const startTime = Date.now();
        const results = await DatabaseValidatorFactory.validateAllDatabases(
          config.databases.accountDb,
          config.databases.transactionDb
        );
        const duration = Date.now() - startTime;
        
        // Validate account database results
        expect(results.accountDb.connectivity).toBe(true);
        expect(results.accountDb.schema).toBe(true);
        expect(results.accountDb.health.status).toBe('UP');
        
        // Validate transaction database results
        expect(results.transactionDb.connectivity).toBe(true);
        expect(results.transactionDb.schema).toBe(true);
        expect(results.transactionDb.health.status).toBe('UP');
        
        logger.testComplete('Comprehensive Database Validation', 'PASSED', duration);
      }, 60000);
    });
  });

  describe('Redis Cache Validation', () => {
    it('should connect to Redis successfully', async () => {
      logger.testStart('Redis Connectivity');
      
      const validator = RedisValidatorFactory.createValidator(config.cache.redis);
      
      const startTime = Date.now();
      const isConnected = await validator.validateConnectivity();
      const duration = Date.now() - startTime;
      
      expect(isConnected).toBe(true);
      logger.testComplete('Redis Connectivity', 'PASSED', duration);
    }, 30000);

    it('should validate Redis cache functionality', async () => {
      logger.testStart('Redis Cache Functionality');
      
      const validator = RedisValidatorFactory.createValidator(config.cache.redis);
      
      const startTime = Date.now();
      const isFunctional = await validator.validateCacheFunctionality();
      const duration = Date.now() - startTime;
      
      expect(isFunctional).toBe(true);
      logger.testComplete('Redis Cache Functionality', 'PASSED', duration);
    }, 30000);

    it('should validate Redis connection pooling', async () => {
      logger.testStart('Redis Connection Pooling');
      
      const validator = RedisValidatorFactory.createValidator(config.cache.redis);
      
      const startTime = Date.now();
      const poolingWorks = await validator.validateConnectionPooling(5);
      const duration = Date.now() - startTime;
      
      expect(poolingWorks).toBe(true);
      logger.testComplete('Redis Connection Pooling', 'PASSED', duration);
    }, 30000);

    it('should perform Redis health check', async () => {
      logger.testStart('Redis Health Check');
      
      const validator = RedisValidatorFactory.createValidator(config.cache.redis);
      
      const startTime = Date.now();
      const healthResult = await validator.healthCheck();
      const duration = Date.now() - startTime;
      
      expect(healthResult.status).toBe('UP');
      expect(healthResult.details.connectivity).toBe(true);
      expect(healthResult.details.responseTime).toBeGreaterThan(0);
      expect(healthResult.details.version).toBeTruthy();
      
      logger.testComplete('Redis Health Check', 'PASSED', duration);
    }, 30000);

    it('should analyze Redis cache performance', async () => {
      logger.testStart('Redis Cache Performance Analysis');
      
      const validator = RedisValidatorFactory.createValidator(config.cache.redis);
      
      const startTime = Date.now();
      const performance = await validator.analyzeCachePerformance(50);
      const duration = Date.now() - startTime;
      
      expect(performance.hitRatio).toBeGreaterThanOrEqual(0);
      expect(performance.hitRatio).toBeLessThanOrEqual(1);
      expect(performance.missRatio).toBeGreaterThanOrEqual(0);
      expect(performance.missRatio).toBeLessThanOrEqual(1);
      expect(performance.averageResponseTime).toBeGreaterThan(0);
      expect(performance.operationsPerSecond).toBeGreaterThan(0);
      
      // Log performance metrics
      logger.performanceMetrics({
        component: 'redis',
        hitRatio: performance.hitRatio,
        missRatio: performance.missRatio,
        averageResponseTime: performance.averageResponseTime,
        operationsPerSecond: performance.operationsPerSecond
      });
      
      logger.testComplete('Redis Cache Performance Analysis', 'PASSED', duration);
    }, 30000);

    describe('Comprehensive Redis Validation', () => {
      it('should validate Redis comprehensively', async () => {
        logger.testStart('Comprehensive Redis Validation');
        
        const startTime = Date.now();
        const results = await RedisValidatorFactory.validateRedis(config.cache.redis);
        const duration = Date.now() - startTime;
        
        expect(results.connectivity).toBe(true);
        expect(results.functionality).toBe(true);
        expect(results.connectionPooling).toBe(true);
        expect(results.health.status).toBe('UP');
        expect(results.performance).toBeDefined();
        
        logger.testComplete('Comprehensive Redis Validation', 'PASSED', duration);
      }, 60000);
    });
  });

  describe('Service Health Check Validation', () => {
    describe('Account Service', () => {
      it('should validate account service health endpoint', async () => {
        logger.testStart('Account Service Health Endpoint');
        
        const validator = ServiceValidatorFactory.createAccountServiceValidator(config.services.accountService);
        
        const startTime = Date.now();
        const isHealthy = await validator.validateHealthEndpoint();
        const duration = Date.now() - startTime;
        
        expect(isHealthy).toBe(true);
        logger.testComplete('Account Service Health Endpoint', 'PASSED', duration);
      }, 60000);

      it('should validate account service readiness', async () => {
        logger.testStart('Account Service Readiness');
        
        const validator = ServiceValidatorFactory.createAccountServiceValidator(config.services.accountService);
        
        const startTime = Date.now();
        const readinessResult = await validator.validateReadiness();
        const duration = Date.now() - startTime;
        
        expect(readinessResult.status).toBe('READY');
        expect(readinessResult.details.health).toBeDefined();
        expect(readinessResult.details.responseTime).toBeGreaterThan(0);
        
        logger.testComplete('Account Service Readiness', 'PASSED', duration);
      }, 30000);

      it('should perform comprehensive account service health check', async () => {
        logger.testStart('Account Service Comprehensive Health Check');
        
        const validator = ServiceValidatorFactory.createAccountServiceValidator(config.services.accountService);
        
        const startTime = Date.now();
        const healthResult = await validator.comprehensiveHealthCheck();
        const duration = Date.now() - startTime;
        
        expect(healthResult.status).toBe('UP');
        expect(healthResult.details.httpStatus).toBe(200);
        expect(healthResult.details.responseTime).toBeGreaterThan(0);
        expect(healthResult.details.health).toBeDefined();
        
        logger.testComplete('Account Service Comprehensive Health Check', 'PASSED', duration);
      }, 30000);

      it('should test account service endpoint availability', async () => {
        logger.testStart('Account Service Endpoint Availability');
        
        const validator = ServiceValidatorFactory.createAccountServiceValidator(config.services.accountService);
        
        const startTime = Date.now();
        const availabilityResult = await validator.testEndpointAvailability('/actuator/health');
        const duration = Date.now() - startTime;
        
        expect(availabilityResult.available).toBe(true);
        expect(availabilityResult.httpStatus).toBe(200);
        expect(availabilityResult.responseTime).toBeGreaterThan(0);
        
        logger.testComplete('Account Service Endpoint Availability', 'PASSED', duration);
      }, 30000);
    });

    describe('Transaction Service', () => {
      it('should validate transaction service health endpoint', async () => {
        logger.testStart('Transaction Service Health Endpoint');
        
        const validator = ServiceValidatorFactory.createTransactionServiceValidator(config.services.transactionService);
        
        const startTime = Date.now();
        const isHealthy = await validator.validateHealthEndpoint();
        const duration = Date.now() - startTime;
        
        expect(isHealthy).toBe(true);
        logger.testComplete('Transaction Service Health Endpoint', 'PASSED', duration);
      }, 60000);

      it('should validate transaction service readiness', async () => {
        logger.testStart('Transaction Service Readiness');
        
        const validator = ServiceValidatorFactory.createTransactionServiceValidator(config.services.transactionService);
        
        const startTime = Date.now();
        const readinessResult = await validator.validateReadiness();
        const duration = Date.now() - startTime;
        
        expect(readinessResult.status).toBe('READY');
        expect(readinessResult.details.health).toBeDefined();
        expect(readinessResult.details.responseTime).toBeGreaterThan(0);
        
        logger.testComplete('Transaction Service Readiness', 'PASSED', duration);
      }, 30000);

      it('should perform comprehensive transaction service health check', async () => {
        logger.testStart('Transaction Service Comprehensive Health Check');
        
        const validator = ServiceValidatorFactory.createTransactionServiceValidator(config.services.transactionService);
        
        const startTime = Date.now();
        const healthResult = await validator.comprehensiveHealthCheck();
        const duration = Date.now() - startTime;
        
        expect(healthResult.status).toBe('UP');
        expect(healthResult.details.httpStatus).toBe(200);
        expect(healthResult.details.responseTime).toBeGreaterThan(0);
        expect(healthResult.details.health).toBeDefined();
        
        logger.testComplete('Transaction Service Comprehensive Health Check', 'PASSED', duration);
      }, 30000);

      it('should test transaction service endpoint availability', async () => {
        logger.testStart('Transaction Service Endpoint Availability');
        
        const validator = ServiceValidatorFactory.createTransactionServiceValidator(config.services.transactionService);
        
        const startTime = Date.now();
        const availabilityResult = await validator.testEndpointAvailability('/actuator/health');
        const duration = Date.now() - startTime;
        
        expect(availabilityResult.available).toBe(true);
        expect(availabilityResult.httpStatus).toBe(200);
        expect(availabilityResult.responseTime).toBeGreaterThan(0);
        
        logger.testComplete('Transaction Service Endpoint Availability', 'PASSED', duration);
      }, 30000);
    });

    describe('Comprehensive Service Validation', () => {
      it('should wait for all services to start up', async () => {
        logger.testStart('All Services Startup');
        
        const startTime = Date.now();
        await ServiceValidatorFactory.waitForAllServices(config.services);
        const duration = Date.now() - startTime;
        
        logger.testComplete('All Services Startup', 'PASSED', duration);
      }, 300000); // 5 minutes timeout for startup

      it('should validate all services comprehensively', async () => {
        logger.testStart('Comprehensive Service Validation');
        
        const startTime = Date.now();
        const results = await ServiceValidatorFactory.validateAllServices(config.services);
        const duration = Date.now() - startTime;
        
        // Validate account service results
        expect(results.accountService.health.status).toBe('UP');
        expect(results.accountService.readiness.status).toBe('READY');
        expect(results.accountService.availability.available).toBe(true);
        
        // Validate transaction service results
        expect(results.transactionService.health.status).toBe('UP');
        expect(results.transactionService.readiness.status).toBe('READY');
        expect(results.transactionService.availability.available).toBe(true);
        
        logger.testComplete('Comprehensive Service Validation', 'PASSED', duration);
      }, 120000);
    });
  });

  describe('Complete Infrastructure Validation', () => {
    it('should validate entire infrastructure stack', async () => {
      logger.testStart('Complete Infrastructure Validation');
      
      const startTime = Date.now();
      
      // Validate databases
      const databaseResults = await DatabaseValidatorFactory.validateAllDatabases(
        config.databases.accountDb,
        config.databases.transactionDb
      );
      
      // Validate Redis
      const redisResults = await RedisValidatorFactory.validateRedis(config.cache.redis);
      
      // Validate services
      const serviceResults = await ServiceValidatorFactory.validateAllServices(config.services);
      
      const duration = Date.now() - startTime;
      
      // Assert all components are healthy
      expect(databaseResults.accountDb.connectivity).toBe(true);
      expect(databaseResults.accountDb.schema).toBe(true);
      expect(databaseResults.accountDb.health.status).toBe('UP');
      
      expect(databaseResults.transactionDb.connectivity).toBe(true);
      expect(databaseResults.transactionDb.schema).toBe(true);
      expect(databaseResults.transactionDb.health.status).toBe('UP');
      
      expect(redisResults.connectivity).toBe(true);
      expect(redisResults.functionality).toBe(true);
      expect(redisResults.health.status).toBe('UP');
      
      expect(serviceResults.accountService.health.status).toBe('UP');
      expect(serviceResults.accountService.readiness.status).toBe('READY');
      
      expect(serviceResults.transactionService.health.status).toBe('UP');
      expect(serviceResults.transactionService.readiness.status).toBe('READY');
      
      // Log comprehensive results
      logger.info('Complete infrastructure validation results', {
        databases: databaseResults,
        redis: redisResults,
        services: serviceResults,
        totalDuration: duration
      });
      
      logger.testComplete('Complete Infrastructure Validation', 'PASSED', duration);
    }, 300000); // 5 minutes timeout for complete validation
  });
});