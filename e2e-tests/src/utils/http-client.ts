import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import { ApiResponse } from '../types';
import { testConfig } from '../config/test-config';

// Extend Axios types to include metadata and duration
declare module 'axios' {
  interface AxiosRequestConfig {
    metadata?: {
      startTime: number;
    };
  }
  
  interface AxiosResponse {
    duration?: number;
  }
}

/**
 * HTTP client utility for API testing
 * Provides consistent request handling, timing, and error management
 */
export class HttpClient {
  private client: AxiosInstance;
  private baseURL: string;

  constructor(baseURL: string) {
    this.baseURL = baseURL;
    this.client = axios.create({
      baseURL,
      timeout: testConfig.getTimeoutConfig().httpTimeout,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      }
    });

    // Request interceptor for timing
    this.client.interceptors.request.use((config) => {
      config.metadata = { startTime: Date.now() };
      return config;
    });

    // Response interceptor for timing and error handling
    this.client.interceptors.response.use(
      (response) => {
        const endTime = Date.now();
        const startTime = response.config.metadata?.startTime || endTime;
        response.duration = endTime - startTime;
        return response;
      },
      (error) => {
        const endTime = Date.now();
        const startTime = error.config?.metadata?.startTime || endTime;
        error.duration = endTime - startTime;
        return Promise.reject(error);
      }
    );
  }

  /**
   * Set authorization header for authenticated requests
   */
  setAuthToken(token: string): void {
    this.client.defaults.headers.common['Authorization'] = `Bearer ${token}`;
  }

  /**
   * Clear authorization header
   */
  clearAuthToken(): void {
    delete this.client.defaults.headers.common['Authorization'];
  }

  /**
   * GET request with timing and error handling
   */
  async get<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    try {
      const response = await this.client.get<T>(url, config);
      return this.formatResponse(response);
    } catch (error) {
      throw this.formatError(error);
    }
  }

  /**
   * POST request with timing and error handling
   */
  async post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    try {
      const response = await this.client.post<T>(url, data, config);
      return this.formatResponse(response);
    } catch (error) {
      throw this.formatError(error);
    }
  }

  /**
   * PUT request with timing and error handling
   */
  async put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    try {
      const response = await this.client.put<T>(url, data, config);
      return this.formatResponse(response);
    } catch (error) {
      throw this.formatError(error);
    }
  }

  /**
   * DELETE request with timing and error handling
   */
  async delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    try {
      const response = await this.client.delete<T>(url, config);
      return this.formatResponse(response);
    } catch (error) {
      throw this.formatError(error);
    }
  }

  /**
   * PATCH request with timing and error handling
   */
  async patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    try {
      const response = await this.client.patch<T>(url, data, config);
      return this.formatResponse(response);
    } catch (error) {
      throw this.formatError(error);
    }
  }

  /**
   * Format successful response
   */
  private formatResponse<T>(response: AxiosResponse<T>): ApiResponse<T> {
    return {
      data: response.data,
      status: response.status,
      statusText: response.statusText,
      headers: response.headers as Record<string, string>,
      duration: (response as any).duration || 0
    };
  }

  /**
   * Format error response
   */
  private formatError(error: any): ApiResponse {
    if (error.response) {
      // Server responded with error status
      return {
        data: error.response.data,
        status: error.response.status,
        statusText: error.response.statusText,
        headers: error.response.headers as Record<string, string>,
        duration: error.duration || 0
      };
    } else if (error.request) {
      // Request was made but no response received
      throw new Error(`Network error: ${error.message}`);
    } else {
      // Something else happened
      throw new Error(`Request error: ${error.message}`);
    }
  }

  /**
   * Health check utility
   */
  async healthCheck(endpoint: string = '/actuator/health'): Promise<boolean> {
    try {
      const response = await this.get(endpoint);
      return response.status === 200;
    } catch (error) {
      return false;
    }
  }

  /**
   * Wait for service to be ready
   */
  async waitForService(
    endpoint: string = '/actuator/health',
    maxAttempts: number = 30,
    delayMs: number = 2000
  ): Promise<boolean> {
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        const isHealthy = await this.healthCheck(endpoint);
        if (isHealthy) {
          console.log(`✅ Service at ${this.baseURL} is ready (attempt ${attempt})`);
          return true;
        }
      } catch (error) {
        // Service not ready yet
      }

      if (attempt < maxAttempts) {
        console.log(`⏳ Waiting for service at ${this.baseURL} (attempt ${attempt}/${maxAttempts})`);
        await this.delay(delayMs);
      }
    }

    console.error(`❌ Service at ${this.baseURL} failed to become ready after ${maxAttempts} attempts`);
    return false;
  }

  /**
   * Utility delay function
   */
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Get base URL
   */
  getBaseURL(): string {
    return this.baseURL;
  }
}

// Factory functions for service clients
export function createAccountServiceClient(): HttpClient {
  const config = testConfig.getServiceConfig();
  return new HttpClient(config.accountService.url);
}

export function createTransactionServiceClient(): HttpClient {
  const config = testConfig.getServiceConfig();
  return new HttpClient(config.transactionService.url);
}