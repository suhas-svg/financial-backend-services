package com.suhasan.finance.transaction_service.health;

/**
 * Interface for health indicators
 */
public interface HealthIndicator {
    
    /**
     * Return an indication of health
     * @return the health status
     */
    Health health();
}