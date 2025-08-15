import { Client } from 'pg';
import { logger } from './logger';
import { DatabaseConnection } from '../types';

/**
 * Database connectivity validator for PostgreSQL databases
 * Provides connection validation, schema validation, and health checks with retry mechanisms
 */
export class DatabaseValidator {
  private config: DatabaseConnection;
  private client: Client | null = null;

  constructor(config: DatabaseConnection) {
    this.config = config;
  }

  /**
   * Validate database connectivity with retry mechanism
   */
  async validateConnectivity(maxRetries: number = 5, retryDelay: number = 2000): Promise<boolean> {
    let lastError: Error | null = null;
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        logger.debug(`Database connectivity attempt ${attempt}/${maxRetries}`, {
          host: this.config.host,
          port: this.config.port,
          database: this.config.database
        });

        await this.connect();
        const isConnected = await this.testConnection();
        
        if (isConnected) {
          logger.infrastructureStatus('database', 'UP', {
            host: this.config.host,
            port: this.config.port,
            database: this.config.database,
            attempt
          });
          return true;
        }
      } catch (error) {
        lastError = error as Error;
        logger.warn(`Database connectivity attempt ${attempt} failed`, {
          host: this.config.host,
          port: this.config.port,
          database: this.config.database,
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

    logger.infrastructureStatus('database', 'DOWN', {
      host: this.config.host,
      port: this.config.port,
      database: this.config.database,
      error: lastError?.message,
      maxRetries
    });

    throw new Error(`Database connectivity failed after ${maxRetries} attempts: ${lastError?.message}`);
  }

  /**
   * Validate database schema and ensure migrations are applied correctly
   */
  async validateSchema(expectedTables: string[]): Promise<boolean> {
    try {
      await this.connect();
      
      logger.debug('Validating database schema', {
        database: this.config.database,
        expectedTables
      });

      // Check if expected tables exist
      const existingTables = await this.getExistingTables();
      const missingTables = expectedTables.filter(table => !existingTables.includes(table));

      if (missingTables.length > 0) {
        logger.error('Database schema validation failed - missing tables', {
          database: this.config.database,
          missingTables,
          existingTables
        });
        return false;
      }

      // Validate table structures (basic check)
      for (const table of expectedTables) {
        const hasValidStructure = await this.validateTableStructure(table);
        if (!hasValidStructure) {
          logger.error('Database schema validation failed - invalid table structure', {
            database: this.config.database,
            table
          });
          return false;
        }
      }

      logger.info('Database schema validation successful', {
        database: this.config.database,
        validatedTables: expectedTables
      });

      return true;
    } catch (error) {
      logger.error('Database schema validation error', {
        database: this.config.database,
        error: error instanceof Error ? error.message : error
      });
      throw error;
    } finally {
      await this.disconnect();
    }
  }

  /**
   * Perform database health check with comprehensive validation
   */
  async healthCheck(): Promise<{
    status: 'UP' | 'DOWN';
    details: {
      connectivity: boolean;
      responseTime: number;
      version: string | null;
      activeConnections: number | null;
      error?: string;
    };
  }> {
    const startTime = Date.now();
    let connectivity = false;
    let version: string | null = null;
    let activeConnections: number | null = null;
    let error: string | undefined;

    try {
      await this.connect();
      
      // Test basic connectivity
      connectivity = await this.testConnection();
      
      if (connectivity) {
        // Get database version
        version = await this.getDatabaseVersion();
        
        // Get active connections count
        activeConnections = await this.getActiveConnectionsCount();
      }
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      logger.error('Database health check failed', {
        database: this.config.database,
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
      activeConnections,
      ...(error && { error })
    };

    logger.debug('Database health check completed', {
      database: this.config.database,
      status,
      details
    });

    return { status, details };
  }

  /**
   * Connect to the database
   */
  private async connect(): Promise<void> {
    if (this.client) {
      return;
    }

    this.client = new Client({
      host: this.config.host,
      port: this.config.port,
      database: this.config.database,
      user: this.config.username,
      password: this.config.password,
      connectionTimeoutMillis: this.config.connectionTimeout,
      query_timeout: this.config.connectionTimeout,
      statement_timeout: this.config.connectionTimeout
    });

    await this.client.connect();
  }

  /**
   * Disconnect from the database
   */
  private async disconnect(): Promise<void> {
    if (this.client) {
      try {
        await this.client.end();
      } catch (error) {
        logger.warn('Error disconnecting from database', {
          database: this.config.database,
          error: error instanceof Error ? error.message : error
        });
      } finally {
        this.client = null;
      }
    }
  }

  /**
   * Test database connection with a simple query
   */
  private async testConnection(): Promise<boolean> {
    if (!this.client) {
      return false;
    }

    try {
      const result = await this.client.query('SELECT 1 as test');
      return result.rows.length > 0 && result.rows[0].test === 1;
    } catch (error) {
      logger.debug('Database connection test failed', {
        database: this.config.database,
        error: error instanceof Error ? error.message : error
      });
      return false;
    }
  }

  /**
   * Get list of existing tables in the database
   */
  private async getExistingTables(): Promise<string[]> {
    if (!this.client) {
      throw new Error('Database client not connected');
    }

    const result = await this.client.query(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public' 
      AND table_type = 'BASE TABLE'
    `);

    return result.rows.map(row => row.table_name);
  }

  /**
   * Validate basic table structure (check if table has columns)
   */
  private async validateTableStructure(tableName: string): Promise<boolean> {
    if (!this.client) {
      throw new Error('Database client not connected');
    }

    try {
      const result = await this.client.query(`
        SELECT column_name, data_type, is_nullable
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = $1
      `, [tableName]);

      // Table should have at least one column
      return result.rows.length > 0;
    } catch (error) {
      logger.debug('Table structure validation failed', {
        table: tableName,
        error: error instanceof Error ? error.message : error
      });
      return false;
    }
  }

  /**
   * Get database version
   */
  private async getDatabaseVersion(): Promise<string | null> {
    if (!this.client) {
      return null;
    }

    try {
      const result = await this.client.query('SELECT version()');
      return result.rows[0]?.version || null;
    } catch (error) {
      logger.debug('Failed to get database version', {
        database: this.config.database,
        error: error instanceof Error ? error.message : error
      });
      return null;
    }
  }

  /**
   * Get active connections count
   */
  private async getActiveConnectionsCount(): Promise<number | null> {
    if (!this.client) {
      return null;
    }

    try {
      const result = await this.client.query(`
        SELECT count(*) as active_connections 
        FROM pg_stat_activity 
        WHERE state = 'active'
      `);
      return parseInt(result.rows[0]?.active_connections) || 0;
    } catch (error) {
      logger.debug('Failed to get active connections count', {
        database: this.config.database,
        error: error instanceof Error ? error.message : error
      });
      return null;
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
 * Factory function to create database validators for different databases
 */
export class DatabaseValidatorFactory {
  static createAccountDbValidator(config: DatabaseConnection): DatabaseValidator {
    return new DatabaseValidator(config);
  }

  static createTransactionDbValidator(config: DatabaseConnection): DatabaseValidator {
    return new DatabaseValidator(config);
  }

  /**
   * Validate both account and transaction databases
   */
  static async validateAllDatabases(
    accountDbConfig: DatabaseConnection,
    transactionDbConfig: DatabaseConnection,
    expectedAccountTables: string[] = ['users', 'accounts'],
    expectedTransactionTables: string[] = ['transactions', 'transaction_limits']
  ): Promise<{
    accountDb: { connectivity: boolean; schema: boolean; health: any };
    transactionDb: { connectivity: boolean; schema: boolean; health: any };
  }> {
    const accountValidator = this.createAccountDbValidator(accountDbConfig);
    const transactionValidator = this.createTransactionDbValidator(transactionDbConfig);

    logger.info('Starting comprehensive database validation');

    // Validate account database
    const accountConnectivity = await accountValidator.validateConnectivity();
    const accountSchema = await accountValidator.validateSchema(expectedAccountTables);
    const accountHealth = await accountValidator.healthCheck();

    // Validate transaction database
    const transactionConnectivity = await transactionValidator.validateConnectivity();
    const transactionSchema = await transactionValidator.validateSchema(expectedTransactionTables);
    const transactionHealth = await transactionValidator.healthCheck();

    const results = {
      accountDb: {
        connectivity: accountConnectivity,
        schema: accountSchema,
        health: accountHealth
      },
      transactionDb: {
        connectivity: transactionConnectivity,
        schema: transactionSchema,
        health: transactionHealth
      }
    };

    logger.info('Database validation completed', results);
    return results;
  }
}