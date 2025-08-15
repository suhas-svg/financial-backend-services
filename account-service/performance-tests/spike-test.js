import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const responseTime = new Trend('response_time');

// Spike test configuration - sudden increases in load
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Normal load
    { duration: '10s', target: 200 },  // Sudden spike to 200 users
    { duration: '30s', target: 200 },  // Stay at spike level
    { duration: '10s', target: 10 },   // Drop back to normal
    { duration: '30s', target: 10 },   // Normal load
    { duration: '10s', target: 300 },  // Even bigger spike
    { duration: '30s', target: 300 },  // Stay at higher spike
    { duration: '10s', target: 0 },    // Drop to zero
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'], // 95% of requests must complete below 2s (relaxed for spike test)
    http_req_failed: ['rate<0.1'],     // Error rate must be below 10% (relaxed for spike test)
    errors: ['rate<0.1'],              // Custom error rate must be below 10%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const testUser = { username: 'spikeuser', password: 'password123' };
let authToken = '';

export function setup() {
  console.log('Setting up spike test...');
  
  const registerPayload = {
    username: testUser.username,
    email: `${testUser.username}@example.com`,
    password: testUser.password,
    firstName: 'Spike',
    lastName: 'User'
  };

  const registerResponse = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify(registerPayload), {
    headers: { 'Content-Type': 'application/json' },
  });

  if (registerResponse.status === 201 || registerResponse.status === 400) {
    const loginPayload = {
      username: testUser.username,
      password: testUser.password
    };

    const loginResponse = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify(loginPayload), {
      headers: { 'Content-Type': 'application/json' },
    });

    if (loginResponse.status === 200) {
      const loginData = JSON.parse(loginResponse.body);
      authToken = loginData.token;
      console.log('Successfully authenticated spike test user');
    }
  }

  return { authToken };
}

export default function(data) {
  if (!data.authToken) {
    console.error('No auth token available for spike test');
    errorRate.add(1);
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.authToken}`
  };

  // Focus on critical path operations during spikes
  spikeTestCriticalPath(headers);
  
  // Very short sleep to simulate real spike behavior
  sleep(0.5);
}

function spikeTestCriticalPath(headers) {
  // Test the most critical operations that users would perform
  
  // 1. Health check (should always be fast)
  const healthResponse = http.get(`${BASE_URL}/actuator/health`);
  check(healthResponse, {
    'Spike health check status is 200': (r) => r.status === 200,
    'Spike health check response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  // 2. Get accounts (common read operation)
  const accountsResponse = http.get(`${BASE_URL}/api/accounts`, { headers });
  const accountsSuccess = check(accountsResponse, {
    'Spike get accounts status is 200': (r) => r.status === 200,
    'Spike get accounts response time < 2000ms': (r) => r.timings.duration < 2000,
  });
  
  // 3. Create account (write operation - most resource intensive)
  const accountPayload = {
    accountType: 'CHECKING',
    ownerId: `spikeuser${Math.floor(Math.random() * 10000)}`,
    balance: 1000
  };

  const createResponse = http.post(`${BASE_URL}/api/accounts`, JSON.stringify(accountPayload), { headers });
  const createSuccess = check(createResponse, {
    'Spike create account status is 201': (r) => r.status === 201,
    'Spike create account response time < 3000ms': (r) => r.timings.duration < 3000,
  });

  // Record overall success/failure
  const overallSuccess = accountsSuccess && createSuccess;
  errorRate.add(!overallSuccess);
  
  // Record response times
  responseTime.add(healthResponse.timings.duration);
  responseTime.add(accountsResponse.timings.duration);
  responseTime.add(createResponse.timings.duration);
}

export function teardown(data) {
  console.log('Spike test completed');
  console.log('Check how the system handled sudden load increases and recovery');
}