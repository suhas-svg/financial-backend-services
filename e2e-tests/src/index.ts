/**
 * Main entry point for E2E testing framework
 * Exports core components and utilities for test execution
 */

// Core framework exports
export { E2ETestRunner } from './core/test-runner';
export { TestConfigManager, testConfig } from './config/test-config';

// Utility exports
export { HttpClient, createAccountServiceClient, createTransactionServiceClient } from './utils/http-client';
export { logger } from './utils/logger';
export * from './utils/test-helpers';

// Type exports
export * from './types';

// Re-export for convenience
export { testConfig as config } from './config/test-config';