import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const responseTime = new Trend('response_time');
const requestCount = new Counter('requests');

// Stress test configuration - gradually increase load to find breaking point
export const options = {
  stages: [
    { duration: '1m', target: 50 },   // Ramp up to 50 users
    { duration: '2m', target: 100 },  // Ramp up to 100 users
    { duration: '2m', target: 200 },  // Ramp up to 200 users
    { duration: '2m', target: 300 },  // Ramp up to 300 users
    { duration: '2m', target: 400 },  // Ramp up to 400 users
    { duration: '2m', target: 500 },  // Ramp up to 500 users - stress point
    { duration: '5m', target: 500 },  // Stay at 500 users for 5 minutes
    { duration: '1m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95% of requests must complete below 1s (relaxed for stress test)
    http_req_failed: ['rate<0.05'],    // Error rate must be below 5% (relaxed for stress test)
    errors: ['rate<0.05'],             // Custom error rate must be below 5%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Simplified test data for stress testing
const testUser = { username: 'stressuser', password: 'password123' };
let authToken = '';

export function setup() {
  console.log('Setting up stress test...');
  
  // Register and authenticate a single user for stress testing
  const registerPayload = {
    username: testUser.username,
    email: `${testUser.username}@example.com`,
    password: testUser.password,
    firstName: 'Stress',
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
      console.log('Successfully authenticated stress test user');
    }
  }

  return { authToken };
}

export default function(data) {
  requestCount.add(1);
  
  if (!data.authToken) {
    console.error('No auth token available for stress test');
    errorRate.add(1);
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.authToken}`
  };

  // Stress test focuses on high-frequency operations
  const operations = [
    () => stressTestHealthCheck(),
    () => stressTestGetAccounts(headers),
    () => stressTestCreateAccount(headers),
  ];

  // Execute multiple operations per iteration to increase stress
  for (let i = 0; i < 3; i++) {
    const operation = operations[Math.floor(Math.random() * operations.length)];
    operation();
    
    // Minimal sleep to maximize stress
    sleep(0.1);
  }
}

function stressTestHealthCheck() {
  const response = http.get(`${BASE_URL}/actuator/health`);
  
  const success = check(response, {
    'Stress health check status is 200': (r) => r.status === 200,
    'Stress health check response time < 1000ms': (r) => r.timings.duration < 1000,
  });

  errorRate.add(!success);
  responseTime.add(response.timings.duration);
}

function stressTestGetAccounts(headers) {
  const response = http.get(`${BASE_URL}/api/accounts`, { headers });
  
  const success = check(response, {
    'Stress get accounts status is 200': (r) => r.status === 200,
    'Stress get accounts response time < 1000ms': (r) => r.timings.duration < 1000,
  });

  errorRate.add(!success);
  responseTime.add(response.timings.duration);
}

function stressTestCreateAccount(headers) {
  const accountPayload = {
    accountType: 'CHECKING',
    ownerId: `stressuser${Math.floor(Math.random() * 100000)}`,
    balance: Math.floor(Math.random() * 1000) + 100
  };

  const response = http.post(`${BASE_URL}/api/accounts`, JSON.stringify(accountPayload), { headers });
  
  const success = check(response, {
    'Stress create account status is 201': (r) => r.status === 201,
    'Stress create account response time < 1000ms': (r) => r.timings.duration < 1000,
  });

  errorRate.add(!success);
  responseTime.add(response.timings.duration);
}

export function teardown(data) {
  console.log('Stress test completed');
  console.log('Check the results to identify the breaking point and performance bottlenecks');
}