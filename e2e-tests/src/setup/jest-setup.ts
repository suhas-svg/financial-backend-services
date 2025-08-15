import { ApiResponse, AuthResponse, AccountInfo, TransactionInfo } from '../types';

/**
 * Custom Jest matchers for API testing
 * These matchers provide specialized assertions for E2E testing scenarios
 */

// Extend Jest matchers
expect.extend({
  /**
   * Validates that a response has the expected API response structure
   */
  toHaveValidApiResponse(received: any) {
    const pass = received &&
      typeof received.status === 'number' &&
      typeof received.statusText === 'string' &&
      typeof received.headers === 'object' &&
      typeof received.duration === 'number';

    if (pass) {
      return {
        message: () => `Expected response not to have valid API response structure`,
        pass: true,
      };
    } else {
      return {
        message: () => `Expected response to have valid API response structure with status, statusText, headers, and duration`,
        pass: false,
      };
    }
  },

  /**
   * Validates HTTP status code
   */
  toHaveHttpStatus(received: ApiResponse, expectedStatus: number) {
    const pass = received.status === expectedStatus;

    if (pass) {
      return {
        message: () => `Expected response not to have status ${expectedStatus}`,
        pass: true,
      };
    } else {
      return {
        message: () => `Expected response to have status ${expectedStatus}, but received ${received.status}`,
        pass: false,
      };
    }
  },

  /**
   * Validates response time is within acceptable limits
   */
  toHaveResponseTime(received: ApiResponse, maxTime: number) {
    const pass = received.duration <= maxTime;

    if (pass) {
      return {
        message: () => `Expected response time ${received.duration}ms to be greater than ${maxTime}ms`,
        pass: true,
      };
    } else {
      return {
        message: () => `Expected response time ${received.duration}ms to be less than or equal to ${maxTime}ms`,
        pass: false,
      };
    }
  },

  /**
   * Validates JWT token structure
   */
  toHaveValidJwtToken(received: any) {
    if (typeof received !== 'string') {
      return {
        message: () => `Expected JWT token to be a string, but received ${typeof received}`,
        pass: false,
      };
    }

    const parts = received.split('.');
    const pass = parts.length === 3 && 
      parts.every(part => part.length > 0);

    if (pass) {
      return {
        message: () => `Expected not to have valid JWT token structure`,
        pass: true,
      };
    } else {
      return {
        message: () => `Expected valid JWT token with 3 parts separated by dots`,
        pass: false,
      };
    }
  },

  /**
   * Validates account object structure
   */
  toHaveValidAccountStructure(received: any) {
    const requiredFields = ['id', 'ownerId', 'accountType', 'balance', 'currency', 'status', 'createdAt'];
    const pass = requiredFields.every(field => received && received.hasOwnProperty(field));

    if (pass) {
      return {
        message: () => `Expected not to have valid account structure`,
        pass: true,
      };
    } else {
      const missingFields = requiredFields.filter(field => !received || !received.hasOwnProperty(field));
      return {
        message: () => `Expected valid account structure with fields: ${requiredFields.join(', ')}. Missing: ${missingFields.join(', ')}`,
        pass: false,
      };
    }
  },

  /**
   * Validates transaction object structure
   */
  toHaveValidTransactionStructure(received: any) {
    const requiredFields = ['id', 'accountId', 'type', 'amount', 'currency', 'status', 'createdAt'];
    const pass = requiredFields.every(field => received && received.hasOwnProperty(field));

    if (pass) {
      return {
        message: () => `Expected not to have valid transaction structure`,
        pass: true,
      };
    } else {
      const missingFields = requiredFields.filter(field => !received || !received.hasOwnProperty(field));
      return {
        message: () => `Expected valid transaction structure with fields: ${requiredFields.join(', ')}. Missing: ${missingFields.join(', ')}`,
        pass: false,
      };
    }
  },

  /**
   * Validates numeric value is within specified range
   */
  toBeWithinRange(received: number, min: number, max: number) {
    const pass = received >= min && received <= max;

    if (pass) {
      return {
        message: () => `Expected ${received} not to be within range ${min} to ${max}`,
        pass: true,
      };
    } else {
      return {
        message: () => `Expected ${received} to be within range ${min} to ${max}`,
        pass: false,
      };
    }
  }
});

// Global test setup
beforeAll(async () => {
  // Set longer timeout for E2E tests
  jest.setTimeout(30000);
  
  // Setup global test environment
  console.log('🚀 Starting E2E Test Suite Setup');
});

afterAll(async () => {
  // Global cleanup
  console.log('🏁 E2E Test Suite Cleanup Complete');
});

// Global error handler for unhandled promise rejections
process.on('unhandledRejection', (reason, promise) => {
  console.error('Unhandled Rejection at:', promise, 'reason:', reason);
});

// Global error handler for uncaught exceptions
process.on('uncaughtException', (error) => {
  console.error('Uncaught Exception:', error);
  process.exit(1);
});