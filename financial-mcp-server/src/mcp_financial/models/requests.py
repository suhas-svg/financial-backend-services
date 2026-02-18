"""
Request models for MCP tools.
"""

from pydantic import BaseModel, Field, field_validator
from typing import Optional, Dict, Any
from decimal import Decimal
from enum import Enum


class AccountType(str, Enum):
    """Account types supported by the system."""
    CHECKING = "CHECKING"
    SAVINGS = "SAVINGS"
    CREDIT = "CREDIT"


class AccountCreateRequest(BaseModel):
    """Request model for account creation."""
    
    owner_id: str = Field(..., description="Account owner identifier")
    account_type: AccountType = Field(..., description="Type of account")
    initial_balance: Decimal = Field(default=Decimal("0.0"), description="Initial account balance")
    
    @field_validator('initial_balance')
    @classmethod
    def validate_initial_balance(cls, v):
        if v < 0:
            raise ValueError("Initial balance cannot be negative")
        return v
        
    @field_validator('owner_id')
    @classmethod
    def validate_owner_id(cls, v):
        if not v or not v.strip():
            raise ValueError("Owner ID cannot be empty")
        return v.strip()


class AccountUpdateRequest(BaseModel):
    """Request model for account updates."""
    
    account_type: Optional[AccountType] = Field(None, description="Updated account type")
    status: Optional[str] = Field(None, description="Account status")
    
    model_config = {"extra": "forbid"}


class BalanceUpdateRequest(BaseModel):
    """Request model for balance updates."""
    
    new_balance: Decimal = Field(..., description="New account balance")
    reason: Optional[str] = Field(None, description="Reason for balance update")
    
    @field_validator('new_balance')
    @classmethod
    def validate_balance(cls, v):
        if v < 0:
            raise ValueError("Balance cannot be negative")
        return v


class AccountSearchRequest(BaseModel):
    """Request model for account search."""
    
    owner_id: Optional[str] = Field(None, description="Filter by owner ID")
    account_type: Optional[AccountType] = Field(None, description="Filter by account type")
    status: Optional[str] = Field(None, description="Filter by account status")
    min_balance: Optional[Decimal] = Field(None, description="Minimum balance filter")
    max_balance: Optional[Decimal] = Field(None, description="Maximum balance filter")
    page: int = Field(default=0, description="Page number for pagination")
    size: int = Field(default=20, description="Page size for pagination")
    
    @field_validator('page')
    @classmethod
    def validate_page(cls, v):
        if v < 0:
            raise ValueError("Page number cannot be negative")
        return v
        
    @field_validator('size')
    @classmethod
    def validate_size(cls, v):
        if v <= 0 or v > 100:
            raise ValueError("Page size must be between 1 and 100")
        return v


class TransactionType(str, Enum):
    """Transaction types supported by the system."""
    DEPOSIT = "DEPOSIT"
    WITHDRAWAL = "WITHDRAWAL"
    TRANSFER = "TRANSFER"


class DepositRequest(BaseModel):
    """Request model for deposit transactions."""
    
    account_id: str = Field(..., description="Target account identifier")
    amount: Decimal = Field(..., description="Deposit amount")
    description: Optional[str] = Field(None, description="Transaction description")
    
    @field_validator('amount')
    @classmethod
    def validate_amount(cls, v):
        if v <= 0:
            raise ValueError("Deposit amount must be positive")
        return v
        
    @field_validator('account_id')
    @classmethod
    def validate_account_id(cls, v):
        if not v or not v.strip():
            raise ValueError("Account ID cannot be empty")
        return v.strip()


class WithdrawalRequest(BaseModel):
    """Request model for withdrawal transactions."""
    
    account_id: str = Field(..., description="Source account identifier")
    amount: Decimal = Field(..., description="Withdrawal amount")
    description: Optional[str] = Field(None, description="Transaction description")
    
    @field_validator('amount')
    @classmethod
    def validate_amount(cls, v):
        if v <= 0:
            raise ValueError("Withdrawal amount must be positive")
        return v
        
    @field_validator('account_id')
    @classmethod
    def validate_account_id(cls, v):
        if not v or not v.strip():
            raise ValueError("Account ID cannot be empty")
        return v.strip()


class TransferRequest(BaseModel):
    """Request model for transfer transactions."""
    
    from_account_id: str = Field(..., description="Source account identifier")
    to_account_id: str = Field(..., description="Destination account identifier")
    amount: Decimal = Field(..., description="Transfer amount")
    description: Optional[str] = Field(None, description="Transaction description")
    
    @field_validator('amount')
    @classmethod
    def validate_amount(cls, v):
        if v <= 0:
            raise ValueError("Transfer amount must be positive")
        return v
        
    @field_validator('from_account_id', 'to_account_id')
    @classmethod
    def validate_account_ids(cls, v):
        if not v or not v.strip():
            raise ValueError("Account ID cannot be empty")
        return v.strip()
        
    def model_post_init(self, __context):
        """Validate that source and destination accounts are different."""
        if self.from_account_id == self.to_account_id:
            raise ValueError("Source and destination accounts must be different")


class TransactionReversalRequest(BaseModel):
    """Request model for transaction reversals."""
    
    transaction_id: str = Field(..., description="Transaction to reverse")
    reason: Optional[str] = Field(None, description="Reason for reversal")
    
    @field_validator('transaction_id')
    @classmethod
    def validate_transaction_id(cls, v):
        if not v or not v.strip():
            raise ValueError("Transaction ID cannot be empty")
        return v.strip()