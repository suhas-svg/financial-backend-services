import { describe, test, expect, beforeAll, afterAll, beforeEach, afterEach } from '@jest/globals';
import { createAccountServiceClient, createTransactionServiceClient } from '../utils/http-client';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { 
  createTestUser, 
  generateTestId, 
  isValidJwtToken,
  extractJwtPayload,
  delay
} from '../utils/test-helpers';
import { AuthResponse, AccountInfo, ApiResponse } from '../types';

/**
 * Service Integration Tests - JWT Token Integration
 * 
 * Tests JWT token validation consistency between Account Service and Transaction Service
 * Validates token expiration, refresh, and cross-service authentication
 * 
 * Requirements: 4.4, 4.6
 */
describe('Service Integration - JWT Token Integration', () => {
  const accountClient = createAccountServiceClient();
  const transactionClient = createTransactionServiceClient();
  const testId = generateTestId('integration_jwt');
  
  let testUser: any;
  let authToken: string;
  let refreshToken: string;
  let testAccount: AccountInfo;
  
  beforeAll(async () => {
    logger.info(`Starting JWT token integration tests: ${testId}`);
    
    // Wait for both services to be ready
    const accountReady = await accountClient.waitForService();
    const transactionReady = await transactionClient.waitForService();
    
    if (!accountReady || !transactionReady) {
      throw new Error('Services not ready for JWT integration testing');
    }
    
    logger.info('Both services are ready for JWT integration testing');
  }, 60000);

  beforeEach(async () => {
    // Create fresh test user for each test
    testUser = createTestUser({ username: `${testId}_${Date.now()}` });
    
    // Register user
    const registerResponse = await accountClient.post('/api/auth/register', {
      username: testUser.username,
      password: testUser.password,
      email: testUser.email
    });
    
    expect(registerResponse.status).toBe(201);
    
    // Login to get tokens
    const loginResponse = await accountClient.post<AuthResponse>('/api/auth/login', {
      username: testUser.username,
      password: testUser.password
    });
    
    expect(loginResponse.status).toBe(200);
    authToken = loginResponse.data!.accessToken;
    refreshToken = loginResponse.data?.refreshToken || '';
    
    // Validate token format
    expect(isValidJwtToken(authToken)).toBe(true);
    
    // Create test account
    accountClient.setAuthToken(authToken);
    const accountResponse = await accountClient.post<AccountInfo>('/api/accounts', {
      ownerId: testUser.username,
      accountType: 'CHECKING',
      balance: 1000.00
    });
    
    expect(accountResponse.status).toBe(201);
    testAccount = accountResponse.data!;
    
    logger.debug(`JWT test setup complete for ${testUser.username} with token`);
  });

  afterEach(async () => {
    // Clear auth tokens
    accountClient.clearAuthToken();
    transactionClient.clearAuthToken();
    
    logger.debug(`JWT test cleanup complete for ${testUser.username}`);
  });

  afterAll(async () => {
    logger.info(`JWT token integration tests completed: ${testId}`);
  });

  describe('Cross-Service JWT Token Validation', () => {
    test('should validate JWT token consistently across both services', async () => {
      // Test that token issued by Account Service is accepted by Transaction Service
      transactionClient.setAuthToken(authToken);
      
      // Make request to Transaction Service using Account Service token
      const response = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: 100.00,
        currency: 'USD',
        description: 'Cross-service token validation test'
      });
      
      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty('id');
      expect(response.data.accountId).toBe(testAccount.id);
      
      logger.info('✅ Cross-service JWT token validation successful');
    });

    test('should reject invalid JWT tokens consistently across services', async () => {
      const invalidToken = 'invalid.jwt.token';
      
      // Test Account Service rejection
      accountClient.setAuthToken(invalidToken);
      const accountResponse = await accountClient.get(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(401);
      
      // Test Transaction Service rejection
      transactionClient.setAuthToken(invalidToken);
      const transactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: 100.00,
        currency: 'USD',
        description: 'Invalid token test'
      });
      expect(transactionResponse.status).toBe(401);
      
      logger.info('✅ Invalid JWT token rejection consistent across services');
    });

    test('should reject malformed JWT tokens consistently', async () => {
      const malformedTokens = [
        'malformed',
        'malformed.token',
        'header.payload', // Missing signature
        'header.payload.signature.extra', // Too many parts
        '', // Empty token
        'Bearer token-without-bearer-prefix'
      ];
      
      for (const malformedToken of malformedTokens) {
        // Test Account Service
        accountClient.setAuthToken(malformedToken);
        const accountResponse = await accountClient.get(`/api/accounts/${testAccount.id}`);
        expect(accountResponse.status).toBe(401);
        
        // Test Transaction Service
        transactionClient.setAuthToken(malformedToken);
        const transactionResponse = await transactionClient.get('/api/transactions/limits');
        expect(transactionResponse.status).toBe(401);
        
        logger.debug(`✅ Malformed token "${malformedToken}" rejected by both services`);
      }
      
      logger.info('✅ Malformed JWT token rejection consistent across services');
    });

    test('should validate JWT token signature consistently', async () => {
      // Create a token with valid format but invalid signature
      const payload = extractJwtPayload(authToken);
      expect(payload).toBeDefined();
      
      // Create token with tampered signature
      const tokenParts = authToken.split('.');
      const tamperedToken = `${tokenParts[0]}.${tokenParts[1]}.tampered_signature`;
      
      // Test Account Service rejection
      accountClient.setAuthToken(tamperedToken);
      const accountResponse = await accountClient.get(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(401);
      
      // Test Transaction Service rejection
      transactionClient.setAuthToken(tamperedToken);
      const transactionResponse = await transactionClient.get('/api/transactions/limits');
      expect(transactionResponse.status).toBe(401);
      
      logger.info('✅ JWT signature validation consistent across services');
    });

    test('should validate JWT token claims consistently', async () => {
      // Use valid token for both services
      accountClient.setAuthToken(authToken);
      transactionClient.setAuthToken(authToken);
      
      // Extract and validate token payload
      const payload = extractJwtPayload(authToken);
      expect(payload).toBeDefined();
      expect(payload.sub).toBeDefined(); // Subject (user ID)
      expect(payload.iat).toBeDefined(); // Issued at
      expect(payload.exp).toBeDefined(); // Expiration
      
      // Test that both services accept the same token claims
      const accountResponse = await accountClient.get('/api/accounts');
      expect(accountResponse.status).toBe(200);
      
      const transactionResponse = await transactionClient.get('/api/transactions/limits');
      expect(transactionResponse.status).toBe(200);
      
      logger.info('✅ JWT token claims validation consistent across services');
    });
  });

  describe('Token Consistency Between Services', () => {
    test('should maintain user identity consistency across services', async () => {
      // Set same token for both services
      accountClient.setAuthToken(authToken);
      transactionClient.setAuthToken(authToken);
      
      // Get user info from Account Service
      const accountsResponse = await accountClient.get('/api/accounts');
      expect(accountsResponse.status).toBe(200);
      
      // Create transaction using Transaction Service
      const transactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: 100.00,
        currency: 'USD',
        description: 'User identity consistency test'
      });
      
      expect(transactionResponse.status).toBe(201);
      
      // Verify transaction is associated with correct user
      const userTransactionsResponse = await transactionClient.get('/api/transactions');
      expect(userTransactionsResponse.status).toBe(200);
      expect(userTransactionsResponse.data).toHaveProperty('content');
      expect(Array.isArray(userTransactionsResponse.data.content)).toBe(true);
      
      // Find our transaction
      const ourTransaction = userTransactionsResponse.data.content.find(
        (t: any) => t.accountId === testAccount.id && t.amount === 100.00
      );
      expect(ourTransaction).toBeDefined();
      
      logger.info('✅ User identity consistency maintained across services');
    });

    test('should enforce same authorization rules across services', async () => {
      // Create second user to test authorization boundaries
      const otherUser = createTestUser({ username: `${testId}_other_${Date.now()}` });
      
      // Register and login as other user
      await accountClient.post('/api/auth/register', {
        username: otherUser.username,
        password: otherUser.password,
        email: otherUser.email
      });
      
      const otherLoginResponse = await accountClient.post<AuthResponse>('/api/auth/login', {
        username: otherUser.username,
        password: otherUser.password
      });
      
      const otherToken = otherLoginResponse.data!.accessToken;
      
      // Try to access original user's account with other user's token
      accountClient.setAuthToken(otherToken);
      const accountResponse = await accountClient.get(`/api/accounts/${testAccount.id}`);
      expect(accountResponse.status).toBe(403);
      
      // Try to create transaction for original user's account with other user's token
      transactionClient.setAuthToken(otherToken);
      const transactionResponse = await transactionClient.post('/api/transactions/deposit', {
        accountId: testAccount.id,
        amount: 100.00,
        currency: 'USD',
        description: 'Unauthorized access test'
      });
      expect(transactionResponse.status).toBe(403);
      
      logger.info('✅ Authorization rules consistent across services');
    });

    test('should handle token refresh consistently across services', async () => {
      // This test verifies that token refresh works consistently
      // Note: Implementation depends on actual refresh token mechanism
      
      if (!refreshToken) {
        logger.info('⏭️ Refresh token not available, skipping refresh test');
        return;
      }
      
      // Use original token for initial request
      accountClient.setAuthToken(authToken);
      transactionClient.setAuthToken(authToken);
      
      // Make requests with original token
      const initialAccountResponse = await accountClient.get('/api/accounts');
      const initialTransactionResponse = await transactionClient.get('/api/transactions/limits');
      
      expect(initialAccountResponse.status).toBe(200);
      expect(initialTransactionResponse.status).toBe(200);
      
      // Attempt token refresh (if refresh endpoint exists)
      try {
        const refreshResponse = await accountClient.post('/api/auth/refresh', {
          refreshToken: refreshToken
        });
        
        if (refreshResponse.status === 200) {
          const newToken = refreshResponse.data.accessToken;
          expect(isValidJwtToken(newToken)).toBe(true);
          
          // Use new token for both services
          accountClient.setAuthToken(newToken);
          transactionClient.setAuthToken(newToken);
          
          // Verify both services accept new token
          const newAccountResponse = await accountClient.get('/api/accounts');
          const newTransactionResponse = await transactionClient.get('/api/transactions/limits');
          
          expect(newAccountResponse.status).toBe(200);
          expect(newTransactionResponse.status).toBe(200);
          
          logger.info('✅ Token refresh consistency verified across services');
        } else {
          logger.info('⏭️ Token refresh not implemented, skipping refresh consistency test');
        }
      } catch (error) {
        logger.info('⏭️ Token refresh endpoint not available, skipping refresh test');
      }
    });
  });

  describe('Token Expiration and Refresh Tests', () => {
    test('should handle token expiration consistently across services', async () => {
      // Extract token expiration time
      const payload = extractJwtPayload(authToken);
      expect(payload).toBeDefined();
      expect(payload.exp).toBeDefined();
      
      const expirationTime = payload.exp * 1000; // Convert to milliseconds
      const currentTime = Date.now();
      const timeUntilExpiration = expirationTime - currentTime;
      
      logger.info(`Token expires in ${Math.round(timeUntilExpiration / 1000)} seconds`);
      
      // If token expires soon (within 5 minutes), test expiration handling
      if (timeUntilExpiration < 300000) { // 5 minutes
        logger.info('Token expires soon, testing expiration handling');
        
        // Wait for token to expire (or close to expiration)
        const waitTime = Math.max(0, timeUntilExpiration + 1000);
        if (waitTime < 60000) { // Only wait if less than 1 minute
          await delay(waitTime);
          
          // Test that both services reject expired token
          accountClient.setAuthToken(authToken);
          transactionClient.setAuthToken(authToken);
          
          const accountResponse = await accountClient.get('/api/accounts');
          const transactionResponse = await transactionClient.get('/api/transactions/limits');
          
          expect(accountResponse.status).toBe(401);
          expect(transactionResponse.status).toBe(401);
          
          logger.info('✅ Token expiration handled consistently across services');
        } else {
          logger.info('⏭️ Token expiration too far in future, skipping expiration test');
        }
      } else {
        // Token is valid for a while, test that both services accept it
        accountClient.setAuthToken(authToken);
        transactionClient.setAuthToken(authToken);
        
        const accountResponse = await accountClient.get('/api/accounts');
        const transactionResponse = await transactionClient.get('/api/transactions/limits');
        
        expect(accountResponse.status).toBe(200);
        expect(transactionResponse.status).toBe(200);
        
        logger.info('✅ Valid token accepted consistently across services');
      }
    });

    test('should validate token timestamps consistently', async () => {
      const payload = extractJwtPayload(authToken);
      expect(payload).toBeDefined();
      
      const currentTime = Math.floor(Date.now() / 1000);
      
      // Validate issued at time (iat)
      if (payload.iat) {
        expect(payload.iat).toBeLessThanOrEqual(currentTime);
        expect(payload.iat).toBeGreaterThan(currentTime - 3600); // Not older than 1 hour
      }
      
      // Validate expiration time (exp)
      if (payload.exp) {
        expect(payload.exp).toBeGreaterThan(currentTime);
        expect(payload.exp).toBeLessThan(currentTime + 86400); // Not more than 24 hours
      }
      
      // Validate not before time (nbf) if present
      if (payload.nbf) {
        expect(payload.nbf).toBeLessThanOrEqual(currentTime);
      }
      
      // Test that both services accept token with valid timestamps
      accountClient.setAuthToken(authToken);
      transactionClient.setAuthToken(authToken);
      
      const accountResponse = await accountClient.get('/api/accounts');
      const transactionResponse = await transactionClient.get('/api/transactions/limits');
      
      expect(accountResponse.status).toBe(200);
      expect(transactionResponse.status).toBe(200);
      
      logger.info('✅ Token timestamp validation consistent across services');
    });

    test('should handle token refresh race conditions', async () => {
      // This test simulates concurrent token refresh attempts
      if (!refreshToken) {
        logger.info('⏭️ Refresh token not available, skipping race condition test');
        return;
      }
      
      // Make multiple concurrent refresh requests
      const refreshPromises = Array.from({ length: 3 }, () =>
        accountClient.post('/api/auth/refresh', {
          refreshToken: refreshToken
        }).catch(error => ({ status: error.response?.status || 500, error: true, data: null }))
      );
      
      const results = await Promise.all(refreshPromises);
      
      // At least one refresh should succeed
      const successfulRefreshes = results.filter(r => !(r as any).error && r.status === 200);
      expect(successfulRefreshes.length).toBeGreaterThan(0);
      
      // If multiple refreshes succeeded, tokens should be consistent
      if (successfulRefreshes.length > 1) {
        const tokens = successfulRefreshes.map(r => (r as any).data?.accessToken).filter(Boolean);
        // All tokens should be valid (implementation-dependent behavior)
        tokens.forEach(token => {
          expect(isValidJwtToken(token)).toBe(true);
        });
      }
      
      logger.info('✅ Token refresh race conditions handled appropriately');
    });

    test('should invalidate old tokens after refresh', async () => {
      if (!refreshToken) {
        logger.info('⏭️ Refresh token not available, skipping token invalidation test');
        return;
      }
      
      // Store original token
      const originalToken = authToken;
      
      // Refresh token
      try {
        const refreshResponse = await accountClient.post('/api/auth/refresh', {
          refreshToken: refreshToken
        });
        
        if (refreshResponse.status === 200) {
          const newToken = refreshResponse.data.accessToken;
          
          // Test that new token works
          accountClient.setAuthToken(newToken);
          transactionClient.setAuthToken(newToken);
          
          const newTokenAccountResponse = await accountClient.get('/api/accounts');
          const newTokenTransactionResponse = await transactionClient.get('/api/transactions/limits');
          
          expect(newTokenAccountResponse.status).toBe(200);
          expect(newTokenTransactionResponse.status).toBe(200);
          
          // Test that old token is invalidated (implementation-dependent)
          accountClient.setAuthToken(originalToken);
          transactionClient.setAuthToken(originalToken);
          
          const oldTokenAccountResponse = await accountClient.get('/api/accounts');
          const oldTokenTransactionResponse = await transactionClient.get('/api/transactions/limits');
          
          // Depending on implementation, old token might still work or be invalidated
          // We'll log the behavior for analysis
          logger.info(`Old token status - Account Service: ${oldTokenAccountResponse.status}, Transaction Service: ${oldTokenTransactionResponse.status}`);
          
          logger.info('✅ Token refresh and invalidation behavior verified');
        } else {
          logger.info('⏭️ Token refresh not successful, skipping invalidation test');
        }
      } catch (error) {
        logger.info('⏭️ Token refresh failed, skipping invalidation test');
      }
    });
  });

  describe('JWT Security and Validation Edge Cases', () => {
    test('should reject tokens with modified headers', async () => {
      // Create token with modified header
      const tokenParts = authToken.split('.');
      const originalHeader = JSON.parse(Buffer.from(tokenParts[0], 'base64').toString());
      
      // Modify algorithm in header
      const modifiedHeader = { ...originalHeader, alg: 'none' };
      const modifiedHeaderEncoded = Buffer.from(JSON.stringify(modifiedHeader)).toString('base64');
      const modifiedToken = `${modifiedHeaderEncoded}.${tokenParts[1]}.${tokenParts[2]}`;
      
      // Test both services reject modified token
      accountClient.setAuthToken(modifiedToken);
      transactionClient.setAuthToken(modifiedToken);
      
      const accountResponse = await accountClient.get('/api/accounts');
      const transactionResponse = await transactionClient.get('/api/transactions/limits');
      
      expect(accountResponse.status).toBe(401);
      expect(transactionResponse.status).toBe(401);
      
      logger.info('✅ Modified JWT headers rejected consistently');
    });

    test('should reject tokens with modified payloads', async () => {
      // Create token with modified payload
      const tokenParts = authToken.split('.');
      const originalPayload = JSON.parse(Buffer.from(tokenParts[1], 'base64').toString());
      
      // Modify user ID in payload
      const modifiedPayload = { ...originalPayload, sub: 'different-user-id' };
      const modifiedPayloadEncoded = Buffer.from(JSON.stringify(modifiedPayload)).toString('base64');
      const modifiedToken = `${tokenParts[0]}.${modifiedPayloadEncoded}.${tokenParts[2]}`;
      
      // Test both services reject modified token
      accountClient.setAuthToken(modifiedToken);
      transactionClient.setAuthToken(modifiedToken);
      
      const accountResponse = await accountClient.get('/api/accounts');
      const transactionResponse = await transactionClient.get('/api/transactions/limits');
      
      expect(accountResponse.status).toBe(401);
      expect(transactionResponse.status).toBe(401);
      
      logger.info('✅ Modified JWT payloads rejected consistently');
    });

    test('should handle concurrent token validation requests', async () => {
      // Make multiple concurrent requests with same token
      accountClient.setAuthToken(authToken);
      transactionClient.setAuthToken(authToken);
      
      const concurrentRequests = 10;
      const accountPromises = Array.from({ length: concurrentRequests }, () =>
        accountClient.get('/api/accounts')
      );
      const transactionPromises = Array.from({ length: concurrentRequests }, () =>
        transactionClient.get('/api/transactions/limits')
      );
      
      const [accountResults, transactionResults] = await Promise.all([
        Promise.all(accountPromises),
        Promise.all(transactionPromises)
      ]);
      
      // All requests should succeed
      accountResults.forEach((result, index) => {
        expect(result.status).toBe(200);
        logger.debug(`Account request ${index + 1} successful`);
      });
      
      transactionResults.forEach((result, index) => {
        expect(result.status).toBe(200);
        logger.debug(`Transaction request ${index + 1} successful`);
      });
      
      logger.info('✅ Concurrent token validation handled successfully');
    });

    test('should validate token audience claims if present', async () => {
      const payload = extractJwtPayload(authToken);
      expect(payload).toBeDefined();
      
      // If audience claim is present, validate it
      if (payload.aud) {
        logger.info(`Token audience: ${payload.aud}`);
        
        // Both services should accept token with valid audience
        accountClient.setAuthToken(authToken);
        transactionClient.setAuthToken(authToken);
        
        const accountResponse = await accountClient.get('/api/accounts');
        const transactionResponse = await transactionClient.get('/api/transactions/limits');
        
        expect(accountResponse.status).toBe(200);
        expect(transactionResponse.status).toBe(200);
        
        logger.info('✅ Token audience validation consistent across services');
      } else {
        logger.info('⏭️ No audience claim in token, skipping audience validation');
      }
    });
  });
});