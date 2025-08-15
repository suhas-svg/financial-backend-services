import winston from 'winston';
import { testConfig } from '../config/test-config';

/**
 * Centralized logging utility for E2E tests
 * Provides structured logging with different levels and formats
 */
class TestLogger {
  private logger: winston.Logger;

  constructor() {
    const reportingConfig = testConfig.getReportingConfig();
    
    this.logger = winston.createLogger({
      level: process.env.LOG_LEVEL || 'info',
      format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.errors({ stack: true }),
        winston.format.json()
      ),
      defaultMeta: { service: 'e2e-tests' },
      transports: [
        // Console transport for development
        new winston.transports.Console({
          format: winston.format.combine(
            winston.format.colorize(),
            winston.format.simple(),
            winston.format.printf(({ timestamp, level, message, ...meta }) => {
              const metaStr = Object.keys(meta).length ? JSON.stringify(meta, null, 2) : '';
              return `${timestamp} [${level}]: ${message} ${metaStr}`;
            })
          )
        }),
        
        // File transport for test logs
        new winston.transports.File({
          filename: `${reportingConfig.outputDir}/test-error.log`,
          level: 'error',
          format: winston.format.combine(
            winston.format.timestamp(),
            winston.format.json()
          )
        }),
        
        new winston.transports.File({
          filename: `${reportingConfig.outputDir}/test-combined.log`,
          format: winston.format.combine(
            winston.format.timestamp(),
            winston.format.json()
          )
        })
      ]
    });
  }

  info(message: string, meta?: any): void {
    this.logger.info(message, meta);
  }

  error(message: string, error?: Error | any): void {
    this.logger.error(message, { error: error?.stack || error });
  }

  warn(message: string, meta?: any): void {
    this.logger.warn(message, meta);
  }

  debug(message: string, meta?: any): void {
    this.logger.debug(message, meta);
  }

  /**
   * Log test start
   */
  testStart(testName: string, suiteName?: string): void {
    this.info('Test started', { testName, suiteName, timestamp: new Date().toISOString() });
  }

  /**
   * Log test completion
   */
  testComplete(testName: string, status: 'PASSED' | 'FAILED', duration: number, error?: Error): void {
    const logData = {
      testName,
      status,
      duration,
      timestamp: new Date().toISOString()
    };

    if (status === 'PASSED') {
      this.info('Test completed successfully', logData);
    } else {
      this.error('Test failed', { ...logData, error: error?.stack || error });
    }
  }

  /**
   * Log API request
   */
  apiRequest(method: string, url: string, data?: any): void {
    this.debug('API request', { method, url, data });
  }

  /**
   * Log API response
   */
  apiResponse(method: string, url: string, status: number, duration: number, data?: any): void {
    this.debug('API response', { method, url, status, duration, data });
  }

  /**
   * Log performance metrics
   */
  performanceMetrics(metrics: any): void {
    this.info('Performance metrics', metrics);
  }

  /**
   * Log infrastructure status
   */
  infrastructureStatus(component: string, status: 'UP' | 'DOWN', details?: any): void {
    if (status === 'UP') {
      this.info(`Infrastructure component ready: ${component}`, details);
    } else {
      this.error(`Infrastructure component failed: ${component}`, details);
    }
  }

  /**
   * Log test suite start
   */
  suiteStart(suiteName: string): void {
    this.info(`Test suite started: ${suiteName}`, { suiteName, timestamp: new Date().toISOString() });
  }

  /**
   * Log test suite completion
   */
  suiteComplete(suiteName: string, results: any): void {
    this.info(`Test suite completed: ${suiteName}`, { suiteName, results, timestamp: new Date().toISOString() });
  }

  /**
   * Create child logger with additional context
   */
  child(meta: any): TestLogger {
    const childLogger = new TestLogger();
    childLogger.logger = this.logger.child(meta);
    return childLogger;
  }
}

// Export singleton instance
export const logger = new TestLogger();