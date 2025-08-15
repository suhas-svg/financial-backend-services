import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const responseTime = new Trend('response_time');

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // Ramp up to 10 users over 30 seconds
    { duration: '1m', target: 10 },   // Stay at 10 users for 1 minute
    { duration: '30s', target: 50 },  // Ramp up to 50 users over 30 seconds
    { duration: '2m', target: 50 },   // Stay at 50 users for 2 minutes
    { duration: '30s', target: 100 }, // Ramp up to 100 users over 30 seconds
    { duration: '2m', target: 100 },  // Stay at 100 users for 2 minutes
    { duration: '30s', target: 0 },   // Ramp down to 0 users over 30 seconds
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate must be below 1%
    errors: ['rate<0.01'],            // Custom error rate must be below 1%
  },
};

// Base URL - can be overridden with environment variable
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Test data
const testUsers = [
  { username: 'testuser1', password: 'password123' },
  { username: 'testuser2', password: 'password123' },
  { username: 'testuser3', password: 'password123' },
];

let authTokens = [];

// Setup function - runs once before the test
export function setup() {
  console.log('Setting up performance test...');
  
  // Register test users and get auth tokens
  testUsers.forEach((user, index) => {
    // Register user
    const registerPayload = {
      username: user.username,
      email: `${user.username}@example.com`,
      password: user.password,
      firstName: 'Test',
      lastName: `User${index + 1}`
    };

    const registerResponse = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify(registerPayload), {
      headers: { 'Content-Type': 'application/json' },
    });

    if (registerResponse.status === 201 || registerResponse.status === 400) {
      // User registered or already exists, now login
      const loginPayload = {
        username: user.username,
        password: user.password
      };

      const loginResponse = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify(loginPayload), {
        headers: { 'Content-Type': 'application/json' },
      });

      if (loginResponse.status === 200) {
        const loginData = JSON.parse(loginResponse.body);
        authTokens.push(loginData.token);
        console.log(`Successfully authenticated user: ${user.username}`);
      } else {
        console.error(`Failed to authenticate user: ${user.username}, status: ${loginResponse.status}`);
      }
    } else {
      console.error(`Failed to register user: ${user.username}, status: ${registerResponse.status}`);
    }
  });

  return { authTokens };
}

// Main test function
export default function(data) {
  // Select a random auth token
  const token = data.authTokens[Math.floor(Math.random() * data.authTokens.length)];
  
  if (!token) {
    console.error('No auth token available');
    errorRate.add(1);
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  };

  // Test scenario: Create account, get accounts, get specific account
  const scenarios = [
    () => testCreateAccount(headers),
    () => testGetAllAccounts(headers),
    () => testGetAccountById(headers),
    () => testHealthCheck(),
  ];

  // Execute random scenario
  const scenario = scenarios[Math.floor(Math.random() * scenarios.length)];
  scenario();

  // Think time between requests
  sleep(Math.random() * 2 + 1); // 1-3 seconds
}

function testCreateAccount(headers) {
  const accountPayload = {
    accountType: Math.random() > 0.5 ? 'CHECKING' : 'SAVINGS',
    ownerId: `user${Math.floor(Math.random() * 1000)}`,
    balance: Math.floor(Math.random() * 10000) + 100
  };

  const response = http.post(`${BASE_URL}/api/accounts`, JSON.stringify(accountPayload), { headers });
  
  const success = check(response, {
    'Create account status is 201': (r) => r.status === 201,
    'Create account response time < 500ms': (r) => r.timings.duration < 500,
    'Create account has valid response': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.id && body.ownerId && body.balance;
      } catch (e) {
        return false;
      }
    }
  });

  errorRate.add(!success);
  responseTime.add(response.timings.duration);
}

function testGetAllAccounts(headers) {
  const response = http.get(`${BASE_URL}/api/accounts`, { headers });
  
  const success = check(response, {
    'Get all accounts status is 200': (r) => r.status === 200,
    'Get all accounts response time < 300ms': (r) => r.timings.duration < 300,
    'Get all accounts returns array': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body);
      } catch (e) {
        return false;
      }
    }
  });

  errorRate.add(!success);
  responseTime.add(response.timings.duration);
}

function testGetAccountById(headers) {
  // First get all accounts to get a valid ID
  const allAccountsResponse = http.get(`${BASE_URL}/api/accounts`, { headers });
  
  if (allAccountsResponse.status === 200) {
    try {
      const accounts = JSON.parse(allAccountsResponse.body);
      if (accounts.length > 0) {
        const accountId = accounts[0].id;
        const response = http.get(`${BASE_URL}/api/accounts/${accountId}`, { headers });
        
        const success = check(response, {
          'Get account by ID status is 200': (r) => r.status === 200,
          'Get account by ID response time < 300ms': (r) => r.timings.duration < 300,
          'Get account by ID returns valid account': (r) => {
            try {
              const body = JSON.parse(r.body);
              return body.id && body.ownerId;
            } catch (e) {
              return false;
            }
          }
        });

        errorRate.add(!success);
        responseTime.add(response.timings.duration);
        return;
      }
    } catch (e) {
      console.error('Failed to parse accounts response');
    }
  }

  // If we can't get a valid account ID, mark as error
  errorRate.add(1);
}

function testHealthCheck() {
  const response = http.get(`${BASE_URL}/actuator/health`);
  
  const success = check(response, {
    'Health check status is 200': (r) => r.status === 200,
    'Health check response time < 100ms': (r) => r.timings.duration < 100,
    'Health check status is UP': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.status === 'UP';
      } catch (e) {
        return false;
      }
    }
  });

  errorRate.add(!success);
  responseTime.add(response.timings.duration);
}

// Teardown function - runs once after the test
export function teardown(data) {
  console.log('Performance test completed');
  console.log(`Auth tokens used: ${data.authTokens.length}`);
}