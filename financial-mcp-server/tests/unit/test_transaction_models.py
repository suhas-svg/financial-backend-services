"""
Unit tests for transaction-related models.
"""

import pytest
from decimal import Decimal
from datetime import datetime
from pydantic import ValidationError

from mcp_financial.models.requests import (
    DepositRequest,
    WithdrawalRequest,
    TransferRequest,
    TransactionReversalRequest,
    TransactionType
)
from mcp_financial.models.responses import (
    TransactionResponse,
    TransactionHistoryResponse,
    TransactionAnalyticsResponse,
    MCPErrorResponse,
    MCPSuccessResponse
)


class TestTransactionRequestModels:
    """Test cases for transaction request models."""
    
    def test_deposit_request_valid(self):
        """Test valid deposit request creation."""
        request = DepositRequest(
            account_id="acc_123",
            amount=Decimal("100.50"),
            description="Test deposit"
        )
        
        assert request.account_id == "acc_123"
        assert request.amount == Decimal("100.50")
        assert request.description == "Test deposit"
    
    def test_deposit_request_minimal(self):
        """Test deposit request with minimal required fields."""
        request = DepositRequest(
            account_id="acc_123",
            amount=Decimal("50.00")
        )
        
        assert request.account_id == "acc_123"
        assert request.amount == Decimal("50.00")
        assert request.description is None
    
    def test_deposit_request_negative_amount(self):
        """Test deposit request with negative amount should fail."""
        with pytest.raises(ValidationError) as exc_info:
            DepositRequest(
                account_id="acc_123",
                amount=Decimal("-100.00")
            )
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Deposit amount must be positive" in str(errors[0]["msg"])
    
    def test_deposit_request_zero_amount(self):
        """Test deposit request with zero amount should fail."""
        with pytest.raises(ValidationError) as exc_info:
            DepositRequest(
                account_id="acc_123",
                amount=Decimal("0.00")
            )
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Deposit amount must be positive" in str(errors[0]["msg"])
    
    def test_deposit_request_empty_account_id(self):
        """Test deposit request with empty account ID should fail."""
        with pytest.raises(ValidationError) as exc_info:
            DepositRequest(
                account_id="",
                amount=Decimal("100.00")
            )
        
        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert "Account ID cannot be empty" in str(errors[0]["msg"])
    
    def test_withdrawal_request_valid(self):
        """Test valid withdrawal request creation."""
        request = WithdrawalRequest(
            account_id="acc_456",
            amount=Decimal("75.25"),
            description="ATM withdrawal"
        )
        
        assert request.account_id == "acc_456"
        assert request.amount == Decimal("75.25")
        assert request.description == "ATM withdrawal"
    
    def test_withdrawal_request_validation_errors(self):
        """Test withdrawal request validation errors."""
        # Test negative amount
        with pytest.raises(ValidationError) as exc_info:
            WithdrawalRequest(
                account_id="acc_123",
                amount=Decimal("-50.00")
            )
        
        errors = exc_info.value.errors()
        assert "Withdrawal amount must be positive" in str(errors[0]["msg"])
        
        # Test empty account ID
        with pytest.raises(ValidationError) as exc_info:
            WithdrawalRequest(
                account_id="   ",  # Whitespace only
                amount=Decimal("50.00")
            )
        
        errors = exc_info.value.errors()
        assert "Account ID cannot be empty" in str(errors[0]["msg"])
    
    def test_transfer_request_valid(self):
        """Test valid transfer request creation."""
        request = TransferRequest(
            from_account_id="acc_123",
            to_account_id="acc_456",
            amount=Decimal("200.00"),
            description="Monthly transfer"
        )
        
        assert request.from_account_id == "acc_123"
        assert request.to_account_id == "acc_456"
        assert request.amount == Decimal("200.00")
        assert request.description == "Monthly transfer"
    
    def test_transfer_request_same_accounts(self):
        """Test transfer request with same source and destination accounts should fail."""
        with pytest.raises(ValidationError) as exc_info:
            TransferRequest(
                from_account_id="acc_123",
                to_account_id="acc_123",  # Same as source
                amount=Decimal("100.00")
            )
        
        errors = exc_info.value.errors()
        assert "Source and destination accounts must be different" in str(errors[0]["msg"])
    
    def test_transfer_request_validation_errors(self):
        """Test transfer request validation errors."""
        # Test negative amount
        with pytest.raises(ValidationError) as exc_info:
            TransferRequest(
                from_account_id="acc_123",
                to_account_id="acc_456",
                amount=Decimal("-100.00")
            )
        
        errors = exc_info.value.errors()
        assert "Transfer amount must be positive" in str(errors[0]["msg"])
    
    def test_transaction_reversal_request_valid(self):
        """Test valid transaction reversal request creation."""
        request = TransactionReversalRequest(
            transaction_id="txn_123",
            reason="Customer dispute"
        )
        
        assert request.transaction_id == "txn_123"
        assert request.reason == "Customer dispute"
    
    def test_transaction_reversal_request_minimal(self):
        """Test transaction reversal request with minimal fields."""
        request = TransactionReversalRequest(
            transaction_id="txn_456"
        )
        
        assert request.transaction_id == "txn_456"
        assert request.reason is None
    
    def test_transaction_reversal_request_empty_id(self):
        """Test transaction reversal request with empty transaction ID should fail."""
        with pytest.raises(ValidationError) as exc_info:
            TransactionReversalRequest(
                transaction_id=""
            )
        
        errors = exc_info.value.errors()
        assert "Transaction ID cannot be empty" in str(errors[0]["msg"])
    
    def test_transaction_type_enum(self):
        """Test TransactionType enum values."""
        assert TransactionType.DEPOSIT == "DEPOSIT"
        assert TransactionType.WITHDRAWAL == "WITHDRAWAL"
        assert TransactionType.TRANSFER == "TRANSFER"
        
        # Test enum membership
        assert "DEPOSIT" in TransactionType
        assert "INVALID_TYPE" not in TransactionType


class TestTransactionResponseModels:
    """Test cases for transaction response models."""
    
    def test_transaction_response_valid(self):
        """Test valid transaction response creation."""
        response = TransactionResponse(
            id="txn_123",
            account_id="acc_456",
            amount=Decimal("100.00"),
            transaction_type="DEPOSIT",
            status="COMPLETED",
            description="Test transaction",
            created_at=datetime(2024, 1, 1, 12, 0, 0),
            updated_at=datetime(2024, 1, 1, 12, 5, 0)
        )
        
        assert response.id == "txn_123"
        assert response.account_id == "acc_456"
        assert response.amount == Decimal("100.00")
        assert response.transaction_type == "DEPOSIT"
        assert response.status == "COMPLETED"
        assert response.description == "Test transaction"
        assert response.created_at == datetime(2024, 1, 1, 12, 0, 0)
        assert response.updated_at == datetime(2024, 1, 1, 12, 5, 0)
    
    def test_transaction_response_minimal(self):
        """Test transaction response with minimal required fields."""
        response = TransactionResponse(
            id="txn_456",
            account_id="acc_789",
            amount=Decimal("50.00"),
            transaction_type="WITHDRAWAL",
            status="PENDING",
            created_at=datetime(2024, 1, 2, 10, 0, 0)
        )
        
        assert response.id == "txn_456"
        assert response.account_id == "acc_789"
        assert response.amount == Decimal("50.00")
        assert response.transaction_type == "WITHDRAWAL"
        assert response.status == "PENDING"
        assert response.description is None
        assert response.updated_at is None
        assert response.from_account_id is None
        assert response.to_account_id is None
    
    def test_transaction_response_transfer(self):
        """Test transaction response for transfer with account IDs."""
        response = TransactionResponse(
            id="txn_transfer_123",
            account_id="acc_source",
            amount=Decimal("200.00"),
            transaction_type="TRANSFER",
            status="COMPLETED",
            created_at=datetime(2024, 1, 3, 14, 0, 0),
            from_account_id="acc_source",
            to_account_id="acc_destination"
        )
        
        assert response.from_account_id == "acc_source"
        assert response.to_account_id == "acc_destination"
        assert response.transaction_type == "TRANSFER"
    
    def test_transaction_history_response_valid(self):
        """Test valid transaction history response creation."""
        transactions = [
            TransactionResponse(
                id="txn_1",
                account_id="acc_123",
                amount=Decimal("100.00"),
                transaction_type="DEPOSIT",
                status="COMPLETED",
                created_at=datetime(2024, 1, 1, 10, 0, 0)
            ),
            TransactionResponse(
                id="txn_2",
                account_id="acc_123",
                amount=Decimal("50.00"),
                transaction_type="WITHDRAWAL",
                status="COMPLETED",
                created_at=datetime(2024, 1, 2, 11, 0, 0)
            )
        ]
        
        response = TransactionHistoryResponse(
            content=transactions,
            total_elements=25,
            total_pages=3,
            current_page=0,
            page_size=10,
            has_next=True,
            has_previous=False
        )
        
        assert len(response.content) == 2
        assert response.total_elements == 25
        assert response.total_pages == 3
        assert response.current_page == 0
        assert response.page_size == 10
        assert response.has_next is True
        assert response.has_previous is False
    
    def test_transaction_analytics_response_valid(self):
        """Test valid transaction analytics response creation."""
        response = TransactionAnalyticsResponse(
            account_id="acc_123",
            total_deposits=Decimal("5000.00"),
            total_withdrawals=Decimal("2000.00"),
            total_transfers_in=Decimal("1500.00"),
            total_transfers_out=Decimal("800.00"),
            transaction_count=45,
            average_transaction_amount=Decimal("155.56"),
            largest_transaction=Decimal("1000.00"),
            period_start=datetime(2024, 1, 1, 0, 0, 0),
            period_end=datetime(2024, 1, 31, 23, 59, 59)
        )
        
        assert response.account_id == "acc_123"
        assert response.total_deposits == Decimal("5000.00")
        assert response.total_withdrawals == Decimal("2000.00")
        assert response.total_transfers_in == Decimal("1500.00")
        assert response.total_transfers_out == Decimal("800.00")
        assert response.transaction_count == 45
        assert response.average_transaction_amount == Decimal("155.56")
        assert response.largest_transaction == Decimal("1000.00")
        assert response.period_start == datetime(2024, 1, 1, 0, 0, 0)
        assert response.period_end == datetime(2024, 1, 31, 23, 59, 59)
    
    def test_transaction_analytics_response_no_account_filter(self):
        """Test transaction analytics response without account filter."""
        response = TransactionAnalyticsResponse(
            total_deposits=Decimal("10000.00"),
            total_withdrawals=Decimal("5000.00"),
            total_transfers_in=Decimal("3000.00"),
            total_transfers_out=Decimal("2000.00"),
            transaction_count=100,
            average_transaction_amount=Decimal("200.00"),
            largest_transaction=Decimal("2500.00"),
            period_start=datetime(2024, 1, 1, 0, 0, 0),
            period_end=datetime(2024, 12, 31, 23, 59, 59)
        )
        
        assert response.account_id is None
        assert response.total_deposits == Decimal("10000.00")
        assert response.transaction_count == 100
    
    def test_mcp_error_response_valid(self):
        """Test valid MCP error response creation."""
        response = MCPErrorResponse(
            error_code="VALIDATION_ERROR",
            error_message="Invalid input parameters",
            details={"field": "amount", "issue": "must be positive"},
            request_id="req_123"
        )
        
        assert response.error_code == "VALIDATION_ERROR"
        assert response.error_message == "Invalid input parameters"
        assert response.details == {"field": "amount", "issue": "must be positive"}
        assert response.request_id == "req_123"
        assert isinstance(response.timestamp, datetime)
    
    def test_mcp_error_response_minimal(self):
        """Test MCP error response with minimal fields."""
        response = MCPErrorResponse(
            error_code="INTERNAL_ERROR",
            error_message="Something went wrong"
        )
        
        assert response.error_code == "INTERNAL_ERROR"
        assert response.error_message == "Something went wrong"
        assert response.details is None
        assert response.request_id is None
        assert isinstance(response.timestamp, datetime)
    
    def test_mcp_success_response_valid(self):
        """Test valid MCP success response creation."""
        response = MCPSuccessResponse(
            message="Transaction completed successfully",
            data={"transaction_id": "txn_123", "amount": 100.00},
            request_id="req_456"
        )
        
        assert response.success is True
        assert response.message == "Transaction completed successfully"
        assert response.data == {"transaction_id": "txn_123", "amount": 100.00}
        assert response.request_id == "req_456"
        assert isinstance(response.timestamp, datetime)
    
    def test_mcp_success_response_minimal(self):
        """Test MCP success response with minimal fields."""
        response = MCPSuccessResponse(
            message="Operation successful"
        )
        
        assert response.success is True
        assert response.message == "Operation successful"
        assert response.data is None
        assert response.request_id is None
        assert isinstance(response.timestamp, datetime)