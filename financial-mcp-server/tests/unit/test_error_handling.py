"""
Unit tests for error handling and validation components.
"""

import pytest
import uuid
from decimal import Decimal
from datetime import datetime
from unittest.mock import Mock, patch

from src.mcp_financial.exceptions.base import (
    MCPFinancialError,
    ValidationError,
    AuthenticationError,
    AuthorizationError,
    ServiceError,
    CircuitBreakerError,
    RateLimitError,
    BusinessRuleError,
    DataIntegrityError,
    TimeoutError
)

from src.mcp_financial.exceptions.handlers import (
    ErrorHandler,
    ErrorContext,
    ErrorCategory,
    ErrorSeverity,
    ValidationErrorCollector,
    error_handling_context,
    create_error_response,
    safe_execute,
    CircuitBreakerManager
)

from src.mcp_financial.utils.validation import (
    EnhancedValidator,
    BusinessRuleValidator,
    SecurityValidator,
    ComprehensiveValidator
)


class TestMCPFinancialError:
    """Test base exception class."""
    
    def test_basic_error_creation(self):
        """Test basic error creation."""
        error = MCPFinancialError("Test error", "TEST_ERROR")
        
        assert error.message == "Test error"
        assert error.error_code == "TEST_ERROR"
        assert error.details == {}
        assert error.request_id is None
        assert isinstance(error.timestamp, datetime)
        
    def test_error_with_details(self):
        """Test error creation with details."""
        details = {"field": "test", "value": 123}
        request_id = str(uuid.uuid4())
        
        error = MCPFinancialError(
            "Test error with details",
            "TEST_ERROR_DETAILED",
            details,
            request_id
        )
        
        assert error.details == details
        assert error.request_id == request_id
        
    def test_error_to_dict(self):
        """Test error serialization to dictionary."""
        error = MCPFinancialError("Test error", "TEST_ERROR")
        error_dict = error.to_dict()
        
        assert error_dict["error_code"] == "TEST_ERROR"
        assert error_dict["error_message"] == "Test error"
        assert "timestamp" in error_dict
        assert error_dict["details"] == {}


class TestValidationError:
    """Test validation error class."""
    
    def test_validation_error_creation(self):
        """Test validation error creation."""
        error = ValidationError("Invalid field", field="test_field", value="invalid")
        
        assert error.error_code == "VALIDATION_ERROR"
        assert error.field == "test_field"
        assert error.value == "invalid"
        assert error.details["field"] == "test_field"
        assert error.details["invalid_value"] == "invalid"
        
    def test_validation_error_without_field(self):
        """Test validation error without field specification."""
        error = ValidationError("General validation error")
        
        assert error.field is None
        assert error.value is None
        assert "field" not in error.details


class TestErrorHandler:
    """Test error handler functionality."""
    
    def test_error_handler_initialization(self):
        """Test error handler initialization."""
        handler = ErrorHandler()
        
        assert ValidationError in handler.error_mappings
        assert handler.error_mappings[ValidationError] == ErrorCategory.VALIDATION
        
    def test_handle_validation_error(self):
        """Test handling validation error."""
        handler = ErrorHandler()
        context = ErrorContext("test_operation", "user123")
        error = ValidationError("Test validation error")
        
        response = handler.handle_error(error, context)
        
        assert response.error_code == "VALIDATION_ERROR"
        assert response.error_message == "Test validation error"
        assert response.request_id == context.request_id
        assert response.details["operation"] == "test_operation"
        assert response.details["category"] == "validation"
        
    def test_handle_generic_error(self):
        """Test handling generic exception."""
        handler = ErrorHandler()
        context = ErrorContext("test_operation")
        error = ValueError("Generic error")
        
        response = handler.handle_error(error, context)
        
        assert response.error_code == "INTERNAL_ERROR"
        assert response.error_message == "Generic error"
        assert response.details["category"] == "internal"
        
    def test_error_severity_determination(self):
        """Test error severity determination."""
        handler = ErrorHandler()
        
        # Test different error types
        validation_error = ValidationError("Test")
        auth_error = AuthenticationError("Test")
        service_error = ServiceError("Test")
        generic_error = ValueError("Test")
        
        assert handler._get_error_severity(validation_error, ErrorCategory.VALIDATION) == ErrorSeverity.LOW
        assert handler._get_error_severity(auth_error, ErrorCategory.AUTHENTICATION) == ErrorSeverity.LOW
        assert handler._get_error_severity(service_error, ErrorCategory.SERVICE) == ErrorSeverity.MEDIUM
        assert handler._get_error_severity(generic_error, ErrorCategory.INTERNAL) == ErrorSeverity.CRITICAL


class TestValidationErrorCollector:
    """Test validation error collector."""
    
    def test_empty_collector(self):
        """Test empty collector."""
        collector = ValidationErrorCollector()
        
        assert not collector.has_errors()
        assert collector.get_errors() == []
        
    def test_add_errors(self):
        """Test adding errors to collector."""
        collector = ValidationErrorCollector()
        
        collector.add_error("field1", "Error 1")
        collector.add_error("field2", "Error 2", "invalid_value")
        
        assert collector.has_errors()
        errors = collector.get_errors()
        assert len(errors) == 2
        assert errors[0]["field"] == "field1"
        assert errors[1]["invalid_value"] == "invalid_value"
        
    def test_raise_if_errors(self):
        """Test raising validation error if errors exist."""
        collector = ValidationErrorCollector()
        collector.add_error("field1", "Error 1")
        
        with pytest.raises(ValidationError) as exc_info:
            collector.raise_if_errors("test_request_id")
            
        error = exc_info.value
        assert "1 error(s)" in error.message
        assert error.request_id == "test_request_id"
        assert len(error.details["validation_errors"]) == 1


class TestErrorHandlingContext:
    """Test error handling context manager."""
    
    def test_successful_context(self):
        """Test successful operation in context."""
        with error_handling_context("test_operation") as ctx:
            assert ctx.operation == "test_operation"
            assert ctx.request_id is not None
            
    def test_error_in_context(self):
        """Test error handling in context."""
        with pytest.raises(MCPFinancialError) as exc_info:
            with error_handling_context("test_operation") as ctx:
                raise ValueError("Test error")
                
        error = exc_info.value
        assert error.error_code == "INTERNAL_ERROR"
        assert "Test error" in error.message


class TestEnhancedValidator:
    """Test enhanced validation utilities."""
    
    def test_validate_mcp_tool_input_success(self):
        """Test successful MCP tool input validation."""
        params = {
            "name": "test",
            "amount": 100.50,
            "active": True
        }
        
        schema = {
            "required": ["name", "amount"],
            "properties": {
                "name": {"type": "string", "minLength": 1, "maxLength": 50},
                "amount": {"type": "number", "minimum": 0, "maximum": 1000000},
                "active": {"type": "boolean"}
            }
        }
        
        result = EnhancedValidator.validate_mcp_tool_input(params, schema)
        
        assert result["name"] == "test"
        assert result["amount"] == Decimal("100.50")
        assert result["active"] is True
        
    def test_validate_mcp_tool_input_missing_required(self):
        """Test validation with missing required parameters."""
        params = {"name": "test"}
        
        schema = {
            "required": ["name", "amount"],
            "properties": {
                "name": {"type": "string"},
                "amount": {"type": "number"}
            }
        }
        
        with pytest.raises(ValidationError) as exc_info:
            EnhancedValidator.validate_mcp_tool_input(params, schema)
            
        error = exc_info.value
        assert "validation_errors" in error.details
        
    def test_validate_string_parameter(self):
        """Test string parameter validation."""
        schema = {
            "type": "string",
            "minLength": 2,
            "maxLength": 10,
            "pattern": r"^[a-zA-Z]+$"
        }
        
        # Valid string
        result = EnhancedValidator._validate_string_parameter("test", "hello", schema)
        assert result == "hello"
        
        # Invalid pattern
        with pytest.raises(ValidationError):
            EnhancedValidator._validate_string_parameter("test", "hello123", schema)
            
        # Too short
        with pytest.raises(ValidationError):
            EnhancedValidator._validate_string_parameter("test", "a", schema)
            
    def test_validate_number_parameter(self):
        """Test number parameter validation."""
        schema = {
            "type": "number",
            "minimum": 0,
            "maximum": 1000,
            "decimalPlaces": 2
        }
        
        # Valid number
        result = EnhancedValidator._validate_number_parameter("amount", 100.50, schema)
        assert result == Decimal("100.50")
        
        # Too many decimal places
        with pytest.raises(ValidationError):
            EnhancedValidator._validate_number_parameter("amount", 100.123, schema)
            
        # Out of range
        with pytest.raises(ValidationError):
            EnhancedValidator._validate_number_parameter("amount", 1500, schema)


class TestBusinessRuleValidator:
    """Test business rule validation."""
    
    def test_validate_account_creation_rules_success(self):
        """Test successful account creation validation."""
        # Should not raise any exception
        BusinessRuleValidator.validate_account_creation_rules(
            "user123",
            "CHECKING",
            Decimal("100.00"),
            Mock()
        )
        
    def test_validate_account_creation_negative_balance(self):
        """Test account creation with negative balance for non-credit account."""
        with pytest.raises(ValidationError) as exc_info:
            BusinessRuleValidator.validate_account_creation_rules(
                "user123",
                "CHECKING",
                Decimal("-100.00"),
                Mock()
            )
            
        error = exc_info.value
        assert "negative" in error.message.lower()
        
    def test_validate_investment_account_minimum(self):
        """Test investment account minimum balance requirement."""
        with pytest.raises(ValidationError) as exc_info:
            BusinessRuleValidator.validate_account_creation_rules(
                "user123",
                "INVESTMENT",
                Decimal("500.00"),
                Mock()
            )
            
        error = exc_info.value
        assert "minimum balance" in error.message.lower()
        
    def test_validate_transaction_rules_insufficient_funds(self):
        """Test transaction validation with insufficient funds."""
        with pytest.raises(ValidationError) as exc_info:
            BusinessRuleValidator.validate_transaction_rules(
                "WITHDRAWAL",
                Decimal("1000.00"),
                account_balance=Decimal("500.00")
            )
            
        error = exc_info.value
        assert "exceeds available balance" in error.message


class TestSecurityValidator:
    """Test security validation."""
    
    def test_validate_input_security_clean_input(self):
        """Test security validation with clean input."""
        clean_input = "This is a clean input string"
        result = SecurityValidator.validate_input_security(clean_input, "test_field")
        assert result == clean_input
        
    def test_validate_input_security_sql_injection(self):
        """Test security validation with SQL injection attempt."""
        malicious_input = "'; DROP TABLE users; --"
        
        with pytest.raises(ValidationError) as exc_info:
            SecurityValidator.validate_input_security(malicious_input, "test_field")
            
        error = exc_info.value
        assert "Invalid characters detected" in error.message
        
    def test_validate_input_security_xss_attempt(self):
        """Test security validation with XSS attempt."""
        malicious_input = "<script>alert('xss')</script>"
        
        with pytest.raises(ValidationError) as exc_info:
            SecurityValidator.validate_input_security(malicious_input, "test_field")
            
        error = exc_info.value
        assert "Invalid content detected" in error.message
        
    def test_validate_rate_limit_within_limit(self):
        """Test rate limit validation within limits."""
        # Should not raise any exception
        SecurityValidator.validate_rate_limit("user123", "test_op", 5, 10, 60)
        
    def test_validate_rate_limit_exceeded(self):
        """Test rate limit validation when limit exceeded."""
        with pytest.raises(RateLimitError) as exc_info:
            SecurityValidator.validate_rate_limit("user123", "test_op", 15, 10, 60)
            
        error = exc_info.value
        assert error.limit == 10
        assert error.window == 60


class TestCircuitBreakerManager:
    """Test enhanced circuit breaker manager."""
    
    def test_circuit_breaker_closed_state(self):
        """Test circuit breaker in closed state."""
        cb = CircuitBreakerManager(failure_threshold=3)
        
        # Successful calls should work
        result = cb.call(lambda: "success")
        assert result == "success"
        assert cb.state == "CLOSED"
        
    def test_circuit_breaker_failure_counting(self):
        """Test circuit breaker failure counting."""
        cb = CircuitBreakerManager(failure_threshold=2)
        
        # First failure
        with pytest.raises(ValueError):
            cb.call(lambda: exec('raise ValueError("test")'))
        assert cb.failure_count == 1
        assert cb.state == "CLOSED"
        
        # Second failure should open circuit
        with pytest.raises(ValueError):
            cb.call(lambda: exec('raise ValueError("test")'))
        assert cb.failure_count == 2
        assert cb.state == "OPEN"
        
    def test_circuit_breaker_open_state(self):
        """Test circuit breaker in open state."""
        cb = CircuitBreakerManager(failure_threshold=1)
        
        # Trigger failure to open circuit
        with pytest.raises(ValueError):
            cb.call(lambda: exec('raise ValueError("test")'))
            
        # Subsequent calls should raise CircuitBreakerError
        with pytest.raises(CircuitBreakerError):
            cb.call(lambda: "should not execute")
            
    def test_circuit_breaker_half_open_transition(self):
        """Test circuit breaker half-open transition."""
        cb = CircuitBreakerManager(failure_threshold=1, recovery_timeout=0)
        
        # Open the circuit
        with pytest.raises(ValueError):
            cb.call(lambda: exec('raise ValueError("test")'))
        assert cb.state == "OPEN"
        
        # Should transition to half-open
        result = cb.call(lambda: "success")
        assert result == "success"
        assert cb.state == "HALF_OPEN"


class TestComprehensiveValidator:
    """Test comprehensive validator integration."""
    
    def test_validate_tool_request_success(self):
        """Test successful tool request validation."""
        validator = ComprehensiveValidator()
        
        params = {
            "owner_id": "user123",
            "account_type": "CHECKING",
            "initial_balance": 100.0
        }
        
        schema = {
            "required": ["owner_id", "account_type"],
            "properties": {
                "owner_id": {"type": "string", "minLength": 1},
                "account_type": {"type": "string", "enum": ["CHECKING", "SAVINGS"]},
                "initial_balance": {"type": "number", "minimum": 0}
            }
        }
        
        result = validator.validate_tool_request(
            "create_account",
            params,
            schema,
            Mock()
        )
        
        assert result["owner_id"] == "user123"
        assert result["account_type"] == "CHECKING"
        assert result["initial_balance"] == Decimal("100.0")
        
    def test_validate_tool_request_with_security_check(self):
        """Test tool request validation with security checks."""
        validator = ComprehensiveValidator()
        
        params = {
            "description": "<script>alert('xss')</script>"
        }
        
        schema = {
            "properties": {
                "description": {"type": "string"}
            }
        }
        
        with pytest.raises(ValidationError) as exc_info:
            validator.validate_tool_request("test_tool", params, schema)
            
        error = exc_info.value
        assert "Invalid content detected" in error.message


if __name__ == "__main__":
    pytest.main([__file__])