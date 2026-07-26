"""
Configuration settings for the MCP Financial Server.
"""

from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings with environment variable support."""

    # Server Configuration
    host: str = "localhost"
    port: int = 8082
    debug: bool = False

    # Service URLs
    account_service_url: str = "http://localhost:8080"
    transaction_service_url: str = "http://localhost:8081"

    # Authentication
    jwt_secret: str = "your-secret-key"

    # HTTP Client Configuration
    http_timeout: int = 5000  # milliseconds
    max_retries: int = 3
    retry_delay: float = 1.0  # seconds

    # Circuit Breaker Configuration
    circuit_breaker_failure_threshold: int = 5
    circuit_breaker_recovery_timeout: int = 30

    # Logging Configuration
    log_level: str = "INFO"
    log_format: str = "json"  # json or text

    # Monitoring Configuration
    metrics_enabled: bool = True
    metrics_port: int = 9090
    health_check_enabled: bool = True

    # Rate Limiting
    rate_limit_enabled: bool = True
    rate_limit_requests: int = 100
    rate_limit_window: int = 60  # seconds

    # Database Configuration (if needed for caching/sessions)
    redis_url: Optional[str] = None

    # Alerting Configuration
    alert_webhook_url: Optional[str] = None
    slack_webhook_url: Optional[str] = None
    slack_channel: str = "#alerts"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


def get_settings() -> Settings:
    """Get application settings instance."""
    return Settings()