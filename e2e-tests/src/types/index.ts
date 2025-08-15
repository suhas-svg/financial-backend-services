// Core test types and interfaces

export interface TestConfiguration {
  services: ServiceConfiguration;
  databases: DatabaseConfiguration;
  cache: CacheConfiguration;
  testData: TestDataConfiguration;
  performance: PerformanceConfiguration;
  reporting: ReportingConfiguration;
  timeouts: TimeoutConfiguration;
}

export interface ServiceConfiguration {
  accountService: ServiceEndpoint;
  transactionService: ServiceEndpoint;
}

export interface ServiceEndpoint {
  url: string;
  healthEndpoint: string;
  startupTimeout: number;
  requestTimeout: number;
}

export interface DatabaseConfiguration {
  accountDb: DatabaseConnection;
  transactionDb: DatabaseConnection;
}

export interface DatabaseConnection {
  host: string;
  port: number;
  database: string;
  username: string;
  password: string;
  connectionTimeout: number;
}

export interface CacheConfiguration {
  redis: {
    host: string;
    port: number;
    password?: string;
    connectionTimeout: number;
  };
}

export interface TestDataConfiguration {
  users: TestUser[];
  accounts: TestAccount[];
  cleanupAfterTests: boolean;
}

export interface TestUser {
  username: string;
  password: string;
  email?: string;
  accounts?: TestAccount[];
}

export interface TestAccount {
  accountType: 'CHECKING' | 'SAVINGS' | 'CREDIT';
  initialBalance: number;
  currency?: string;
}

export interface PerformanceConfiguration {
  concurrentUsers: number;
  testDuration: number;
  rampUpTime: number;
  thresholds: PerformanceThresholds;
}

export interface PerformanceThresholds {
  averageResponseTime: number;
  p95ResponseTime: number;
  errorRate: number;
  throughput: number;
}

export interface ReportingConfiguration {
  outputDir: string;
  formats: ReportFormat[];
  includeMetrics: boolean;
  includeErrorDetails: boolean;
}

export type ReportFormat = 'html' | 'json' | 'csv' | 'junit';

export interface TimeoutConfiguration {
  testTimeout: number;
  setupTimeout: number;
  teardownTimeout: number;
  httpTimeout: number;
  databaseTimeout: number;
}

// Test execution types
export interface TestResults {
  testRun: TestRunInfo;
  suites: TestSuiteResult[];
  metrics: TestMetrics;
  errors: TestError[];
}

export interface TestRunInfo {
  id: string;
  startTime: string;
  endTime: string;
  duration: number;
  status: TestStatus;
  environment: string;
}

export interface TestSuiteResult {
  name: string;
  status: TestStatus;
  duration: number;
  tests: TestResult[];
  metrics?: SuiteMetrics;
}

export interface TestResult {
  name: string;
  status: TestStatus;
  duration: number;
  details?: string;
  error?: TestError;
  metrics?: TestResultMetrics;
}

export interface TestMetrics {
  totalTests: number;
  passed: number;
  failed: number;
  skipped: number;
  coverage: CoverageMetrics;
  performance: PerformanceMetrics;
}

export interface CoverageMetrics {
  endpoints: number;
  workflows: number;
  errorScenarios: number;
}

export interface PerformanceMetrics {
  averageResponseTime: number;
  p95ResponseTime: number;
  p99ResponseTime: number;
  throughput: number;
  errorRate: number;
}

export interface SuiteMetrics {
  endpointsCovered: string[];
  averageResponseTime: number;
  totalRequests: number;
  failedRequests: number;
}

export interface TestResultMetrics {
  responseTime: number;
  requestSize: number;
  responseSize: number;
  httpStatus: number;
}

export interface TestError {
  message: string;
  stack?: string;
  code?: string;
  details?: any;
  timestamp: string;
}

export type TestStatus = 'PASSED' | 'FAILED' | 'SKIPPED' | 'RUNNING';

// API response types
export interface ApiResponse<T = any> {
  data?: T;
  status: number;
  statusText: string;
  headers: Record<string, string>;
  duration: number;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
  user?: UserInfo;
}

export interface UserInfo {
  id: string;
  username: string;
  email?: string;
  roles: string[];
}

export interface AccountInfo {
  id: string;
  ownerId: string;
  accountType: string;
  balance: number;
  currency: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionInfo {
  id: string;
  accountId: string;
  type: string;
  amount: number;
  currency: string;
  status: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

// Custom matcher types for Jest
declare global {
  namespace jest {
    interface Matchers<R> {
      toHaveValidApiResponse(): R;
      toHaveHttpStatus(status: number): R;
      toHaveResponseTime(maxTime: number): R;
      toHaveValidJwtToken(): R;
      toHaveValidAccountStructure(): R;
      toHaveValidTransactionStructure(): R;
      toBeWithinRange(min: number, max: number): R;
    }
  }
}

export {};