#!/usr/bin/env python3
"""
Configuration Validation Script for MCP Financial Server
Validates environment configuration before deployment
"""

import os
import sys
import json
import argparse
import re
from typing import Dict, List, Any, Optional
from urllib.parse import urlparse
import logging

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

class ConfigValidator:
    """Configuration validator for MCP Financial Server"""
    
    def __init__(self, environment: str):
        self.environment = environment
        self.errors: List[str] = []
        self.warnings: List[str] = []
        
    def validate_required_vars(self, required_vars: Dict[str, str]) -> bool:
        """Validate required environment variables"""
        logger.info("Validating required environment variables...")
        
        missing_vars = []
        for var_name, description in required_vars.items():
            value = os.getenv(var_name)
            if not value:
                missing_vars.append(f"{var_name} ({description})")
            elif value.strip() == "":
                missing_vars.append(f"{var_name} (empty value)")
        
        if missing_vars:
            self.errors.append(f"Missing required environment variables: {', '.join(missing_vars)}")
            return False
        
        logger.info("✓ All required environment variables are set")
        return True
    
    def validate_urls(self, url_vars: Dict[str, str]) -> bool:
        """Validate URL format for service endpoints"""
        logger.info("Validating service URLs...")
        
        invalid_urls = []
        for var_name, description in url_vars.items():
            url = os.getenv(var_name)
            if url:
                try:
                    parsed = urlparse(url)
                    if not parsed.scheme or not parsed.netloc:
                        invalid_urls.append(f"{var_name}: {url} (invalid format)")
                    elif parsed.scheme not in ['http', 'https']:
                        invalid_urls.append(f"{var_name}: {url} (unsupported scheme)")
                except Exception as e:
                    invalid_urls.append(f"{var_name}: {url} (parse error: {e})")
        
        if invalid_urls:
            self.errors.extend(invalid_urls)
            return False
        
        logger.info("✓ All URLs are valid")
        return True
    
    def validate_ports(self, port_vars: Dict[str, str]) -> bool:
        """Validate port numbers"""
        logger.info("Validating port numbers...")
        
        invalid_ports = []
        for var_name, description in port_vars.items():
            port_str = os.getenv(var_name)
            if port_str:
                try:
                    port = int(port_str)
                    if port < 1 or port > 65535:
                        invalid_ports.append(f"{var_name}: {port} (out of range 1-65535)")
                except ValueError:
                    invalid_ports.append(f"{var_name}: {port_str} (not a number)")
        
        if invalid_ports:
            self.errors.extend(invalid_ports)
            return False
        
        logger.info("✓ All ports are valid")
        return True
    
    def validate_jwt_secret(self) -> bool:
        """Validate JWT secret strength"""
        logger.info("Validating JWT secret...")
        
        jwt_secret = os.getenv('JWT_SECRET')
        if not jwt_secret:
            self.errors.append("JWT_SECRET is required")
            return False
        
        # Check minimum length
        if len(jwt_secret) < 32:
            self.errors.append("JWT_SECRET must be at least 32 characters long")
            return False
        
        # Check for default/weak secrets
        weak_secrets = [
            'your-secret-key',
            'secret',
            'password',
            '123456',
            'default'
        ]
        
        if jwt_secret.lower() in weak_secrets:
            self.errors.append("JWT_SECRET appears to be a default/weak value")
            return False
        
        # Production-specific checks
        if self.environment == 'production':
            # Check for base64 encoding (common pattern)
            if not re.match(r'^[A-Za-z0-9+/]+=*$', jwt_secret):
                self.warnings.append("JWT_SECRET should be base64 encoded in production")
            
            # Check entropy (basic check)
            unique_chars = len(set(jwt_secret))
            if unique_chars < 16:
                self.warnings.append("JWT_SECRET has low entropy (consider using more diverse characters)")
        
        logger.info("✓ JWT secret validation passed")
        return True
    
    def validate_log_level(self) -> bool:
        """Validate log level setting"""
        logger.info("Validating log level...")
        
        log_level = os.getenv('LOG_LEVEL', 'INFO')
        valid_levels = ['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL']
        
        if log_level.upper() not in valid_levels:
            self.errors.append(f"LOG_LEVEL must be one of: {', '.join(valid_levels)}")
            return False
        
        # Environment-specific recommendations
        if self.environment == 'production' and log_level.upper() == 'DEBUG':
            self.warnings.append("DEBUG log level not recommended for production")
        
        logger.info("✓ Log level is valid")
        return True
    
    def validate_numeric_configs(self, numeric_vars: Dict[str, Dict[str, Any]]) -> bool:
        """Validate numeric configuration values"""
        logger.info("Validating numeric configurations...")
        
        invalid_configs = []
        for var_name, config in numeric_vars.items():
            value_str = os.getenv(var_name)
            if value_str:
                try:
                    value = float(value_str)
                    min_val = config.get('min')
                    max_val = config.get('max')
                    
                    if min_val is not None and value < min_val:
                        invalid_configs.append(f"{var_name}: {value} (below minimum {min_val})")
                    elif max_val is not None and value > max_val:
                        invalid_configs.append(f"{var_name}: {value} (above maximum {max_val})")
                        
                except ValueError:
                    invalid_configs.append(f"{var_name}: {value_str} (not a number)")
        
        if invalid_configs:
            self.errors.extend(invalid_configs)
            return False
        
        logger.info("✓ All numeric configurations are valid")
        return True
    
    def validate_boolean_configs(self, boolean_vars: List[str]) -> bool:
        """Validate boolean configuration values"""
        logger.info("Validating boolean configurations...")
        
        invalid_booleans = []
        valid_boolean_values = ['true', 'false', '1', '0', 'yes', 'no', 'on', 'off']
        
        for var_name in boolean_vars:
            value = os.getenv(var_name)
            if value and value.lower() not in valid_boolean_values:
                invalid_booleans.append(f"{var_name}: {value} (must be true/false, 1/0, yes/no, or on/off)")
        
        if invalid_booleans:
            self.errors.extend(invalid_booleans)
            return False
        
        logger.info("✓ All boolean configurations are valid")
        return True
    
    def validate_environment_specific(self) -> bool:
        """Validate environment-specific requirements"""
        logger.info(f"Validating {self.environment}-specific requirements...")
        
        if self.environment == 'production':
            return self._validate_production_config()
        elif self.environment == 'staging':
            return self._validate_staging_config()
        else:
            return self._validate_development_config()
    
    def _validate_production_config(self) -> bool:
        """Production-specific validation"""
        valid = True
        
        # Debug should be disabled
        if os.getenv('DEBUG', 'false').lower() == 'true':
            self.errors.append("DEBUG must be disabled in production")
            valid = False
        
        # CORS should be restricted
        cors_origins = os.getenv('CORS_ORIGINS', '')
        if cors_origins == '*':
            self.errors.append("CORS_ORIGINS should not be '*' in production")
            valid = False
        
        # Rate limiting should be enabled
        if os.getenv('RATE_LIMIT_ENABLED', 'true').lower() != 'true':
            self.warnings.append("Rate limiting should be enabled in production")
        
        # Metrics should be enabled
        if os.getenv('METRICS_ENABLED', 'true').lower() != 'true':
            self.warnings.append("Metrics should be enabled in production")
        
        # Check for production-required secrets
        prod_secrets = ['DB_PASSWORD', 'REDIS_URL']
        for secret in prod_secrets:
            if not os.getenv(secret):
                self.errors.append(f"{secret} is required in production")
                valid = False
        
        return valid
    
    def _validate_staging_config(self) -> bool:
        """Staging-specific validation"""
        # Similar to production but more lenient
        return True
    
    def _validate_development_config(self) -> bool:
        """Development-specific validation"""
        # Most permissive, mainly check for obvious issues
        return True
    
    def validate_all(self) -> bool:
        """Run all validation checks"""
        logger.info(f"Starting configuration validation for {self.environment} environment")
        logger.info("=" * 60)
        
        all_valid = True
        
        # Define validation rules
        required_vars = {
            'HOST': 'Server host address',
            'PORT': 'Server port number',
            'JWT_SECRET': 'JWT signing secret'
        }
        
        url_vars = {
            'ACCOUNT_SERVICE_URL': 'Account service endpoint',
            'TRANSACTION_SERVICE_URL': 'Transaction service endpoint'
        }
        
        port_vars = {
            'PORT': 'MCP server port',
            'METRICS_PORT': 'Metrics server port'
        }
        
        numeric_vars = {
            'HTTP_TIMEOUT': {'min': 1000, 'max': 60000},
            'MAX_RETRIES': {'min': 1, 'max': 10},
            'CIRCUIT_BREAKER_FAILURE_THRESHOLD': {'min': 1, 'max': 20},
            'CIRCUIT_BREAKER_RECOVERY_TIMEOUT': {'min': 5, 'max': 300},
            'RATE_LIMIT_REQUESTS': {'min': 1, 'max': 10000},
            'RATE_LIMIT_WINDOW': {'min': 1, 'max': 3600}
        }
        
        boolean_vars = [
            'DEBUG', 'METRICS_ENABLED', 'HEALTH_CHECK_ENABLED',
            'RATE_LIMIT_ENABLED', 'RELOAD_ON_CHANGE', 'CORS_ENABLED'
        ]
        
        # Run validation checks
        checks = [
            self.validate_required_vars(required_vars),
            self.validate_urls(url_vars),
            self.validate_ports(port_vars),
            self.validate_jwt_secret(),
            self.validate_log_level(),
            self.validate_numeric_configs(numeric_vars),
            self.validate_boolean_configs(boolean_vars),
            self.validate_environment_specific()
        ]
        
        all_valid = all(checks)
        
        # Print summary
        logger.info("=" * 60)
        logger.info("Validation Summary:")
        
        if self.errors:
            logger.error(f"❌ {len(self.errors)} error(s) found:")
            for error in self.errors:
                logger.error(f"  • {error}")
        
        if self.warnings:
            logger.warning(f"⚠️  {len(self.warnings)} warning(s):")
            for warning in self.warnings:
                logger.warning(f"  • {warning}")
        
        if all_valid and not self.errors:
            logger.info("✅ Configuration validation passed!")
        else:
            logger.error("❌ Configuration validation failed!")
        
        return all_valid and not self.errors

def main():
    """Main function"""
    parser = argparse.ArgumentParser(description='Validate MCP Financial Server configuration')
    parser.add_argument('--env', '--environment', 
                       choices=['development', 'staging', 'production'],
                       default='development',
                       help='Environment to validate (default: development)')
    parser.add_argument('--env-file', 
                       help='Path to environment file to load')
    parser.add_argument('--output-format',
                       choices=['text', 'json'],
                       default='text',
                       help='Output format (default: text)')
    
    args = parser.parse_args()
    
    # Load environment file if specified
    if args.env_file:
        try:
            with open(args.env_file, 'r') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#') and '=' in line:
                        key, value = line.split('=', 1)
                        os.environ[key] = value
            logger.info(f"Loaded environment from: {args.env_file}")
        except Exception as e:
            logger.error(f"Failed to load environment file: {e}")
            sys.exit(1)
    
    # Run validation
    validator = ConfigValidator(args.env)
    is_valid = validator.validate_all()
    
    # Output results
    if args.output_format == 'json':
        result = {
            'environment': args.env,
            'valid': is_valid,
            'errors': validator.errors,
            'warnings': validator.warnings
        }
        print(json.dumps(result, indent=2))
    
    # Exit with appropriate code
    sys.exit(0 if is_valid else 1)

if __name__ == '__main__':
    main()