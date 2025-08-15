import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { logger } from './logger';
import { ServiceConfiguration, ServiceEndpoint } from '../types';

/**
 * Service health check validator for Account and Transaction services
 * Provides health endpoint validation, startup timeout handling, and readiness validation
 */
export class ServiceValidator {
  private config: ServiceEndpoint;
  private httpClient: AxiosInstance;
  private serviceName: string;

  constructor(config: ServiceEndpoint, serviceName: string) {
    this.config = config;
    this.serviceName = serviceName;
    this.httpClient = axios.create({
      baseURL: config.url,
      timeout: config.requestTimeout,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      }
    });
  }

  /**
   * Validate service health endpoint with retry mechanism
   */
  async validateHealthEndpoint(maxRetries: number = 10, retryDelay: number = 5000): Promise<boolean> {
    let lastError: Error | null = null;
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        logger.debug(`Service health check attempt ${attempt}/${maxRetries}`, {
          service: this.serviceName,
          url: this.config.url,
          healthEndpoint: this.config.healthEndpoint
        });

        const response = await this.httpClient.get(this.config.healthEndpoint);
        
        if (this.isHealthyResponse(response)) {
          logger.infrastructureStatus(this.serviceName, 'UP', {
            url: this.config.url,
            healthEndpoint: this.config.healthEndpoint,
            status: response.status,
            attempt
          });
          return true;
        } else {
          logger.warn(`Service health check returned unhealthy status`, {
            service: this.serviceName,
            status: response.status,
            data: response.data,
            attempt
          });
        }
      } catch (error) {
        lastError = error as Error;
        logger.warn(`Service health check attempt ${attempt} failed`, {
          service: this.serviceName,
          url: this.config.url,
          error: error instanceof Error ? error.message : error,
          attempt
        });

        if (attempt < maxRetries) {
          await this.delay(retryDelay);
        }
      }
    }

    logger.infrastructureStatus(this.serviceName, 'DOWN', {
      url: this.config.url,
      healthEndpoint: this.config.healthEndpoint,
      error: lastError?.message,
      maxRetries
    });

    throw new Error(`Service ${this.serviceName} health check failed after ${maxRetries} attempts: ${lastError?.message}`);
  }

  /**
   * Wait for service startup with configurable timeout
   */
  async waitForStartup(timeoutMs?: number): Promise<boolean> {
    const timeout = timeoutMs || this.config.startupTimeout;
    const startTime = Date.now();
    const maxRetries = Math.ceil(timeout / 5000); // Check every 5 seconds
    
    logger.info(`Waiting for service startup`, {
      service: this.serviceName,
      url: this.config.url,
      timeoutMs: timeout
    });

    try {
      await this.validateHealthEndpoint(maxRetries, 5000);
      const elapsedTime = Date.now() - startTime;
      
      logger.info(`Service startup completed`, {
        service: this.serviceName,
        url: this.config.url,
        elapsedTime
      });
      
      return true;
    } catch (error) {
      const elapsedTime = Date.now() - startTime;
      
      logger.error(`Service startup timeout`, {
        service: this.serviceName,
        url: this.config.url,
        timeoutMs: timeout,
        elapsedTime,
        error: error instanceof Error ? error.message : error
      });
      
      throw new Error(`Service ${this.serviceName} failed to start within ${timeout}ms`);
    }
  }

  /**
   * Validate service readiness with dependency checking
   */
  async validateReadiness(): Promise<{
    status: 'READY' | 'NOT_READY';
    details: {
      health: any;
      dependencies: any;
      responseTime: number;
      error?: string;
    };
  }> {
    const startTime = Date.now();
    let health: any = null;
    let dependencies: any = null;
    let error: string | undefined;

    try {
      logger.debug('Validating service readiness', {
        service: this.serviceName,
        url: this.config.url
      });

      // Check health endpoint
      const healthResponse = await this.httpClient.get(this.config.healthEndpoint);
      health = healthResponse.data;

      // Check if service has dependency information
      if (health && typeof health === 'object') {
        dependencies = this.extractDependencyInfo(health);
      }

      const isReady = this.isServiceReady(health, dependencies);
      const responseTime = Date.now() - startTime;

      const status = isReady ? 'READY' : 'NOT_READY';
      const details = {
        health,
        dependencies,
        responseTime,
        ...(error && { error })
      };

      logger.debug('Service readiness validation completed', {
        service: this.serviceName,
        status,
        details
      });

      return { status, details };
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      const responseTime = Date.now() - startTime;

      logger.error('Service readiness validation failed', {
        service: this.serviceName,
        url: this.config.url,
        error
      });

      return {
        status: 'NOT_READY',
        details: {
          health,
          dependencies,
          responseTime,
          error
        }
      };
    }
  }

  /**
   * Perform comprehensive service health check
   */
  async comprehensiveHealthCheck(): Promise<{
    status: 'UP' | 'DOWN';
    details: {
      endpoint: string;
      responseTime: number;
      httpStatus: number;
      health: any;
      dependencies: any;
      metrics: any;
      error?: string;
    };
  }> {
    const startTime = Date.now();
    let httpStatus = 0;
    let health: any = null;
    let dependencies: any = null;
    let metrics: any = null;
    let error: string | undefined;

    try {
      logger.debug('Performing comprehensive health check', {
        service: this.serviceName,
        url: this.config.url
      });

      // Health endpoint check
      const healthResponse = await this.httpClient.get(this.config.healthEndpoint);
      httpStatus = healthResponse.status;
      health = healthResponse.data;

      // Extract dependency information
      if (health && typeof health === 'object') {
        dependencies = this.extractDependencyInfo(health);
      }

      // Try to get metrics if available
      try {
        const metricsResponse = await this.httpClient.get('/actuator/metrics');
        metrics = metricsResponse.data;
      } catch (metricsError) {
        logger.debug('Metrics endpoint not available or failed', {
          service: this.serviceName,
          error: metricsError instanceof Error ? metricsError.message : metricsError
        });
      }

      const responseTime = Date.now() - startTime;
      const status = this.isHealthyResponse({ status: httpStatus, data: health } as AxiosResponse) ? 'UP' : 'DOWN';

      const details = {
        endpoint: `${this.config.url}${this.config.healthEndpoint}`,
        responseTime,
        httpStatus,
        health,
        dependencies,
        metrics,
        ...(error && { error })
      };

      logger.debug('Comprehensive health check completed', {
        service: this.serviceName,
        status,
        details
      });

      return { status, details };
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      const responseTime = Date.now() - startTime;

      logger.error('Comprehensive health check failed', {
        service: this.serviceName,
        url: this.config.url,
        error
      });

      return {
        status: 'DOWN',
        details: {
          endpoint: `${this.config.url}${this.config.healthEndpoint}`,
          responseTime,
          httpStatus,
          health,
          dependencies,
          metrics,
          error
        }
      };
    }
  }

  /**
   * Test service endpoint availability
   */
  async testEndpointAvailability(endpoint: string = '/'): Promise<{
    available: boolean;
    responseTime: number;
    httpStatus: number;
    error?: string;
  }> {
    const startTime = Date.now();
    let httpStatus = 0;
    let error: string | undefined;

    try {
      const response = await this.httpClient.get(endpoint);
      httpStatus = response.status;
      const responseTime = Date.now() - startTime;

      const available = response.status >= 200 && response.status < 400;

      logger.debug('Endpoint availability test completed', {
        service: this.serviceName,
        endpoint,
        available,
        httpStatus,
        responseTime
      });

      return {
        available,
        responseTime,
        httpStatus
      };
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      const responseTime = Date.now() - startTime;

      if (axios.isAxiosError(err) && err.response) {
        httpStatus = err.response.status;
      }

      logger.debug('Endpoint availability test failed', {
        service: this.serviceName,
        endpoint,
        error,
        httpStatus,
        responseTime
      });

      return {
        available: false,
        responseTime,
        httpStatus,
        error
      };
    }
  }

  /**
   * Check if the health response indicates a healthy service
   */
  private isHealthyResponse(response: AxiosResponse): boolean {
    // Check HTTP status
    if (response.status !== 200) {
      return false;
    }

    // Check response body for Spring Boot Actuator format
    if (response.data && typeof response.data === 'object') {
      const status = response.data.status;
      return status === 'UP';
    }

    // If no specific format, assume healthy if 200 OK
    return true;
  }

  /**
   * Extract dependency information from health response
   */
  private extractDependencyInfo(health: any): any {
    if (!health || typeof health !== 'object') {
      return null;
    }

    const dependencies: any = {};

    // Spring Boot Actuator health format
    if (health.components) {
      Object.keys(health.components).forEach(component => {
        const componentHealth = health.components[component];
        dependencies[component] = {
          status: componentHealth.status,
          details: componentHealth.details || {}
        };
      });
    }

    // Legacy format
    if (health.db) {
      dependencies.database = {
        status: health.db.status,
        details: health.db.details || {}
      };
    }

    if (health.redis) {
      dependencies.redis = {
        status: health.redis.status,
        details: health.redis.details || {}
      };
    }

    return Object.keys(dependencies).length > 0 ? dependencies : null;
  }

  /**
   * Determine if service is ready based on health and dependencies
   */
  private isServiceReady(health: any, dependencies: any): boolean {
    // Check overall health status
    if (!health || health.status !== 'UP') {
      return false;
    }

    // Check dependencies if available
    if (dependencies) {
      for (const [name, dep] of Object.entries(dependencies)) {
        const dependency = dep as any;
        if (dependency.status !== 'UP') {
          logger.warn(`Service dependency not ready`, {
            service: this.serviceName,
            dependency: name,
            status: dependency.status
          });
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Utility method for delays
   */
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

/**
 * Factory class for creating service validators
 */
export class ServiceValidatorFactory {
  static createAccountServiceValidator(config: ServiceEndpoint): ServiceValidator {
    return new ServiceValidator(config, 'account-service');
  }

  static createTransactionServiceValidator(config: ServiceEndpoint): ServiceValidator {
    return new ServiceValidator(config, 'transaction-service');
  }

  /**
   * Validate all services in the configuration
   */
  static async validateAllServices(serviceConfig: ServiceConfiguration): Promise<{
    accountService: { health: any; readiness: any; availability: any };
    transactionService: { health: any; readiness: any; availability: any };
  }> {
    const accountValidator = this.createAccountServiceValidator(serviceConfig.accountService);
    const transactionValidator = this.createTransactionServiceValidator(serviceConfig.transactionService);

    logger.info('Starting comprehensive service validation');

    try {
      // Validate services in parallel
      const [accountResults, transactionResults] = await Promise.all([
        this.validateSingleService(accountValidator),
        this.validateSingleService(transactionValidator)
      ]);

      const results = {
        accountService: accountResults,
        transactionService: transactionResults
      };

      logger.info('Service validation completed', results);
      return results;
    } catch (error) {
      logger.error('Service validation failed', {
        error: error instanceof Error ? error.message : error
      });
      throw error;
    }
  }

  /**
   * Wait for all services to start up
   */
  static async waitForAllServices(serviceConfig: ServiceConfiguration): Promise<void> {
    const accountValidator = this.createAccountServiceValidator(serviceConfig.accountService);
    const transactionValidator = this.createTransactionServiceValidator(serviceConfig.transactionService);

    logger.info('Waiting for all services to start up');

    try {
      // Wait for services in parallel
      await Promise.all([
        accountValidator.waitForStartup(),
        transactionValidator.waitForStartup()
      ]);

      logger.info('All services started successfully');
    } catch (error) {
      logger.error('Service startup failed', {
        error: error instanceof Error ? error.message : error
      });
      throw error;
    }
  }

  /**
   * Validate a single service comprehensively
   */
  private static async validateSingleService(validator: ServiceValidator): Promise<{
    health: any;
    readiness: any;
    availability: any;
  }> {
    // Perform comprehensive health check
    const health = await validator.comprehensiveHealthCheck();
    
    // Validate readiness
    const readiness = await validator.validateReadiness();
    
    // Test endpoint availability
    const availability = await validator.testEndpointAvailability();

    return {
      health,
      readiness,
      availability
    };
  }
}