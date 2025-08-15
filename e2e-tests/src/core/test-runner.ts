import { TestResults, TestSuiteResult, TestRunInfo, TestStatus } from '../types';
import { testConfig } from '../config/test-config';
import { logger } from '../utils/logger';
import { generateTestId } from '../utils/test-helpers';

/**
 * Central test runner for E2E test execution
 * Orchestrates test suites and collects results
 */
export class E2ETestRunner {
  private testRunId: string;
  private startTime: Date;
  private suiteResults: TestSuiteResult[] = [];

  constructor() {
    this.testRunId = generateTestId('run');
    this.startTime = new Date();
  }

  /**
   * Execute all test suites
   */
  async runAllTests(): Promise<TestResults> {
    logger.info('🚀 Starting comprehensive E2E test execution');
    
    try {
      // Validate configuration before starting
      testConfig.validateConfiguration();
      
      const config = testConfig.getConfig();
      logger.info('Test configuration validated', {
        services: Object.keys(config.services),
        testUsers: config.testData.users.length,
        concurrentUsers: config.performance.concurrentUsers
      });

      // Initialize test results
      const testResults: TestResults = {
        testRun: this.createTestRunInfo('RUNNING'),
        suites: [],
        metrics: {
          totalTests: 0,
          passed: 0,
          failed: 0,
          skipped: 0,
          coverage: {
            endpoints: 0,
            workflows: 0,
            errorScenarios: 0
          },
          performance: {
            averageResponseTime: 0,
            p95ResponseTime: 0,
            p99ResponseTime: 0,
            throughput: 0,
            errorRate: 0
          }
        },
        errors: []
      };

      // Note: Actual test suite execution will be implemented in subsequent tasks
      // This is the framework foundation that will orchestrate the test suites
      
      logger.info('✅ E2E test runner framework initialized successfully');
      
      // Update final results
      testResults.testRun = this.createTestRunInfo('PASSED');
      testResults.suites = this.suiteResults;
      
      return testResults;
      
    } catch (error) {
      logger.error('❌ E2E test execution failed', error);
      
      return {
        testRun: this.createTestRunInfo('FAILED'),
        suites: this.suiteResults,
        metrics: {
          totalTests: 0,
          passed: 0,
          failed: 1,
          skipped: 0,
          coverage: { endpoints: 0, workflows: 0, errorScenarios: 0 },
          performance: { averageResponseTime: 0, p95ResponseTime: 0, p99ResponseTime: 0, throughput: 0, errorRate: 1 }
        },
        errors: [{
          message: error instanceof Error ? error.message : 'Unknown error',
          stack: error instanceof Error ? error.stack : undefined,
          timestamp: new Date().toISOString()
        }]
      };
    }
  }

  /**
   * Execute a specific test suite
   */
  async runTestSuite(suiteName: string): Promise<TestSuiteResult> {
    logger.suiteStart(suiteName);
    const suiteStartTime = Date.now();
    
    try {
      // Placeholder for actual suite execution
      // Individual test suites will be implemented in subsequent tasks
      
      const suiteResult: TestSuiteResult = {
        name: suiteName,
        status: 'PASSED',
        duration: Date.now() - suiteStartTime,
        tests: [],
        metrics: {
          endpointsCovered: [],
          averageResponseTime: 0,
          totalRequests: 0,
          failedRequests: 0
        }
      };
      
      this.suiteResults.push(suiteResult);
      logger.suiteComplete(suiteName, suiteResult);
      
      return suiteResult;
      
    } catch (error) {
      logger.error(`Test suite failed: ${suiteName}`, error);
      
      const failedSuiteResult: TestSuiteResult = {
        name: suiteName,
        status: 'FAILED',
        duration: Date.now() - suiteStartTime,
        tests: []
      };
      
      this.suiteResults.push(failedSuiteResult);
      return failedSuiteResult;
    }
  }

  /**
   * Generate comprehensive test report
   */
  generateReport(results: TestResults): void {
    logger.info('📊 Generating test report');
    
    const reportSummary = {
      testRunId: results.testRun.id,
      duration: results.testRun.duration,
      status: results.testRun.status,
      totalSuites: results.suites.length,
      passedSuites: results.suites.filter(s => s.status === 'PASSED').length,
      failedSuites: results.suites.filter(s => s.status === 'FAILED').length,
      totalTests: results.metrics.totalTests,
      passRate: results.metrics.totalTests > 0 ? 
        (results.metrics.passed / results.metrics.totalTests * 100).toFixed(2) + '%' : '0%',
      averageResponseTime: results.metrics.performance.averageResponseTime,
      errorRate: (results.metrics.performance.errorRate * 100).toFixed(2) + '%'
    };
    
    logger.info('📋 Test Execution Summary', reportSummary);
    
    // Detailed suite results
    results.suites.forEach(suite => {
      logger.info(`📁 Suite: ${suite.name}`, {
        status: suite.status,
        duration: `${suite.duration}ms`,
        tests: suite.tests.length,
        passed: suite.tests.filter(t => t.status === 'PASSED').length,
        failed: suite.tests.filter(t => t.status === 'FAILED').length
      });
    });
    
    // Performance metrics
    if (results.metrics.performance.averageResponseTime > 0) {
      logger.performanceMetrics({
        averageResponseTime: results.metrics.performance.averageResponseTime,
        p95ResponseTime: results.metrics.performance.p95ResponseTime,
        throughput: results.metrics.performance.throughput,
        errorRate: results.metrics.performance.errorRate
      });
    }
    
    // Error summary
    if (results.errors.length > 0) {
      logger.error(`❌ ${results.errors.length} errors encountered during test execution`);
      results.errors.forEach((error, index) => {
        logger.error(`Error ${index + 1}: ${error.message}`, error);
      });
    }
  }

  /**
   * Create test run information
   */
  private createTestRunInfo(status: TestStatus): TestRunInfo {
    const endTime = new Date();
    
    return {
      id: this.testRunId,
      startTime: this.startTime.toISOString(),
      endTime: endTime.toISOString(),
      duration: endTime.getTime() - this.startTime.getTime(),
      status,
      environment: process.env.NODE_ENV || 'test'
    };
  }

  /**
   * Get test run ID
   */
  getTestRunId(): string {
    return this.testRunId;
  }

  /**
   * Get current suite results
   */
  getSuiteResults(): TestSuiteResult[] {
    return [...this.suiteResults];
  }
}