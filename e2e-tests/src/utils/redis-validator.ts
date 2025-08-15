import { createClient, RedisClientType } from 'redis';
import { logger } from './logger';
import { CacheConfiguration } from '../types';

/**
 * Redis cache validator with connection pooling tests and functionality validation
 * Provides connectivity validation, cache operations testing, and health checks
 */
export class RedisValidator {
  private config: CacheConfiguration['redis'];
  private client: RedisClientType | null = null;

  constructor(config: CacheConfiguration['redis']) {
    this.config = config;
  }

  /**
   * Validate Redis connectivity with retry mechanism
   */
  async validateConnectivity(maxRetries: number = 5, retryDelay: number = 2000): Promise<boolean> {
    let lastError: Error | null = null;
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        logger.debug(`Redis connectivity attempt ${attempt}/${maxRetries}`, {
          host: this.config.host,
          port: this.config.port
        });

        // Wrap the entire connection attempt in a timeout
        const attemptPromise = this.attemptConnection();
        const timeoutPromise = new Promise<boolean>((_, reject) => {
          setTimeout(() => {
            reject(new Error(`Redis connectivity attempt timeout after ${this.config.connectionTimeout * 2}ms`));
          }, this.config.connectionTimeout * 2);
        });

        const isConnected = await Promise.race([attemptPromise, timeoutPromise]);
        
        if (isConnected) {
          logger.infrastructureStatus('redis', 'UP', {
            host: this.config.host,
            port: this.config.port,
            attempt
          });
          return true;
        }
      } catch (error) {
        lastError = error as Error;
        logger.warn(`Redis connectivity attempt ${attempt} failed`, {
          host: this.config.host,
          port: this.config.port,
          error: error instanceof Error ? error.message : error,
          attempt
        });

        if (attempt < maxRetries) {
          await this.delay(retryDelay);
          retryDelay *= 1.5; // Exponential backoff
        }
      } finally {
        await this.disconnect();
      }
    }

    logger.infrastructureStatus('redis', 'DOWN', {
      host: this.config.host,
      port: this.config.port,
      error: lastError?.message,
      maxRetries
    });

    throw new Error(`Redis connectivity failed after ${maxRetries} attempts: ${lastError?.message}`);
  }

  /**
   * Attempt a single Redis connection
   */
  private async attemptConnection(): Promise<boolean> {
    await this.connect();
    return await this.testConnection();
  }

  /**
   * Test Redis cache functionality (set, get, expire operations)
   */
  async validateCacheFunctionality(): Promise<boolean> {
    try {
      await this.connect();
      
      logger.debug('Testing Redis cache functionality', {
        host: this.config.host,
        port: this.config.port
      });

      const testKey = `e2e-test-${Date.now()}`;
      const testValue = 'test-value-' + Math.random().toString(36).substring(7);
      const expireSeconds = 60;

      // Test SET operation
      const setResult = await this.client!.set(testKey, testValue);
      if (setResult !== 'OK') {
        logger.error('Redis SET operation failed', { testKey, setResult });
        return false;
      }

      // Test GET operation
      const getValue = await this.client!.get(testKey);
      if (getValue !== testValue) {
        logger.error('Redis GET operation failed', { 
          testKey, 
          expected: testValue, 
          actual: getValue 
        });
        return false;
      }

      // Test EXPIRE operation
      const expireResult = await this.client!.expire(testKey, expireSeconds);
      if (!expireResult) {
        logger.error('Redis EXPIRE operation failed', { testKey, expireResult });
        return false;
      }

      // Test TTL operation
      const ttl = await this.client!.ttl(testKey);
      if (ttl <= 0 || ttl > expireSeconds) {
        logger.error('Redis TTL operation failed', { testKey, ttl, expectedMax: expireSeconds });
        return false;
      }

      // Test DELETE operation
      const deleteResult = await this.client!.del(testKey);
      if (deleteResult !== 1) {
        logger.error('Redis DELETE operation failed', { testKey, deleteResult });
        return false;
      }

      // Verify key is deleted
      const deletedValue = await this.client!.get(testKey);
      if (deletedValue !== null) {
        logger.error('Redis key not properly deleted', { testKey, deletedValue });
        return false;
      }

      logger.info('Redis cache functionality validation successful', {
        host: this.config.host,
        port: this.config.port,
        operations: ['SET', 'GET', 'EXPIRE', 'TTL', 'DELETE']
      });

      return true;
    } catch (error) {
      logger.error('Redis cache functionality validation error', {
        host: this.config.host,
        port: this.config.port,
        error: error instanceof Error ? error.message : error
      });
      throw error;
    } finally {
      await this.disconnect();
    }
  }

  /**
   * Test Redis connection pooling behavior
   */
  async validateConnectionPooling(poolSize: number = 5): Promise<boolean> {
    const clients: RedisClientType[] = [];
    
    try {
      logger.debug('Testing Redis connection pooling', {
        host: this.config.host,
        port: this.config.port,
        poolSize
      });

      // Create multiple connections
      for (let i = 0; i < poolSize; i++) {
        const client = this.createClient();
        await client.connect();
        clients.push(client);
      }

      // Test concurrent operations
      const promises = clients.map(async (client, index) => {
        const testKey = `pool-test-${index}-${Date.now()}`;
        const testValue = `pool-value-${index}`;
        
        await client.set(testKey, testValue);
        const result = await client.get(testKey);
        await client.del(testKey);
        
        return result === testValue;
      });

      const results = await Promise.all(promises);
      const allSuccessful = results.every(result => result === true);

      if (allSuccessful) {
        logger.info('Redis connection pooling validation successful', {
          host: this.config.host,
          port: this.config.port,
          poolSize,
          successfulConnections: results.length
        });
      } else {
        logger.error('Redis connection pooling validation failed', {
          host: this.config.host,
          port: this.config.port,
          poolSize,
          failedConnections: results.filter(r => !r).length
        });
      }

      return allSuccessful;
    } catch (error) {
      logger.error('Redis connection pooling validation error', {
        host: this.config.host,
        port: this.config.port,
        error: error instanceof Error ? error.message : error
      });
      throw error;
    } finally {
      // Clean up all connections
      await Promise.all(clients.map(async (client) => {
        try {
          await client.quit();
        } catch (error) {
          logger.warn('Error closing Redis connection', {
            error: error instanceof Error ? error.message : error
          });
        }
      }));
    }
  }

  /**
   * Perform comprehensive Redis health check
   */
  async healthCheck(): Promise<{
    status: 'UP' | 'DOWN';
    details: {
      connectivity: boolean;
      responseTime: number;
      version: string | null;
      memory: any;
      keyspace: any;
      error?: string;
    };
  }> {
    const startTime = Date.now();
    let connectivity = false;
    let version: string | null = null;
    let memory: any = null;
    let keyspace: any = null;
    let error: string | undefined;

    try {
      await this.connect();
      
      // Test basic connectivity
      connectivity = await this.testConnection();
      
      if (connectivity) {
        // Get Redis info
        const info = await this.getRedisInfo();
        version = info.version;
        memory = info.memory;
        keyspace = info.keyspace;
      }
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      logger.error('Redis health check failed', {
        host: this.config.host,
        port: this.config.port,
        error
      });
    } finally {
      await this.disconnect();
    }

    const responseTime = Date.now() - startTime;
    const status = connectivity ? 'UP' : 'DOWN';

    const details = {
      connectivity,
      responseTime,
      version,
      memory,
      keyspace,
      ...(error && { error })
    };

    logger.debug('Redis health check completed', {
      host: this.config.host,
      port: this.config.port,
      status,
      details
    });

    return { status, details };
  }

  /**
   * Test cache hit/miss ratio analysis
   */
  async analyzeCachePerformance(testOperations: number = 100): Promise<{
    hitRatio: number;
    missRatio: number;
    averageResponseTime: number;
    operationsPerSecond: number;
  }> {
    try {
      await this.connect();
      
      logger.debug('Analyzing Redis cache performance', {
        host: this.config.host,
        port: this.config.port,
        testOperations
      });

      const testKeys = Array.from({ length: testOperations }, (_, i) => `perf-test-${i}`);
      const testValues = testKeys.map(key => `value-${key}`);
      
      // Pre-populate half of the keys
      const prePopulateCount = Math.floor(testOperations / 2);
      for (let i = 0; i < prePopulateCount; i++) {
        await this.client!.set(testKeys[i], testValues[i]);
      }

      // Measure performance
      const startTime = Date.now();
      let hits = 0;
      let misses = 0;
      const responseTimes: number[] = [];

      for (const key of testKeys) {
        const opStartTime = Date.now();
        const value = await this.client!.get(key);
        const opEndTime = Date.now();
        
        responseTimes.push(opEndTime - opStartTime);
        
        if (value !== null) {
          hits++;
        } else {
          misses++;
        }
      }

      const totalTime = Date.now() - startTime;
      
      // Clean up test keys
      if (testKeys.length > 0) {
        await this.client!.del(testKeys);
      }

      const hitRatio = hits / testOperations;
      const missRatio = misses / testOperations;
      const averageResponseTime = responseTimes.reduce((a, b) => a + b, 0) / responseTimes.length;
      const operationsPerSecond = (testOperations / totalTime) * 1000;

      const performance = {
        hitRatio,
        missRatio,
        averageResponseTime,
        operationsPerSecond
      };

      logger.info('Redis cache performance analysis completed', {
        host: this.config.host,
        port: this.config.port,
        testOperations,
        performance
      });

      return performance;
    } catch (error) {
      logger.error('Redis cache performance analysis error', {
        host: this.config.host,
        port: this.config.port,
        error: error instanceof Error ? error.message : error
      });
      throw error;
    } finally {
      await this.disconnect();
    }
  }

  /**
   * Connect to Redis
   */
  private async connect(): Promise<void> {
    if (this.client && this.client.isOpen) {
      return;
    }

    this.client = this.createClient();
    
    // Add timeout wrapper for connection
    const connectPromise = this.client.connect();
    const timeoutPromise = new Promise((_, reject) => {
      setTimeout(() => {
        reject(new Error(`Redis connection timeout after ${this.config.connectionTimeout}ms`));
      }, this.config.connectionTimeout);
    });

    await Promise.race([connectPromise, timeoutPromise]);
  }

  /**
   * Create a new Redis client
   */
  private createClient(): RedisClientType {
    const clientConfig: any = {
      socket: {
        host: this.config.host,
        port: this.config.port,
        connectTimeout: this.config.connectionTimeout,
        commandTimeout: this.config.connectionTimeout,
        reconnectStrategy: false // Disable automatic reconnection for tests
      }
    };

    if (this.config.password) {
      clientConfig.password = this.config.password;
    }

    const client = createClient(clientConfig) as RedisClientType;
    
    // Add error event handlers to prevent unhandled promise rejections
    client.on('error', (error) => {
      logger.debug('Redis client error event', {
        host: this.config.host,
        port: this.config.port,
        error: error.message
      });
    });

    client.on('connect', () => {
      logger.debug('Redis client connected', {
        host: this.config.host,
        port: this.config.port
      });
    });

    client.on('disconnect', () => {
      logger.debug('Redis client disconnected', {
        host: this.config.host,
        port: this.config.port
      });
    });

    return client;
  }

  /**
   * Disconnect from Redis
   */
  private async disconnect(): Promise<void> {
    if (this.client) {
      try {
        if (this.client.isOpen) {
          // Try graceful disconnect first
          await Promise.race([
            this.client.quit(),
            new Promise((_, reject) => {
              setTimeout(() => reject(new Error('Quit timeout')), 2000);
            })
          ]);
        }
      } catch (error) {
        logger.warn('Error disconnecting from Redis gracefully, forcing disconnect', {
          host: this.config.host,
          port: this.config.port,
          error: error instanceof Error ? error.message : error
        });
        
        try {
          // Force disconnect if graceful quit fails
          await this.client.disconnect();
        } catch (forceError) {
          logger.warn('Error force disconnecting from Redis', {
            host: this.config.host,
            port: this.config.port,
            error: forceError instanceof Error ? forceError.message : forceError
          });
        }
      } finally {
        this.client = null;
      }
    }
  }

  /**
   * Test Redis connection with a simple command
   */
  private async testConnection(): Promise<boolean> {
    if (!this.client || !this.client.isOpen) {
      return false;
    }

    try {
      const result = await this.client.ping();
      return result === 'PONG';
    } catch (error) {
      logger.debug('Redis connection test failed', {
        host: this.config.host,
        port: this.config.port,
        error: error instanceof Error ? error.message : error
      });
      return false;
    }
  }

  /**
   * Get Redis server information
   */
  private async getRedisInfo(): Promise<{
    version: string | null;
    memory: any;
    keyspace: any;
  }> {
    if (!this.client || !this.client.isOpen) {
      return { version: null, memory: null, keyspace: null };
    }

    try {
      const info = await this.client.info();
      const lines = info.split('\r\n');
      
      let version: string | null = null;
      const memory: any = {};
      const keyspace: any = {};

      for (const line of lines) {
        if (line.startsWith('redis_version:')) {
          version = line.split(':')[1];
        } else if (line.startsWith('used_memory:')) {
          memory.used = parseInt(line.split(':')[1]);
        } else if (line.startsWith('used_memory_human:')) {
          memory.used_human = line.split(':')[1];
        } else if (line.startsWith('maxmemory:')) {
          memory.max = parseInt(line.split(':')[1]);
        } else if (line.startsWith('db0:')) {
          const dbInfo = line.split(':')[1];
          const matches = dbInfo.match(/keys=(\d+),expires=(\d+)/);
          if (matches) {
            keyspace.keys = parseInt(matches[1]);
            keyspace.expires = parseInt(matches[2]);
          }
        }
      }

      return { version, memory, keyspace };
    } catch (error) {
      logger.debug('Failed to get Redis info', {
        host: this.config.host,
        port: this.config.port,
        error: error instanceof Error ? error.message : error
      });
      return { version: null, memory: null, keyspace: null };
    }
  }

  /**
   * Utility method for delays
   */
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

/**
 * Factory function to create Redis validator
 */
export class RedisValidatorFactory {
  static createValidator(config: CacheConfiguration['redis']): RedisValidator {
    return new RedisValidator(config);
  }

  /**
   * Perform comprehensive Redis validation
   */
  static async validateRedis(config: CacheConfiguration['redis']): Promise<{
    connectivity: boolean;
    functionality: boolean;
    connectionPooling: boolean;
    health: any;
    performance: any;
  }> {
    const validator = this.createValidator(config);

    logger.info('Starting comprehensive Redis validation');

    try {
      // Test connectivity
      const connectivity = await validator.validateConnectivity();
      
      // Test cache functionality
      const functionality = await validator.validateCacheFunctionality();
      
      // Test connection pooling
      const connectionPooling = await validator.validateConnectionPooling();
      
      // Perform health check
      const health = await validator.healthCheck();
      
      // Analyze performance
      const performance = await validator.analyzeCachePerformance();

      const results = {
        connectivity,
        functionality,
        connectionPooling,
        health,
        performance
      };

      logger.info('Redis validation completed', results);
      return results;
    } catch (error) {
      logger.error('Redis validation failed', {
        error: error instanceof Error ? error.message : error
      });
      throw error;
    }
  }
}