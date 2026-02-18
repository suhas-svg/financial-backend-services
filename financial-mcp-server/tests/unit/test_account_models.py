"""
Unit tests for account-related data models.
"""

import pytest
from decimal import Decimal
from datetime import datetime
from pydantic import ValidationError

from mcp_financial.models import (
    AccountCreateRequest,
    AccountUpdateRequest,
    BalanceUpdateRequest,
    AccountSearchRequest,
    AccountType,
    AccountResponse,
    BalanceResponse,
    AccountAnalyticsResponse,
    AccountSearchResponse,
    MCPErrorResponse,
    MCPSuccessResponse
)


class TestAccountCreateRequest:
    """Test suite for AccountCreateRequest model."""
    
    def test_valid_account_create_request(self):
        """Test valid account creation request."""
        request = AccountCreateRequest(
            owner_id="user_123",
            account_type=AccountType.CHECKING,
            initial_balance=Decimal("1000.00")
        )
        
        assert request.owner_id == "user_123"
        assert request.account_type == AccountType.CHECKING
        assert request.initial_balance == Decimal("1000.00")
    
    def test_account_create_request_default_balance(self):
        """Test account creation request with default balance."""
        request = AccountCreateRequest(
            owner_id="user_123",
            account_type=AccountType.SAVINGS
        )
        
        assert request.initial_balance == Decimal("0.0")
    
    def test_account_create_request_negative_balance(self):
        """Test account creation request with negative balance."""
        with pytest.raises(ValidationError) as exc_info:
            AccountCreateRequest(
                owner_id="user_123",
                account_type=AccountType.CHECKING,
                initial_balance=Decimal("-100.00")
            )
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Initial balance cannot be negative" in str(errors[0]["msg"])
    
    def test_account_create_request_empty_owner_id(self):
        """Test account creation request with empty owner ID."""
        with pytest.raises(ValidationError) as exc_info:
            AccountCreateRequest(
                owner_id="",
                account_type=AccountType.CHECKING
            )
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Owner ID cannot be empty" in str(errors[0]["msg"])
    
    def test_account_create_request_whitespace_owner_id(self):
        """Test account creation request with whitespace-only owner ID."""
        with pytest.raises(ValidationError) as exc_info:
            AccountCreateRequest(
                owner_id="   ",
                account_type=AccountType.CHECKING
            )
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Owner ID cannot be empty" in str(errors[0]["msg"])
    
    def test_account_create_request_strips_owner_id(self):
        """Test that owner ID is stripped of whitespace."""
        request = AccountCreateRequest(
            owner_id="  user_123  ",
            account_type=AccountType.CHECKING
        )
        
        assert request.owner_id == "user_123"
    
    def test_account_type_enum_values(self):
        """Test AccountType enum values."""
        assert AccountType.CHECKING == "CHECKING"
        assert AccountType.SAVINGS == "SAVINGS"
        assert AccountType.CREDIT == "CREDIT"
    
    def test_invalid_account_type(self):
        """Test invalid account type."""
        with pytest.raises(ValidationError):
            AccountCreateRequest(
                owner_id="user_123",
                account_type="INVALID_TYPE"
            )


class TestAccountUpdateRequest:
    """Test suite for AccountUpdateRequest model."""
    
    def test_valid_account_update_request(self):
        """Test valid account update request."""
        request = AccountUpdateRequest(
            account_type=AccountType.SAVINGS,
            status="ACTIVE"
        )
        
        assert request.account_type == AccountType.SAVINGS
        assert request.status == "ACTIVE"
    
    def test_account_update_request_partial(self):
        """Test partial account update request."""
        request = AccountUpdateRequest(account_type=AccountType.CHECKING)
        
        assert request.account_type == AccountType.CHECKING
        assert request.status is None
    
    def test_account_update_request_empty(self):
        """Test empty account update request."""
        request = AccountUpdateRequest()
        
        assert request.account_type is None
        assert request.status is None


class TestBalanceUpdateRequest:
    """Test suite for BalanceUpdateRequest model."""
    
    def test_valid_balance_update_request(self):
        """Test valid balance update request."""
        request = BalanceUpdateRequest(
            new_balance=Decimal("1500.00"),
            reason="Salary deposit"
        )
        
        assert request.new_balance == Decimal("1500.00")
        assert request.reason == "Salary deposit"
    
    def test_balance_update_request_no_reason(self):
        """Test balance update request without reason."""
        request = BalanceUpdateRequest(new_balance=Decimal("1500.00"))
        
        assert request.new_balance == Decimal("1500.00")
        assert request.reason is None
    
    def test_balance_update_request_negative_balance(self):
        """Test balance update request with negative balance."""
        with pytest.raises(ValidationError) as exc_info:
            BalanceUpdateRequest(new_balance=Decimal("-100.00"))
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Balance cannot be negative" in str(errors[0]["msg"])


class TestAccountSearchRequest:
    """Test suite for AccountSearchRequest model."""
    
    def test_valid_account_search_request(self):
        """Test valid account search request."""
        request = AccountSearchRequest(
            owner_id="user_123",
            account_type=AccountType.CHECKING,
            status="ACTIVE",
            min_balance=Decimal("100.00"),
            max_balance=Decimal("10000.00"),
            page=1,
            size=10
        )
        
        assert request.owner_id == "user_123"
        assert request.account_type == AccountType.CHECKING
        assert request.status == "ACTIVE"
        assert request.min_balance == Decimal("100.00")
        assert request.max_balance == Decimal("10000.00")
        assert request.page == 1
        assert request.size == 10
    
    def test_account_search_request_defaults(self):
        """Test account search request with default values."""
        request = AccountSearchRequest()
        
        assert request.owner_id is None
        assert request.account_type is None
        assert request.status is None
        assert request.min_balance is None
        assert request.max_balance is None
        assert request.page == 0
        assert request.size == 20
    
    def test_account_search_request_negative_page(self):
        """Test account search request with negative page number."""
        with pytest.raises(ValidationError) as exc_info:
            AccountSearchRequest(page=-1)
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Page number cannot be negative" in str(errors[0]["msg"])
    
    def test_account_search_request_invalid_size(self):
        """Test account search request with invalid size."""
        with pytest.raises(ValidationError) as exc_info:
            AccountSearchRequest(size=0)
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Page size must be between 1 and 100" in str(errors[0]["msg"])
        
        with pytest.raises(ValidationError) as exc_info:
            AccountSearchRequest(size=101)
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Page size must be between 1 and 100" in str(errors[0]["msg"])


class TestAccountResponse:
    """Test suite for AccountResponse model."""
    
    def test_valid_account_response(self):
        """Test valid account response."""
        response = AccountResponse(
            id="acc_123",
            owner_id="user_123",
            account_type="CHECKING",
            balance=Decimal("1000.00"),
            status="ACTIVE",
            created_at=datetime(2024, 1, 1, 10, 0, 0),
            updated_at=datetime(2024, 1, 1, 11, 0, 0)
        )
        
        assert response.id == "acc_123"
        assert response.owner_id == "user_123"
        assert response.account_type == "CHECKING"
        assert response.balance == Decimal("1000.00")
        assert response.status == "ACTIVE"
        assert response.created_at == datetime(2024, 1, 1, 10, 0, 0)
        assert response.updated_at == datetime(2024, 1, 1, 11, 0, 0)
    
    def test_account_response_no_updated_at(self):
        """Test account response without updated_at."""
        response = AccountResponse(
            id="acc_123",
            owner_id="user_123",
            account_type="CHECKING",
            balance=Decimal("1000.00"),
            status="ACTIVE",
            created_at=datetime(2024, 1, 1, 10, 0, 0)
        )
        
        assert response.updated_at is None


class TestBalanceResponse:
    """Test suite for BalanceResponse model."""
    
    def test_valid_balance_response(self):
        """Test valid balance response."""
        response = BalanceResponse(
            account_id="acc_123",
            balance=Decimal("1000.00"),
            available_balance=Decimal("950.00"),
            pending_balance=Decimal("50.00"),
            last_updated=datetime(2024, 1, 1, 10, 0, 0)
        )
        
        assert response.account_id == "acc_123"
        assert response.balance == Decimal("1000.00")
        assert response.available_balance == Decimal("950.00")
        assert response.pending_balance == Decimal("50.00")
        assert response.last_updated == datetime(2024, 1, 1, 10, 0, 0)
    
    def test_balance_response_minimal(self):
        """Test balance response with minimal fields."""
        response = BalanceResponse(
            account_id="acc_123",
            balance=Decimal("1000.00"),
            last_updated=datetime(2024, 1, 1, 10, 0, 0)
        )
        
        assert response.available_balance is None
        assert response.pending_balance is None


class TestAccountAnalyticsResponse:
    """Test suite for AccountAnalyticsResponse model."""
    
    def test_valid_analytics_response(self):
        """Test valid analytics response."""
        response = AccountAnalyticsResponse(
            account_id="acc_123",
            total_deposits=Decimal("5000.00"),
            total_withdrawals=Decimal("2000.00"),
            transaction_count=25,
            average_balance=Decimal("1500.00"),
            balance_trend="increasing",
            last_transaction_date=datetime(2024, 1, 1, 15, 0, 0)
        )
        
        assert response.account_id == "acc_123"
        assert response.total_deposits == Decimal("5000.00")
        assert response.total_withdrawals == Decimal("2000.00")
        assert response.transaction_count == 25
        assert response.average_balance == Decimal("1500.00")
        assert response.balance_trend == "increasing"
        assert response.last_transaction_date == datetime(2024, 1, 1, 15, 0, 0)
    
    def test_analytics_response_no_last_transaction(self):
        """Test analytics response without last transaction date."""
        response = AccountAnalyticsResponse(
            account_id="acc_123",
            total_deposits=Decimal("0.00"),
            total_withdrawals=Decimal("0.00"),
            transaction_count=0,
            average_balance=Decimal("0.00"),
            balance_trend="stable"
        )
        
        assert response.last_transaction_date is None


class TestAccountSearchResponse:
    """Test suite for AccountSearchResponse model."""
    
    def test_valid_search_response(self):
        """Test valid search response."""
        accounts = [
            AccountResponse(
                id="acc_1",
                owner_id="user_1",
                account_type="CHECKING",
                balance=Decimal("1000.00"),
                status="ACTIVE",
                created_at=datetime(2024, 1, 1, 10, 0, 0)
            ),
            AccountResponse(
                id="acc_2",
                owner_id="user_1",
                account_type="SAVINGS",
                balance=Decimal("5000.00"),
                status="ACTIVE",
                created_at=datetime(2024, 1, 1, 10, 0, 0)
            )
        ]
        
        response = AccountSearchResponse(
            content=accounts,
            total_elements=2,
            total_pages=1,
            current_page=0,
            page_size=20,
            has_next=False,
            has_previous=False
        )
        
        assert len(response.content) == 2
        assert response.total_elements == 2
        assert response.total_pages == 1
        assert response.current_page == 0
        assert response.page_size == 20
        assert response.has_next is False
        assert response.has_previous is False


class TestMCPErrorResponse:
    """Test suite for MCPErrorResponse model."""
    
    def test_valid_error_response(self):
        """Test valid error response."""
        response = MCPErrorResponse(
            error_code="VALIDATION_ERROR",
            error_message="Invalid parameters",
            details={"field": "owner_id", "issue": "cannot be empty"},
            request_id="req_123"
        )
        
        assert response.error_code == "VALIDATION_ERROR"
        assert response.error_message == "Invalid parameters"
        assert response.details == {"field": "owner_id", "issue": "cannot be empty"}
        assert response.request_id == "req_123"
        assert isinstance(response.timestamp, datetime)
    
    def test_error_response_minimal(self):
        """Test error response with minimal fields."""
        response = MCPErrorResponse(
            error_code="INTERNAL_ERROR",
            error_message="Something went wrong"
        )
        
        assert response.details is None
        assert response.request_id is None
        assert isinstance(response.timestamp, datetime)


class TestMCPSuccessResponse:
    """Test suite for MCPSuccessResponse model."""
    
    def test_valid_success_response(self):
        """Test valid success response."""
        response = MCPSuccessResponse(
            message="Operation completed successfully",
            data={"account_id": "acc_123", "balance": 1000.00},
            request_id="req_123"
        )
        
        assert response.success is True
        assert response.message == "Operation completed successfully"
        assert response.data == {"account_id": "acc_123", "balance": 1000.00}
        assert response.request_id == "req_123"
        assert isinstance(response.timestamp, datetime)
    
    def test_success_response_minimal(self):
        """Test success response with minimal fields."""
        response = MCPSuccessResponse(
            message="Success"
        )
        
        assert response.success is True
        assert response.data is None
        assert response.request_id is None
        assert isinstance(response.timestamp, datetime)