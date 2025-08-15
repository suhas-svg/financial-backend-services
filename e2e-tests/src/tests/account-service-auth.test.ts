/**
 * Account Service Authentication Endpoint Tests
 * Tests user registration, login, and JWT token functionality
 */

import { createAccountServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { generateTestId, createTestUser, isValidJwtToken } from '../utils/test-helpers';
import { AuthResponse, UserInfo } from '../types';

describe('Account Service Authentication Endpoints', () => {
  let accountClient: ReturnType<typeof createAccountServiceClient>;
  let testUserId: string;

  beforeAll(async () => {
    accountClient = createAccountServiceClient();
    testUserId = generateTestId();
    
    // Wait for service to be ready
    const isReady = await accountClient.waitForService();
    if (!isReady) {
      throw new Error('Account Service is not ready for testing');
    }
    
    logger.info('Account Service Authentication Tests - Setup Complete');
  });

  afterAll(async () => {
    logger.info('Account Service Authentication Tests - Cleanup Complete');
  });

  describe('User Registration Endpoint', () => {
    describe('Valid Registration Scenarios', () => {
      it('should register a new user with valid credentials', async () => {
        logger.testStart('User Registration - Valid Credentials');
        
        const testUser = createTestUser(`testuser_${testUserId}`);
        const registrationData = {
          username: testUser.username,
          password: testUser.password
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/register', registrationData);
        const duration = Date.now() - startTime;

        // Validate response structure
        expect(response.status).toBe(201);
        expect(response.data).toBeDefined();
        expect(response.data.message).toContain('registered successfully');
        expect(response.duration).toBeGreaterThan(0);
        expect(response.duration).toBeLessThan(5000); // Should complete within 5 seconds

        logger.testComplete('User Registration - Valid Credentials', 'PASSED', duration);
      }, 30000);

      it('should register multiple users with different usernames', async () => {
        logger.testStart('User Registration - Multiple Users');
        
        const users = [
          createTestUser(`multiuser1_${testUserId}`),
          createTestUser(`multiuser2_${testUserId}`),
          createTestUser(`multiuser3_${testUserId}`)
        ];

        const startTime = Date.now();
        
        for (const user of users) {
          const registrationData = {
            username: user.username,
            password: user.password
          };

          const response = await accountClient.post('/api/auth/register', registrationData);
          
          expect(response.status).toBe(201);
          expect(response.data.message).toContain('registered successfully');
        }

        const duration = Date.now() - startTime;
        logger.testComplete('User Registration - Multiple Users', 'PASSED', duration);
      }, 45000);
    });

    describe('Invalid Registration Scenarios', () => {
      it('should reject registration with missing username', async () => {
        logger.testStart('User Registration - Missing Username');
        
        const invalidData = {
          password: 'validpassword123'
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/register', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toContain('Username is required');

        logger.testComplete('User Registration - Missing Username', 'PASSED', duration);
      }, 15000);

      it('should reject registration with missing password', async () => {
        logger.testStart('User Registration - Missing Password');
        
        const invalidData = {
          username: `testuser_missing_pwd_${testUserId}`
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/register', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toContain('Password is required');

        logger.testComplete('User Registration - Missing Password', 'PASSED', duration);
      }, 15000);

      it('should reject registration with empty username', async () => {
        logger.testStart('User Registration - Empty Username');
        
        const invalidData = {
          username: '',
          password: 'validpassword123'
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/register', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toContain('Username is required');

        logger.testComplete('User Registration - Empty Username', 'PASSED', duration);
      }, 15000);

      it('should reject registration with short password', async () => {
        logger.testStart('User Registration - Short Password');
        
        const invalidData = {
          username: `testuser_short_pwd_${testUserId}`,
          password: '123' // Less than 6 characters
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/register', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toContain('Password must be at least 6 characters');

        logger.testComplete('User Registration - Short Password', 'PASSED', duration);
      }, 15000);

      it('should reject registration with duplicate username', async () => {
        logger.testStart('User Registration - Duplicate Username');
        
        const testUser = createTestUser(`duplicate_${testUserId}`);
        const registrationData = {
          username: testUser.username,
          password: testUser.password
        };

        // First registration should succeed
        const firstResponse = await accountClient.post('/api/auth/register', registrationData);
        expect(firstResponse.status).toBe(201);

        // Second registration with same username should fail
        const startTime = Date.now();
        const secondResponse = await accountClient.post('/api/auth/register', registrationData);
        const duration = Date.now() - startTime;

        expect(secondResponse.status).toBe(409);
        expect(secondResponse.data.message || secondResponse.data.error).toContain('already exists');

        logger.testComplete('User Registration - Duplicate Username', 'PASSED', duration);
      }, 30000);
    });
  });

  describe('User Login Endpoint', () => {
    let registeredUser: { username: string; password: string };

    beforeAll(async () => {
      // Register a user for login tests
      registeredUser = createTestUser(`logintest_${testUserId}`);
      const registrationData = {
        username: registeredUser.username,
        password: registeredUser.password
      };

      const response = await accountClient.post('/api/auth/register', registrationData);
      expect(response.status).toBe(201);
    });

    describe('Valid Login Scenarios', () => {
      it('should login with valid credentials', async () => {
        logger.testStart('User Login - Valid Credentials');
        
        const loginData = {
          username: registeredUser.username,
          password: registeredUser.password
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/login', loginData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(200);
        expect(response.data).toBeDefined();
        expect(response.data.accessToken).toBeDefined();
        expect(typeof response.data.accessToken).toBe('string');
        expect(response.data.accessToken.length).toBeGreaterThan(0);
        expect(response.duration).toBeGreaterThan(0);
        expect(response.duration).toBeLessThan(3000); // Should complete within 3 seconds

        logger.testComplete('User Login - Valid Credentials', 'PASSED', duration);
      }, 30000);

      it('should return different tokens for multiple logins', async () => {
        logger.testStart('User Login - Multiple Login Sessions');
        
        const loginData = {
          username: registeredUser.username,
          password: registeredUser.password
        };

        const startTime = Date.now();
        
        // First login
        const firstResponse = await accountClient.post('/api/auth/login', loginData);
        expect(firstResponse.status).toBe(200);
        const firstToken = firstResponse.data.accessToken;

        // Second login
        const secondResponse = await accountClient.post('/api/auth/login', loginData);
        expect(secondResponse.status).toBe(200);
        const secondToken = secondResponse.data.accessToken;

        // Tokens should be different (new session)
        expect(firstToken).not.toBe(secondToken);
        expect(firstToken.length).toBeGreaterThan(0);
        expect(secondToken.length).toBeGreaterThan(0);

        const duration = Date.now() - startTime;
        logger.testComplete('User Login - Multiple Login Sessions', 'PASSED', duration);
      }, 30000);
    });

    describe('Invalid Login Scenarios', () => {
      it('should reject login with invalid username', async () => {
        logger.testStart('User Login - Invalid Username');
        
        const invalidData = {
          username: `nonexistent_${testUserId}`,
          password: registeredUser.password
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/login', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(401);
        expect(response.data.message || response.data.error).toContain('Invalid credentials');

        logger.testComplete('User Login - Invalid Username', 'PASSED', duration);
      }, 15000);

      it('should reject login with invalid password', async () => {
        logger.testStart('User Login - Invalid Password');
        
        const invalidData = {
          username: registeredUser.username,
          password: 'wrongpassword'
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/login', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(401);
        expect(response.data.message || response.data.error).toContain('Invalid credentials');

        logger.testComplete('User Login - Invalid Password', 'PASSED', duration);
      }, 15000);

      it('should reject login with missing username', async () => {
        logger.testStart('User Login - Missing Username');
        
        const invalidData = {
          password: registeredUser.password
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/login', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toContain('Username is required');

        logger.testComplete('User Login - Missing Username', 'PASSED', duration);
      }, 15000);

      it('should reject login with missing password', async () => {
        logger.testStart('User Login - Missing Password');
        
        const invalidData = {
          username: registeredUser.username
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/login', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toContain('Password is required');

        logger.testComplete('User Login - Missing Password', 'PASSED', duration);
      }, 15000);

      it('should reject login with empty credentials', async () => {
        logger.testStart('User Login - Empty Credentials');
        
        const invalidData = {
          username: '',
          password: ''
        };

        const startTime = Date.now();
        const response = await accountClient.post('/api/auth/login', invalidData);
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/Username is required|Password is required/);

        logger.testComplete('User Login - Empty Credentials', 'PASSED', duration);
      }, 15000);
    });
  });

  describe('JWT Token Generation and Validation', () => {
    let validToken: string;
    let testUser: { username: string; password: string };

    beforeAll(async () => {
      // Register and login to get a valid token
      testUser = createTestUser(`jwttest_${testUserId}`);
      
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
      validToken = loginResponse.data.accessToken;
    });

    it('should generate valid JWT token structure', async () => {
      logger.testStart('JWT Token - Valid Structure');
      
      const startTime = Date.now();
      
      // Validate token structure (JWT has 3 parts separated by dots)
      expect(validToken).toBeDefined();
      expect(typeof validToken).toBe('string');
      expect(validToken.split('.')).toHaveLength(3);
      
      // Validate token is not empty
      expect(validToken.length).toBeGreaterThan(50); // JWT tokens are typically longer
      
      // Validate token starts with expected JWT pattern
      expect(validToken).toMatch(/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/);

      const duration = Date.now() - startTime;
      logger.testComplete('JWT Token - Valid Structure', 'PASSED', duration);
    }, 10000);

    it('should accept valid JWT token for authenticated requests', async () => {
      logger.testStart('JWT Token - Authentication Success');
      
      // Set the token for authenticated requests
      accountClient.setAuthToken(validToken);

      const startTime = Date.now();
      
      // Try to access a protected endpoint (accounts listing)
      const response = await accountClient.get('/api/accounts');
      const duration = Date.now() - startTime;

      // Should succeed with valid token
      expect(response.status).toBe(200);
      expect(response.data).toBeDefined();

      logger.testComplete('JWT Token - Authentication Success', 'PASSED', duration);
    }, 15000);

    it('should reject requests with invalid JWT token', async () => {
      logger.testStart('JWT Token - Invalid Token Rejection');
      
      // Set an invalid token
      const invalidToken = 'invalid.jwt.token';
      accountClient.setAuthToken(invalidToken);

      const startTime = Date.now();
      
      // Try to access a protected endpoint
      const response = await accountClient.get('/api/accounts');
      const duration = Date.now() - startTime;

      // Should fail with invalid token
      expect(response.status).toBe(401);
      expect(response.data.message || response.data.error).toMatch(/unauthorized|invalid.*token|authentication/i);

      logger.testComplete('JWT Token - Invalid Token Rejection', 'PASSED', duration);
    }, 15000);

    it('should reject requests with malformed JWT token', async () => {
      logger.testStart('JWT Token - Malformed Token Rejection');
      
      // Set a malformed token (not proper JWT structure)
      const malformedToken = 'not-a-jwt-token';
      accountClient.setAuthToken(malformedToken);

      const startTime = Date.now();
      
      // Try to access a protected endpoint
      const response = await accountClient.get('/api/accounts');
      const duration = Date.now() - startTime;

      // Should fail with malformed token
      expect(response.status).toBe(401);
      expect(response.data.message || response.data.error).toMatch(/unauthorized|invalid.*token|authentication/i);

      logger.testComplete('JWT Token - Malformed Token Rejection', 'PASSED', duration);
    }, 15000);

    it('should reject requests without JWT token', async () => {
      logger.testStart('JWT Token - Missing Token Rejection');
      
      // Clear the token
      accountClient.clearAuthToken();

      const startTime = Date.now();
      
      // Try to access a protected endpoint
      const response = await accountClient.get('/api/accounts');
      const duration = Date.now() - startTime;

      // Should fail without token
      expect(response.status).toBe(401);
      expect(response.data.message || response.data.error).toMatch(/unauthorized|authentication.*required|missing.*token/i);

      logger.testComplete('JWT Token - Missing Token Rejection', 'PASSED', duration);
    }, 15000);

    afterAll(async () => {
      // Clean up - clear any set tokens
      accountClient.clearAuthToken();
    });
  });

  describe('Authentication Error Scenarios', () => {
    it('should handle malformed JSON in registration request', async () => {
      logger.testStart('Authentication Error - Malformed JSON Registration');
      
      const startTime = Date.now();
      
      try {
        // Send malformed JSON
        const response = await accountClient.post('/api/auth/register', 'invalid-json', {
          headers: { 'Content-Type': 'application/json' }
        });
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/invalid.*json|malformed.*request|bad.*request/i);

        logger.testComplete('Authentication Error - Malformed JSON Registration', 'PASSED', duration);
      } catch (error: any) {
        // Axios might throw for malformed JSON
        const duration = Date.now() - startTime;
        expect(error.message).toMatch(/invalid.*json|malformed|bad.*request/i);
        logger.testComplete('Authentication Error - Malformed JSON Registration', 'PASSED', duration);
      }
    }, 15000);

    it('should handle malformed JSON in login request', async () => {
      logger.testStart('Authentication Error - Malformed JSON Login');
      
      const startTime = Date.now();
      
      try {
        // Send malformed JSON
        const response = await accountClient.post('/api/auth/login', 'invalid-json', {
          headers: { 'Content-Type': 'application/json' }
        });
        const duration = Date.now() - startTime;

        expect(response.status).toBe(400);
        expect(response.data.message || response.data.error).toMatch(/invalid.*json|malformed.*request|bad.*request/i);

        logger.testComplete('Authentication Error - Malformed JSON Login', 'PASSED', duration);
      } catch (error: any) {
        // Axios might throw for malformed JSON
        const duration = Date.now() - startTime;
        expect(error.message).toMatch(/invalid.*json|malformed|bad.*request/i);
        logger.testComplete('Authentication Error - Malformed JSON Login', 'PASSED', duration);
      }
    }, 15000);

    it('should handle concurrent registration attempts', async () => {
      logger.testStart('Authentication Error - Concurrent Registration');
      
      const baseUsername = `concurrent_${testUserId}`;
      const registrationData = {
        username: baseUsername,
        password: 'password123'
      };

      const startTime = Date.now();
      
      // Send multiple concurrent registration requests for the same user
      const promises = Array.from({ length: 3 }, () => 
        accountClient.post('/api/auth/register', registrationData)
      );

      const responses = await Promise.all(promises);
      const duration = Date.now() - startTime;

      // Only one should succeed, others should fail with conflict
      const successfulResponses = responses.filter(r => r.status === 201);
      const conflictResponses = responses.filter(r => r.status === 409);

      expect(successfulResponses).toHaveLength(1);
      expect(conflictResponses.length).toBeGreaterThanOrEqual(1);

      logger.testComplete('Authentication Error - Concurrent Registration', 'PASSED', duration);
    }, 30000);
  });
});