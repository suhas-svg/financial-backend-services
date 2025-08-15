import { TestUser, TestAccount, AuthResponse, AccountInfo } from '../types';
import { logger } from './logger';

/**
 * Common test helper utilities
 * Provides reusable functions for test setup, data generation, and assertions
 */

/**
 * Generate unique test identifiers
 */
export function generateTestId(prefix: string = 'test'): string {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(2, 8);
  return `${prefix}_${timestamp}_${random}`;
}

/**
 * Generate unique username for testing
 */
export function generateTestUsername(prefix: string = 'testuser'): string {
  return generateTestId(prefix);
}

/**
 * Generate unique email for testing
 */
export function generateTestEmail(username?: string): string {
  const user = username || generateTestUsername();
  return `${user}@e2etest.com`;
}

/**
 * Create test user data
 */
export function createTestUser(overrides: Partial<TestUser> = {}): TestUser {
  const username = generateTestUsername();
  return {
    username,
    password: 'TestPassword123!',
    email: generateTestEmail(username),
    accounts: [
      {
        accountType: 'CHECKING',
        initialBalance: 1000.00,
        currency: 'USD'
      }
    ],
    ...overrides
  };
}

/**
 * Create test account data
 */
export function createTestAccount(overrides: Partial<TestAccount> = {}): TestAccount {
  return {
    accountType: 'CHECKING',
    initialBalance: 1000.00,
    currency: 'USD',
    ...overrides
  };
}

/**
 * Wait for a condition to be true with timeout
 */
export async function waitForCondition(
  condition: () => Promise<boolean> | boolean,
  timeoutMs: number = 10000,
  intervalMs: number = 500,
  description: string = 'condition'
): Promise<boolean> {
  const startTime = Date.now();
  
  while (Date.now() - startTime < timeoutMs) {
    try {
      const result = await condition();
      if (result) {
        logger.debug(`Condition met: ${description}`);
        return true;
      }
    } catch (error) {
      logger.debug(`Condition check failed: ${description}`, error);
    }
    
    await delay(intervalMs);
  }
  
  logger.error(`Condition timeout: ${description} after ${timeoutMs}ms`);
  return false;
}

/**
 * Retry an operation with exponential backoff
 */
export async function retryOperation<T>(
  operation: () => Promise<T>,
  maxAttempts: number = 3,
  baseDelayMs: number = 1000,
  description: string = 'operation'
): Promise<T> {
  let lastError: Error;
  
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      logger.debug(`Attempting ${description} (${attempt}/${maxAttempts})`);
      const result = await operation();
      logger.debug(`${description} succeeded on attempt ${attempt}`);
      return result;
    } catch (error) {
      lastError = error as Error;
      logger.warn(`${description} failed on attempt ${attempt}`, error);
      
      if (attempt < maxAttempts) {
        const delayMs = baseDelayMs * Math.pow(2, attempt - 1);
        logger.debug(`Retrying ${description} in ${delayMs}ms`);
        await delay(delayMs);
      }
    }
  }
  
  logger.error(`${description} failed after ${maxAttempts} attempts`);
  throw lastError!;
}

/**
 * Delay utility
 */
export function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Validate JWT token format
 */
export function isValidJwtToken(token: string): boolean {
  if (typeof token !== 'string') return false;
  const parts = token.split('.');
  return parts.length === 3 && parts.every(part => part.length > 0);
}

/**
 * Extract JWT payload (for testing purposes only)
 */
export function extractJwtPayload(token: string): any {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    
    const payload = parts[1];
    const decoded = Buffer.from(payload, 'base64').toString('utf8');
    return JSON.parse(decoded);
  } catch (error) {
    logger.error('Failed to extract JWT payload', error);
    return null;
  }
}

/**
 * Validate account object structure
 */
export function validateAccountStructure(account: any): account is AccountInfo {
  const requiredFields = ['id', 'ownerId', 'accountType', 'balance', 'currency', 'status', 'createdAt'];
  return requiredFields.every(field => account && account.hasOwnProperty(field));
}

/**
 * Validate transaction object structure
 */
export function validateTransactionStructure(transaction: any): boolean {
  const requiredFields = ['id', 'accountId', 'type', 'amount', 'currency', 'status', 'createdAt'];
  return requiredFields.every(field => transaction && transaction.hasOwnProperty(field));
}

/**
 * Generate random amount for testing
 */
export function generateRandomAmount(min: number = 1, max: number = 1000): number {
  return Math.round((Math.random() * (max - min) + min) * 100) / 100;
}

/**
 * Format currency amount
 */
export function formatCurrency(amount: number, currency: string = 'USD'): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency
  }).format(amount);
}

/**
 * Compare floating point numbers with tolerance
 */
export function compareAmounts(amount1: number, amount2: number, tolerance: number = 0.01): boolean {
  return Math.abs(amount1 - amount2) <= tolerance;
}

/**
 * Clean up test data (placeholder for actual cleanup logic)
 */
export async function cleanupTestData(testId: string): Promise<void> {
  logger.info(`Cleaning up test data for: ${testId}`);
  // Implementation will be added in later tasks
}

/**
 * Setup test environment (placeholder for actual setup logic)
 */
export async function setupTestEnvironment(): Promise<void> {
  logger.info('Setting up test environment');
  // Implementation will be added in later tasks
}

/**
 * Validate response time is acceptable
 */
export function validateResponseTime(duration: number, maxTime: number): boolean {
  return duration <= maxTime;
}

/**
 * Create test data summary for reporting
 */
export function createTestDataSummary(users: TestUser[]): any {
  return {
    totalUsers: users.length,
    totalAccounts: users.reduce((sum, user) => sum + (user.accounts?.length || 0), 0),
    accountTypes: users.flatMap(user => user.accounts || [])
      .reduce((acc, account) => {
        acc[account.accountType] = (acc[account.accountType] || 0) + 1;
        return acc;
      }, {} as Record<string, number>),
    totalBalance: users.flatMap(user => user.accounts || [])
      .reduce((sum, account) => sum + account.initialBalance, 0)
  };
}

/**
 * Mask sensitive data for logging
 */
export function maskSensitiveData(data: any): any {
  if (typeof data !== 'object' || data === null) return data;
  
  const masked = { ...data };
  const sensitiveFields = ['password', 'token', 'authorization', 'secret'];
  
  for (const field of sensitiveFields) {
    if (masked[field]) {
      masked[field] = '***MASKED***';
    }
  }
  
  return masked;
}