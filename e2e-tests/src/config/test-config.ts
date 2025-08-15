import { config } from 'dotenv';
import { TestConfiguration } from '../types';
import path from 'path';

// Load environment variables based on NODE_ENV
const isE2EMode = process.env.NODE_ENV === 'e2e';
const envFile = isE2EMode ? '.env.e2e' : '.env';

console.log(`Loading configuration from: ${envFile} (E2E Mode: ${isE2EMode})`);

config({ path: path.resolve(process.cwd(), envFile) });

/**
 * Central configuration management for E2E tests
 * Supports environment variable overrides and default values
 */
export class TestConfigManager {
  private static instance: TestConfigManager;
  private configuration: TestConfiguration;

  private constructor() {
    this.configuration = this.loadConfiguration();
  }

  public static getInstance(): TestConfigManager {
    if (!TestConfigManager.instance) {
      TestConfigManager.instance = new TestConfigManager();
    }
    return TestConfigManager.instance;
  }

  public getConfig(): TestConfiguration {
    return this.configuration;
  }

  public getServiceConfig() {
    return this.configuration.services;
  }

  public getDatabaseConfig() {
    return this.configuration.databases;
  }

  public getCacheConfig() {
    return this.configuration.cache;
  }

  public getTestDataConfig() {
    return this.configuration.testData;
  }

  public getPerformanceConfig() {
    return this.configuration.performance;
  }

  public getReportingConfig() {
    return this.configuration.reporting;
  }

  public getTimeoutConfig() {
    return this.configuration.timeouts;
  }

  private loadConfiguration(): TestConfiguration {
    return {
      services: {
        accountService: {
          url: process.env.ACCOUNT_SERVICE_URL || 'http://localhost:8081',
          healthEndpoint: process.env.ACCOUNT_HEALTH_ENDPOINT || '/actuator/health',
          startupTimeout: parseInt(process.env.ACCOUNT_STARTUP_TIMEOUT || '180000'),
          requestTimeout: parseInt(process.env.ACCOUNT_REQUEST_TIMEOUT || '30000')
        },
        transactionService: {
          url: process.env.TRANSACTION_SERVICE_URL || 'http://localhost:8080',
          healthEndpoint: process.env.TRANSACTION_HEALTH_ENDPOINT || '/actuator/health',
          startupTimeout: parseInt(process.env.TRANSACTION_STARTUP_TIMEOUT || '180000'),
          requestTimeout: parseInt(process.env.TRANSACTION_REQUEST_TIMEOUT || '30000')
        }
      },
      databases: {
        accountDb: {
          host: process.env.ACCOUNT_DB_HOST || 'localhost',
          port: parseInt(process.env.ACCOUNT_DB_PORT || '5432'),
          database: process.env.ACCOUNT_DB_NAME || 'account_db',
          username: process.env.ACCOUNT_DB_USER || 'postgres',
          password: process.env.ACCOUNT_DB_PASSWORD || 'postgres',
          connectionTimeout: parseInt(process.env.DB_CONNECTION_TIMEOUT || '10000')
        },
        transactionDb: {
          host: process.env.TRANSACTION_DB_HOST || 'localhost',
          port: parseInt(process.env.TRANSACTION_DB_PORT || '5433'),
          database: process.env.TRANSACTION_DB_NAME || 'transaction_db',
          username: process.env.TRANSACTION_DB_USER || 'postgres',
          password: process.env.TRANSACTION_DB_PASSWORD || 'postgres',
          connectionTimeout: parseInt(process.env.DB_CONNECTION_TIMEOUT || '10000')
        }
      },
      cache: {
        redis: {
          host: process.env.REDIS_HOST || 'localhost',
          port: parseInt(process.env.REDIS_PORT || '6379'),
          password: process.env.REDIS_PASSWORD,
          connectionTimeout: parseInt(process.env.REDIS_CONNECTION_TIMEOUT || '5000')
        }
      },
      testData: {
        users: this.loadTestUsers(),
        accounts: this.loadTestAccounts(),
        cleanupAfterTests: process.env.CLEANUP_AFTER_TESTS !== 'false'
      },
      performance: {
        concurrentUsers: parseInt(process.env.CONCURRENT_USERS || '10'),
        testDuration: parseInt(process.env.TEST_DURATION || '300'),
        rampUpTime: parseInt(process.env.RAMP_UP_TIME || '60'),
        thresholds: {
          averageResponseTime: parseInt(process.env.AVG_RESPONSE_TIME_THRESHOLD || '500'),
          p95ResponseTime: parseInt(process.env.P95_RESPONSE_TIME_THRESHOLD || '1000'),
          errorRate: parseFloat(process.env.ERROR_RATE_THRESHOLD || '0.01'),
          throughput: parseInt(process.env.THROUGHPUT_THRESHOLD || '100')
        }
      },
      reporting: {
        outputDir: process.env.REPORT_OUTPUT_DIR || './reports',
        formats: (process.env.REPORT_FORMATS || 'html,json').split(',') as any[],
        includeMetrics: process.env.INCLUDE_METRICS !== 'false',
        includeErrorDetails: process.env.INCLUDE_ERROR_DETAILS !== 'false'
      },
      timeouts: {
        testTimeout: parseInt(process.env.TEST_TIMEOUT || '30000'),
        setupTimeout: parseInt(process.env.SETUP_TIMEOUT || '60000'),
        teardownTimeout: parseInt(process.env.TEARDOWN_TIMEOUT || '30000'),
        httpTimeout: parseInt(process.env.HTTP_TIMEOUT || '10000'),
        databaseTimeout: parseInt(process.env.DATABASE_TIMEOUT || '15000')
      }
    };
  }

  private loadTestUsers() {
    const defaultUsers = [
      {
        username: 'testuser1',
        password: 'password123',
        email: 'testuser1@example.com',
        accounts: [
          {
            accountType: 'CHECKING' as const,
            initialBalance: 1000.00,
            currency: 'USD'
          }
        ]
      },
      {
        username: 'testuser2',
        password: 'password123',
        email: 'testuser2@example.com',
        accounts: [
          {
            accountType: 'SAVINGS' as const,
            initialBalance: 5000.00,
            currency: 'USD'
          },
          {
            accountType: 'CHECKING' as const,
            initialBalance: 2000.00,
            currency: 'USD'
          }
        ]
      }
    ];

    try {
      const customUsers = process.env.TEST_USERS ? JSON.parse(process.env.TEST_USERS) : null;
      return customUsers || defaultUsers;
    } catch (error) {
      console.warn('Failed to parse TEST_USERS environment variable, using defaults');
      return defaultUsers;
    }
  }

  private loadTestAccounts() {
    const defaultAccounts = [
      {
        accountType: 'CHECKING' as const,
        initialBalance: 1000.00,
        currency: 'USD'
      },
      {
        accountType: 'SAVINGS' as const,
        initialBalance: 5000.00,
        currency: 'USD'
      }
    ];

    try {
      const customAccounts = process.env.TEST_ACCOUNTS ? JSON.parse(process.env.TEST_ACCOUNTS) : null;
      return customAccounts || defaultAccounts;
    } catch (error) {
      console.warn('Failed to parse TEST_ACCOUNTS environment variable, using defaults');
      return defaultAccounts;
    }
  }

  /**
   * Validate configuration and throw errors for invalid settings
   */
  public validateConfiguration(): void {
    const config = this.configuration;
    
    // Validate service URLs
    if (!this.isValidUrl(config.services.accountService.url)) {
      throw new Error(`Invalid Account Service URL: ${config.services.accountService.url}`);
    }
    
    if (!this.isValidUrl(config.services.transactionService.url)) {
      throw new Error(`Invalid Transaction Service URL: ${config.services.transactionService.url}`);
    }

    // Validate database configurations
    if (config.databases.accountDb.port < 1 || config.databases.accountDb.port > 65535) {
      throw new Error(`Invalid Account DB port: ${config.databases.accountDb.port}`);
    }

    if (config.databases.transactionDb.port < 1 || config.databases.transactionDb.port > 65535) {
      throw new Error(`Invalid Transaction DB port: ${config.databases.transactionDb.port}`);
    }

    // Validate Redis configuration
    if (config.cache.redis.port < 1 || config.cache.redis.port > 65535) {
      throw new Error(`Invalid Redis port: ${config.cache.redis.port}`);
    }

    // Validate performance thresholds
    if (config.performance.thresholds.errorRate < 0 || config.performance.thresholds.errorRate > 1) {
      throw new Error(`Invalid error rate threshold: ${config.performance.thresholds.errorRate}`);
    }

    // Validate timeouts
    if (config.timeouts.testTimeout < 1000) {
      throw new Error(`Test timeout too low: ${config.timeouts.testTimeout}ms`);
    }
  }

  private isValidUrl(url: string): boolean {
    try {
      new URL(url);
      return true;
    } catch {
      return false;
    }
  }
}

// Export singleton instance
export const testConfig = TestConfigManager.getInstance();