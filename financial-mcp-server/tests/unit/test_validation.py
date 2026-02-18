"""
Unit tests for input validation utilities.
"""

import pytest
from decimal import Decimal
from datetime import datetime
from typing import Dict, Any

from mcp_financial.utils.validation import (
    ValidationError,
    validate_account_data,
    validate_transaction_data,
    validate_amount,
    validate_account_id,
    validate_user_id,
    validate_date_range,
    validate_pagination_params,
    sanitize_input
)


class TestValidationUtilities:
    """Test suite for validation utilities."""
    
    def test_validate_amount_valid_cases(self):
        """Test amount validation with valid inputs."""
        # Valid positive amounts
        assert validate_amount(100.0) == Decimal("100.0")
        assert validate_amount(0.01) == Decimal("0.01")
        assert validate_amount(999999.99) == Decimal("999999.99")
        assert validate_amount("100.50") == Decimal("100.50")
        assert validate_amount(Decimal("250.75")) == Decimal("250.75")
    
    def test_validate_amount_invalid_cases(self):
        """Test amount validation with invalid inputs."""
        # Negative amounts
        with pytest.raises(ValidationError, match="Amount must be positive"):
            validate_amount(-100.0)
        
        # Zero amount
        with pytest.raises(ValidationError, match="Amount must be positive"):
            validate_amount(0.0)
        
        # Invalid string
        with pytest.raises(ValidationError, match="Amount must be a valid number"):
            validate_amount("invalid")
        
        # None value
        with pytest.raises(ValidationError, match="Amount is required"):
            validate_amount(None)
        
        # Too many decimal places
        with pytest.raises(ValidationError, match="Amount cannot have more than 2 decimal places"):
            validate_amount(100.123)
    
    def test_validate_account_id_valid_cases(self):
        """Test account ID validation with valid inputs."""
        valid_ids = [
            "acc_123456789",
            "account_abc123",
            "ACC_TEST_001",
            "a1b2c3d4e5f6"
        ]
        
        for account_id in valid_ids:
            assert validate_account_id(account_id) == account_id
    
    def test_validate_account_id_invalid_cases(self):
        """Test account ID validation with invalid inputs."""
        # Empty string
        with pytest.raises(ValidationError, match="Account ID is required"):
            validate_account_id("")
        
        # None value
        with pytest.raises(ValidationError, match="Account ID is required"):
            validate_account_id(None)
        
        # Too short
        with pytest.raises(ValidationError, match="Account ID must be at least 3 characters"):
            validate_account_id("ab")
        
        # Too long
        with pytest.raises(ValidationError, match="Account ID cannot exceed 50 characters"):
            validate_account_id("a" * 51)
        
        # Invalid characters
        with pytest.raises(ValidationError, match="Account ID contains invalid characters"):
            validate_account_id("acc@123!")
    
    def test_validate_user_id_valid_cases(self):
        """Test user ID validation with valid inputs."""
        valid_ids = [
            "user_123456789",
            "customer_abc123",
            "USER_TEST_001",
            "u1b2c3d4e5f6"
        ]
        
        for user_id in valid_ids:
            assert validate_user_id(user_id) == user_id
    
    def test_validate_user_id_invalid_cases(self):
        """Test user ID validation with invalid inputs."""
        # Empty string
        with pytest.raises(ValidationError, match="User ID is required"):
            validate_user_id("")
        
        # None value
        with pytest.raises(ValidationError, match="User ID is required"):
            validate_user_id(None)
        
        # Too short
        with pytest.raises(ValidationError, match="User ID must be at least 3 characters"):
            validate_user_id("ab")
        
        # Invalid characters
        with pytest.raises(ValidationError, match="User ID contains invalid characters"):
            validate_user_id("user@123!")
    
    def test_validate_account_data_valid_cases(self):
        """Test account data validation with valid inputs."""
        valid_data = {
            "ownerId": "user_123",
            "accountType": "CHECKING",
            "balance": 1000.0
        }
        
        result = validate_account_data(valid_data)
        assert result["ownerId"] == "user_123"
        assert result["accountType"] == "CHECKING"
        assert result["balance"] == Decimal("1000.0")
    
    def test_validate_account_data_invalid_cases(self):
        """Test account data validation with invalid inputs."""
        # Missing required fields
        with pytest.raises(ValidationError, match="ownerId is required"):
            validate_account_data({"accountType": "CHECKING"})
        
        # Invalid account type
        with pytest.raises(ValidationError, match="Invalid account type"):
            validate_account_data({
                "ownerId": "user_123",
                "accountType": "INVALID_TYPE",
                "balance": 1000.0
            })
        
        # Invalid balance
        with pytest.raises(ValidationError, match="Amount must be positive"):
            validate_account_data({
                "ownerId": "user_123",
                "accountType": "CHECKING",
                "balance": -100.0
            })
    
    def test_validate_transaction_data_valid_cases(self):
        """Test transaction data validation with valid inputs."""
        valid_data = {
            "accountId": "acc_123",
            "amount": 100.0,
            "transactionType": "DEPOSIT",
            "description": "Test transaction"
        }
        
        result = validate_transaction_data(valid_data)
        assert result["accountId"] == "acc_123"
        assert result["amount"] == Decimal("100.0")
        assert result["transactionType"] == "DEPOSIT"
        assert result["description"] == "Test transaction"
    
    def test_validate_transaction_data_invalid_cases(self):
        """Test transaction data validation with invalid inputs."""
        # Missing required fields
        with pytest.raises(ValidationError, match="accountId is required"):
            validate_transaction_data({
                "amount": 100.0,
                "transactionType": "DEPOSIT"
            })
        
        # Invalid transaction type
        with pytest.raises(ValidationError, match="Invalid transaction type"):
            validate_transaction_data({
                "accountId": "acc_123",
                "amount": 100.0,
                "transactionType": "INVALID_TYPE"
            })
        
        # Description too long
        with pytest.raises(ValidationError, match="Description cannot exceed 500 characters"):
            validate_transaction_data({
                "accountId": "acc_123",
                "amount": 100.0,
                "transactionType": "DEPOSIT",
                "description": "A" * 501
            })
    
    def test_validate_date_range_valid_cases(self):
        """Test date range validation with valid inputs."""
        start_date = "2024-01-01"
        end_date = "2024-01-31"
        
        result = validate_date_range(start_date, end_date)
        assert result["start_date"] == datetime(2024, 1, 1)
        assert result["end_date"] == datetime(2024, 1, 31)
    
    def test_validate_date_range_invalid_cases(self):
        """Test date range validation with invalid inputs."""
        # Invalid date format
        with pytest.raises(ValidationError, match="Invalid date format"):
            validate_date_range("invalid-date", "2024-01-31")
        
        # End date before start date
        with pytest.raises(ValidationError, match="End date must be after start date"):
            validate_date_range("2024-01-31", "2024-01-01")
        
        # Date range too large
        with pytest.raises(ValidationError, match="Date range cannot exceed 1 year"):
            validate_date_range("2024-01-01", "2025-01-02")
    
    def test_validate_pagination_params_valid_cases(self):
        """Test pagination parameter validation with valid inputs."""
        # Default values
        result = validate_pagination_params(None, None)
        assert result["page"] == 0
        assert result["size"] == 20
        
        # Custom values
        result = validate_pagination_params(2, 50)
        assert result["page"] == 2
        assert result["size"] == 50
    
    def test_validate_pagination_params_invalid_cases(self):
        """Test pagination parameter validation with invalid inputs."""
        # Negative page
        with pytest.raises(ValidationError, match="Page number must be non-negative"):
            validate_pagination_params(-1, 20)
        
        # Invalid page size
        with pytest.raises(ValidationError, match="Page size must be between 1 and 1000"):
            validate_pagination_params(0, 0)
        
        # Page size too large
        with pytest.raises(ValidationError, match="Page size must be between 1 and 1000"):
            validate_pagination_params(0, 1001)
    
    def test_sanitize_input_valid_cases(self):
        """Test input sanitization with valid inputs."""
        # String sanitization
        assert sanitize_input("  hello world  ") == "hello world"
        assert sanitize_input("Test String") == "Test String"
        
        # HTML/Script sanitization
        result = sanitize_input("<script>alert('xss')</script>")
        assert "alert" in result  # Content is escaped, not removed
        result2 = sanitize_input("Normal text with <b>bold</b>")
        assert "Normal text with" in result2 and "bold" in result2  # HTML tags are escaped
        
        # SQL injection prevention
        result3 = sanitize_input("'; DROP TABLE users; --")
        assert "DROP TABLE users" in result3  # Content is escaped but preserved
    
    def test_sanitize_input_edge_cases(self):
        """Test input sanitization with edge cases."""
        # None input
        assert sanitize_input(None) is None
        
        # Empty string
        assert sanitize_input("") == ""
        
        # Whitespace only
        assert sanitize_input("   ") == ""
        
        # Unicode characters
        assert sanitize_input("Hello 世界") == "Hello 世界"
        
        # Numbers and special characters
        assert sanitize_input("Price: $123.45") == "Price: $123.45"


class TestValidationErrorHandling:
    """Test validation error handling and messages."""
    
    def test_validation_error_creation(self):
        """Test ValidationError creation and attributes."""
        error = ValidationError("Test error message", field="test_field")
        
        assert str(error) == "Test error message"
        assert error.field == "test_field"
        assert error.error_code == "VALIDATION_ERROR"
    
    def test_validation_error_without_optional_params(self):
        """Test ValidationError creation without optional parameters."""
        error = ValidationError("Simple error")
        
        assert str(error) == "Simple error"
        assert error.field is None
        assert error.error_code == "VALIDATION_ERROR"
    
    def test_multiple_validation_errors(self):
        """Test handling multiple validation errors."""
        errors = []
        
        try:
            validate_account_data({})
        except ValidationError as e:
            errors.append(e)
        
        try:
            validate_amount(-100)
        except ValidationError as e:
            errors.append(e)
        
        assert len(errors) == 2
        assert any("ownerId is required" in str(e) for e in errors)
        assert any("Amount must be positive" in str(e) for e in errors)


class TestValidationIntegration:
    """Test validation integration with other components."""
    
    def test_validation_with_real_data_structures(self):
        """Test validation with realistic data structures."""
        # Complete account creation data
        account_data = {
            "ownerId": "customer_12345",
            "accountType": "SAVINGS",
            "balance": 2500.75,
            "metadata": {
                "branch": "downtown",
                "openedBy": "teller_001"
            }
        }
        
        result = validate_account_data(account_data)
        assert result["ownerId"] == "customer_12345"
        assert result["balance"] == Decimal("2500.75")
        
        # Complete transaction data
        transaction_data = {
            "accountId": "acc_savings_789",
            "amount": 150.25,
            "transactionType": "WITHDRAWAL",
            "description": "ATM withdrawal at Main St location",
            "metadata": {
                "atmId": "ATM_001",
                "location": "Main St & 1st Ave"
            }
        }
        
        result = validate_transaction_data(transaction_data)
        assert result["accountId"] == "acc_savings_789"
        assert result["amount"] == Decimal("150.25")
    
    def test_validation_performance(self):
        """Test validation performance with large datasets."""
        import time
        
        # Test validation of many account records
        start_time = time.perf_counter()
        
        for i in range(1000):
            account_data = {
                "ownerId": f"user_{i}",
                "accountType": "CHECKING",
                "balance": 1000.0 + i
            }
            validate_account_data(account_data)
        
        end_time = time.perf_counter()
        duration = end_time - start_time
        
        # Should validate 1000 records in under 1 second
        assert duration < 1.0, f"Validation too slow: {duration} seconds"
    
    def test_validation_thread_safety(self):
        """Test validation thread safety."""
        import threading
        import concurrent.futures
        
        def validate_in_thread(thread_id):
            """Validate data in a separate thread."""
            results = []
            for i in range(100):
                account_data = {
                    "ownerId": f"thread_{thread_id}_user_{i}",
                    "accountType": "CHECKING",
                    "balance": 1000.0 + i
                }
                result = validate_account_data(account_data)
                results.append(result)
            return results
        
        # Run validation in multiple threads
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
            futures = [executor.submit(validate_in_thread, i) for i in range(5)]
            results = [future.result() for future in concurrent.futures.as_completed(futures)]
        
        # Verify all threads completed successfully
        assert len(results) == 5
        for thread_results in results:
            assert len(thread_results) == 100