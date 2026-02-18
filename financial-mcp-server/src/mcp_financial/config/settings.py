"""
Configuration settings for the MCP Financial Server.
"""

import os
from typing import Optional
from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings with environment variable support."""
    
    # Server Configuration
    host: str = Field(default="localhost", env="HOST")
    port: int = Field(default=8082, env="PORT")
    debug: bool = Field(default=False, env="DEBUG")
    
    # Service URLs
    account_service_url: str = Field(
        default="http://localhost:8080", 
        env="ACCOUNT_SERVICE_URL"
    )
    transaction_service_url: str = Field(
        default="http://localhost:8081", 
        env="TRANSACTION_SERVICE_URL"
    )
    
    # Authentication
    jwt_secret: str = Field(
        default="your-secret-key", 
        env="JWT_SECRET"
    )
    
    # HTTP Client Configuration
    http_timeout: int = Field(default=5000, env="HTTP_TIMEOUT")  # milliseconds
    max_retries: int = Field(default=3, env="MAX_RETRIES")
    retry_delay: float = Field(default=1.0, env="RETRY_DELAY")  # seconds
    
    # Circuit Breaker Configuration
    circuit_breaker_failure_threshold: int = Field(
        default=5, 
        env="CIRCUIT_BREAKER_FAILURE_THRESHOLD"
    )
    circuit_breaker_recovery_timeout: int = Field(
        default=30, 
        env="CIRCUIT_BREAKER_RECOVERY_TIMEOUT"
    )
    
    # Logging Configuration
    log_level: str = Field(default="INFO", env="LOG_LEVEL")
    log_format: str = Field(default="json", env="LOG_FORMAT")  # json or text
    
    # Monitoring Configuration
    metrics_enabled: bool = Field(default=True, env="METRICS_ENABLED")
    metrics_port: int = Field(default=9090, env="METRICS_PORT")
    health_check_enabled: bool = Field(default=True, env="HEALTH_CHECK_ENABLED")
    
    # Rate Limiting
    rate_limit_enabled: bool = Field(default=True, env="RATE_LIMIT_ENABLED")
    rate_limit_requests: int = Field(default=100, env="RATE_LIMIT_REQUESTS")
    rate_limit_window: int = Field(default=60, env="RATE_LIMIT_WINDOW")  # seconds
    
    # Database Configuration (if needed for caching/sessions)
    redis_url: Optional[str] = Field(default=None, env="REDIS_URL")
    
    # Alerting Configuration
    alert_webhook_url: Optional[str] = Field(default=None, env="ALERT_WEBHOOK_URL")
    slack_webhook_url: Optional[str] = Field(default=None, env="SLACK_WEBHOOK_URL")
    slack_channel: str = Field(default="#alerts", env="SLACK_CHANNEL")
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


def get_settings() -> Settings:
    """Get application settings instance."""
    return Settings()