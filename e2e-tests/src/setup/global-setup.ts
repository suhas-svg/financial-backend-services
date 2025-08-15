import { testConfig } from '../config/test-config';

/**
 * Global setup for Jest test suite
 * Runs once before all test suites
 */
export default async function globalSetup() {
  console.log('🔧 Global E2E Test Setup Starting...');
  
  try {
    // Validate test configuration
    testConfig.validateConfiguration();
    console.log('✅ Test configuration validated');

    // Log test environment information
    const config = testConfig.getConfig();
    console.log('📋 Test Environment Configuration:');
    console.log(`   Account Service: ${config.services.accountService.url}`);
    console.log(`   Transaction Service: ${config.services.transactionService.url}`);
    console.log(`   Account DB: ${config.databases.accountDb.host}:${config.databases.accountDb.port}`);
    console.log(`   Transaction DB: ${config.databases.transactionDb.host}:${config.databases.transactionDb.port}`);
    console.log(`   Redis: ${config.cache.redis.host}:${config.cache.redis.port}`);
    console.log(`   Test Users: ${config.testData.users.length}`);
    console.log(`   Concurrent Users: ${config.performance.concurrentUsers}`);
    console.log(`   Test Timeout: ${config.timeouts.testTimeout}ms`);

    // Set global environment variables for tests
    process.env.E2E_TEST_MODE = 'true';
    process.env.NODE_ENV = 'test';

    console.log('✅ Global setup completed successfully');
  } catch (error) {
    console.error('❌ Global setup failed:', error);
    throw error;
  }
}