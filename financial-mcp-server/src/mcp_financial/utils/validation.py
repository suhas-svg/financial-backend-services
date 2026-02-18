"""
Input validation utilities for MCP tools.
"""

import re
import logging
from typing import Any, Dict, List, Optional, Union, Callable
from decimal import Decimal, InvalidOperation
from datetime import datetime
from pydantic import BaseModel, ValidationError as PydanticValidationError, validator

from ..exceptions.base import ValidationError, BusinessRuleError
from ..exceptions.handlers import ValidationErrorCollector

logger = logging.getLogger(__name__)


class AccountValidator:
    """Validation utilities for account operations."""
    
    VALID_ACCOUNT_TYPES = ["CHECKING", "SAVINGS", "CREDIT", "INVESTMENT"]
    ACCOUNT_ID_PATTERN = re.compile(r'^[A-Za-z0-9\-_]{1,50}$')
    
    @staticmethod
    def validate_account_id(account_id: str) -> str:
        """Validate account ID format."""
        if not account_id:
            raise ValidationError("Account ID is required")
            
        if not isinstance(account_id, str):
            raise ValidationError("Account ID must be a string")
            
        if not AccountValidator.ACCOUNT_ID_PATTERN.match(account_id):
            raise ValidationError(
                "Account ID must contain only alphanumeric characters, hyphens, and underscores"
            )
            
        return account_id.strip()
        
    @staticmethod
    def validate_account_type(account_type: str) -> str:
        """Validate account type."""
        if not account_type:
            raise ValidationError("Account type is required")
            
        account_type = account_type.upper().strip()
        if account_type not in AccountValidator.VALID_ACCOUNT_TYPES:
            raise ValidationError(
                f"Invalid account type. Must be one of: {', '.join(AccountValidator.VALID_ACCOUNT_TYPES)}"
            )
            
        return account_type
        
    @staticmethod
    def validate_owner_id(owner_id: str) -> str:
        """Validate account owner ID."""
        if not owner_id:
            raise ValidationError("Owner ID is required")
            
        if not isinstance(owner_id, str):
            raise ValidationError("Owner ID must be a string")
            
        owner_id = owner_id.strip()
        if len(owner_id) < 1 or len(owner_id) > 100:
            raise ValidationError("Owner ID must be between 1 and 100 characters")
            
        return owner_id


class TransactionValidator:
    """Validation utilities for transaction operations."""
    
    VALID_TRANSACTION_TYPES = ["DEPOSIT", "WITHDRAWAL", "TRANSFER", "REVERSAL"]
    MIN_AMOUNT = Decimal("0.01")
    MAX_AMOUNT = Decimal("1000000.00")
    
    @staticmethod
    def validate_amount(amount: Union[str, float, Decimal]) -> Decimal:
        """Validate transaction amount."""
        if amount is None:
            raise ValidationError("Amount is required")
            
        try:
            decimal_amount = Decimal(str(amount))
        except (InvalidOperation, ValueError):
            raise ValidationError("Amount must be a valid number")
            
        if decimal_amount <= 0:
            raise ValidationError("Amount must be positive")
            
        if decimal_amount < TransactionValidator.MIN_AMOUNT:
            raise ValidationError(f"Amount must be at least {TransactionValidator.MIN_AMOUNT}")
            
        if decimal_amount > TransactionValidator.MAX_AMOUNT:
            raise ValidationError(f"Amount cannot exceed {TransactionValidator.MAX_AMOUNT}")
            
        # Check decimal places (max 2)
        if decimal_amount.as_tuple().exponent < -2:
            raise ValidationError("Amount cannot have more than 2 decimal places")
            
        return decimal_amount
        
    @staticmethod
    def validate_transaction_type(transaction_type: str) -> str:
        """Validate transaction type."""
        if not transaction_type:
            raise ValidationError("Transaction type is required")
            
        transaction_type = transaction_type.upper().strip()
        if transaction_type not in TransactionValidator.VALID_TRANSACTION_TYPES:
            raise ValidationError(
                f"Invalid transaction type. Must be one of: {', '.join(TransactionValidator.VALID_TRANSACTION_TYPES)}"
            )
            
        return transaction_type
        
    @staticmethod
    def validate_description(description: Optional[str]) -> Optional[str]:
        """Validate transaction description."""
        if description is None:
            return None
            
        if not isinstance(description, str):
            raise ValidationError("Description must be a string")
            
        description = description.strip()
        if len(description) > 500:
            raise ValidationError("Description cannot exceed 500 characters")
            
        return description if description else None


class AuthValidator:
    """Validation utilities for authentication."""
    
    @staticmethod
    def validate_jwt_token(token: str) -> str:
        """Validate JWT token format."""
        if not token:
            raise ValidationError("Authentication token is required")
            
        if not isinstance(token, str):
            raise ValidationError("Token must be a string")
            
        token = token.strip()
        
        # Remove Bearer prefix if present
        if token.startswith('Bearer '):
            token = token[7:]
            
        # Basic JWT format check (3 parts separated by dots)
        parts = token.split('.')
        if len(parts) != 3:
            raise ValidationError("Invalid JWT token format")
            
        return token


class QueryValidator:
    """Validation utilities for query operations."""
    
    @staticmethod
    def validate_page_params(page: int = 0, size: int = 20) -> tuple[int, int]:
        """Validate pagination parameters."""
        if not isinstance(page, int) or page < 0:
            raise ValidationError("Page must be a non-negative integer")
            
        if not isinstance(size, int) or size < 1 or size > 1000:
            raise ValidationError("Size must be between 1 and 1000")
            
        return page, size
        
    @staticmethod
    def validate_date_range(start_date: Optional[str], end_date: Optional[str]) -> tuple[Optional[datetime], Optional[datetime]]:
        """Validate date range parameters."""
        start_dt = None
        end_dt = None
        
        if start_date:
            try:
                start_dt = datetime.fromisoformat(start_date.replace('Z', '+00:00'))
            except ValueError:
                raise ValidationError("Start date must be in ISO format (YYYY-MM-DDTHH:MM:SS)")
                
        if end_date:
            try:
                end_dt = datetime.fromisoformat(end_date.replace('Z', '+00:00'))
            except ValueError:
                raise ValidationError("End date must be in ISO format (YYYY-MM-DDTHH:MM:SS)")
                
        if start_dt and end_dt and start_dt > end_dt:
            raise ValidationError("Start date must be before end date")
            
        return start_dt, end_dt


class InputSanitizer:
    """Input sanitization utilities."""
    
    @staticmethod
    def sanitize_string(value: str, max_length: int = 1000) -> str:
        """Sanitize string input."""
        if not isinstance(value, str):
            raise ValidationError("Value must be a string")
            
        # Remove null bytes and control characters
        sanitized = ''.join(char for char in value if ord(char) >= 32 or char in '\t\n\r')
        
        # Trim whitespace
        sanitized = sanitized.strip()
        
        # Check length
        if len(sanitized) > max_length:
            raise ValidationError(f"Value cannot exceed {max_length} characters")
            
        return sanitized
        
    @staticmethod
    def sanitize_dict(data: Dict[str, Any], allowed_keys: List[str]) -> Dict[str, Any]:
        """Sanitize dictionary input by filtering allowed keys."""
        if not isinstance(data, dict):
            raise ValidationError("Data must be a dictionary")
            
        sanitized = {}
        for key in allowed_keys:
            if key in data:
                sanitized[key] = data[key]
                
        return sanitized


def validate_required_fields(data: Dict[str, Any], required_fields: List[str]) -> None:
    """Validate that all required fields are present."""
    missing_fields = []
    for field in required_fields:
        if field not in data or data[field] is None:
            missing_fields.append(field)
            
    if missing_fields:
        raise ValidationError(f"Missing required fields: {', '.join(missing_fields)}")


def validate_required_params(params: Dict[str, Any]) -> Optional[str]:
    """
    Validate that required parameters are present and not empty.
    
    Args:
        params: Dictionary of parameter names and values
        
    Returns:
        Error message if validation fails, None if successful
    """
    for param_name, param_value in params.items():
        if param_value is None or (isinstance(param_value, str) and not param_value.strip()):
            return f"Parameter '{param_name}' is required and cannot be empty"
    return None


def validate_date_format(date_string: str) -> bool:
    """
    Validate date string is in YYYY-MM-DD format.
    
    Args:
        date_string: Date string to validate
        
    Returns:
        True if valid, False otherwise
    """
    try:
        datetime.strptime(date_string, "%Y-%m-%d")
        return True
    except ValueError:
        return False


def validate_pagination_params(page: int, size: int, max_size: int = 100) -> Optional[str]:
    """
    Validate pagination parameters.
    
    Args:
        page: Page number (0-based)
        size: Page size
        max_size: Maximum allowed page size
        
    Returns:
        Error message if validation fails, None if successful
    """
    if not isinstance(page, int) or page < 0:
        return "Page must be a non-negative integer"
    
    if not isinstance(size, int) or size < 1:
        return "Size must be a positive integer"
    
    if size > max_size:
        return f"Size cannot exceed {max_size}"
    
    return None


def validate_mcp_tool_params(params: Dict[str, Any], schema: Dict[str, Any]) -> Dict[str, Any]:
    """
    Validate MCP tool parameters against schema.
    
    Args:
        params: Tool parameters
        schema: Parameter schema definition
        
    Returns:
        Validated and sanitized parameters
        
    Raises:
        ValidationError: If validation fails
    """
    validated = {}
    
    # Check required parameters
    required = schema.get('required', [])
    validate_required_fields(params, required)
    
    # Validate each parameter
    properties = schema.get('properties', {})
    for param_name, param_schema in properties.items():
        if param_name in params:
            value = params[param_name]
            param_type = param_schema.get('type', 'string')
            
            # Type validation
            if param_type == 'string' and not isinstance(value, str):
                raise ValidationError(f"Parameter '{param_name}' must be a string")
            elif param_type == 'number' and not isinstance(value, (int, float)):
                raise ValidationError(f"Parameter '{param_name}' must be a number")
            elif param_type == 'boolean' and not isinstance(value, bool):
                raise ValidationError(f"Parameter '{param_name}' must be a boolean")
                
            validated[param_name] = value
            
    return validated


class EnhancedValidator:
    """Enhanced validation utilities with comprehensive error handling."""
    
    @staticmethod
    def validate_mcp_tool_input(
        params: Dict[str, Any],
        schema: Dict[str, Any],
        request_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Comprehensive MCP tool parameter validation.
        
        Args:
            params: Input parameters
            schema: Validation schema
            request_id: Request identifier for error tracking
            
        Returns:
            Validated and sanitized parameters
            
        Raises:
            ValidationError: If validation fails
        """
        collector = ValidationErrorCollector()
        validated = {}
        
        # Check required parameters
        required = schema.get('required', [])
        for param_name in required:
            if param_name not in params or params[param_name] is None:
                collector.add_error(param_name, f"Parameter '{param_name}' is required")
            elif isinstance(params[param_name], str) and not params[param_name].strip():
                collector.add_error(param_name, f"Parameter '{param_name}' cannot be empty")
                
        # Validate each parameter
        properties = schema.get('properties', {})
        for param_name, param_schema in properties.items():
            if param_name in params:
                try:
                    validated[param_name] = EnhancedValidator._validate_parameter(
                        param_name, params[param_name], param_schema
                    )
                except ValidationError as e:
                    collector.add_error(param_name, e.message, params[param_name])
                    
        # Raise if any validation errors
        collector.raise_if_errors(request_id)
        
        return validated
        
    @staticmethod
    def _validate_parameter(param_name: str, value: Any, schema: Dict[str, Any]) -> Any:
        """Validate individual parameter against schema."""
        param_type = schema.get('type', 'string')
        
        # Type validation
        if param_type == 'string':
            if not isinstance(value, str):
                raise ValidationError(f"Parameter '{param_name}' must be a string")
            return EnhancedValidator._validate_string_parameter(param_name, value, schema)
            
        elif param_type == 'number':
            if not isinstance(value, (int, float, Decimal)):
                raise ValidationError(f"Parameter '{param_name}' must be a number")
            return EnhancedValidator._validate_number_parameter(param_name, value, schema)
            
        elif param_type == 'integer':
            if not isinstance(value, int):
                raise ValidationError(f"Parameter '{param_name}' must be an integer")
            return EnhancedValidator._validate_integer_parameter(param_name, value, schema)
            
        elif param_type == 'boolean':
            if not isinstance(value, bool):
                raise ValidationError(f"Parameter '{param_name}' must be a boolean")
            return value
            
        elif param_type == 'array':
            if not isinstance(value, list):
                raise ValidationError(f"Parameter '{param_name}' must be an array")
            return EnhancedValidator._validate_array_parameter(param_name, value, schema)
            
        else:
            return value
            
    @staticmethod
    def _validate_string_parameter(param_name: str, value: str, schema: Dict[str, Any]) -> str:
        """Validate string parameter with additional constraints."""
        # Sanitize
        sanitized = InputSanitizer.sanitize_string(value)
        
        # Length validation
        min_length = schema.get('minLength', 0)
        max_length = schema.get('maxLength', 1000)
        
        if len(sanitized) < min_length:
            raise ValidationError(f"Parameter '{param_name}' must be at least {min_length} characters")
        if len(sanitized) > max_length:
            raise ValidationError(f"Parameter '{param_name}' cannot exceed {max_length} characters")
            
        # Pattern validation
        pattern = schema.get('pattern')
        if pattern and not re.match(pattern, sanitized):
            raise ValidationError(f"Parameter '{param_name}' does not match required pattern")
            
        # Enum validation
        enum_values = schema.get('enum')
        if enum_values and sanitized not in enum_values:
            raise ValidationError(f"Parameter '{param_name}' must be one of: {', '.join(enum_values)}")
            
        return sanitized
        
    @staticmethod
    def _validate_number_parameter(param_name: str, value: Union[int, float, Decimal], schema: Dict[str, Any]) -> Decimal:
        """Validate number parameter with constraints."""
        try:
            decimal_value = Decimal(str(value))
        except (InvalidOperation, ValueError):
            raise ValidationError(f"Parameter '{param_name}' must be a valid number")
            
        # Range validation
        minimum = schema.get('minimum')
        maximum = schema.get('maximum')
        
        if minimum is not None and decimal_value < Decimal(str(minimum)):
            raise ValidationError(f"Parameter '{param_name}' must be at least {minimum}")
        if maximum is not None and decimal_value > Decimal(str(maximum)):
            raise ValidationError(f"Parameter '{param_name}' cannot exceed {maximum}")
            
        # Decimal places validation
        decimal_places = schema.get('decimalPlaces', 2)
        if decimal_value.as_tuple().exponent < -decimal_places:
            raise ValidationError(f"Parameter '{param_name}' cannot have more than {decimal_places} decimal places")
            
        return decimal_value
        
    @staticmethod
    def _validate_integer_parameter(param_name: str, value: int, schema: Dict[str, Any]) -> int:
        """Validate integer parameter with constraints."""
        minimum = schema.get('minimum')
        maximum = schema.get('maximum')
        
        if minimum is not None and value < minimum:
            raise ValidationError(f"Parameter '{param_name}' must be at least {minimum}")
        if maximum is not None and value > maximum:
            raise ValidationError(f"Parameter '{param_name}' cannot exceed {maximum}")
            
        return value
        
    @staticmethod
    def _validate_array_parameter(param_name: str, value: List[Any], schema: Dict[str, Any]) -> List[Any]:
        """Validate array parameter with constraints."""
        min_items = schema.get('minItems', 0)
        max_items = schema.get('maxItems', 1000)
        
        if len(value) < min_items:
            raise ValidationError(f"Parameter '{param_name}' must have at least {min_items} items")
        if len(value) > max_items:
            raise ValidationError(f"Parameter '{param_name}' cannot have more than {max_items} items")
            
        # Validate items if schema provided
        items_schema = schema.get('items')
        if items_schema:
            validated_items = []
            for i, item in enumerate(value):
                try:
                    validated_item = EnhancedValidator._validate_parameter(f"{param_name}[{i}]", item, items_schema)
                    validated_items.append(validated_item)
                except ValidationError as e:
                    raise ValidationError(f"Parameter '{param_name}[{i}]': {e.message}")
            return validated_items
            
        return value


class BusinessRuleValidator:
    """Validator for business rules and constraints."""
    
    @staticmethod
    def validate_account_creation_rules(
        owner_id: str,
        account_type: str,
        initial_balance: Decimal,
        user_context: Any
    ) -> None:
        """Validate business rules for account creation."""
        collector = ValidationErrorCollector()
        
        # Rule: Initial balance cannot be negative for most account types
        if account_type != "CREDIT" and initial_balance < 0:
            collector.add_error(
                "initial_balance",
                f"Initial balance cannot be negative for {account_type} accounts",
                initial_balance,
                "business_rule_violation"
            )
            
        # Rule: Credit accounts must have zero or negative initial balance
        if account_type == "CREDIT" and initial_balance > 0:
            collector.add_error(
                "initial_balance",
                "Credit accounts cannot have positive initial balance",
                initial_balance,
                "business_rule_violation"
            )
            
        # Rule: Investment accounts require minimum balance
        if account_type == "INVESTMENT" and initial_balance < Decimal("1000.00"):
            collector.add_error(
                "initial_balance",
                "Investment accounts require minimum balance of $1,000.00",
                initial_balance,
                "business_rule_violation"
            )
            
        collector.raise_if_errors()
        
    @staticmethod
    def validate_transaction_rules(
        transaction_type: str,
        amount: Decimal,
        account_balance: Optional[Decimal] = None,
        daily_limit: Optional[Decimal] = None,
        daily_total: Optional[Decimal] = None
    ) -> None:
        """Validate business rules for transactions."""
        collector = ValidationErrorCollector()
        
        # Rule: Withdrawal cannot exceed available balance
        if transaction_type == "WITHDRAWAL" and account_balance is not None:
            if amount > account_balance:
                collector.add_error(
                    "amount",
                    f"Withdrawal amount ${amount} exceeds available balance ${account_balance}",
                    amount,
                    "insufficient_funds"
                )
                
        # Rule: Daily transaction limit
        if daily_limit is not None and daily_total is not None:
            if daily_total + amount > daily_limit:
                collector.add_error(
                    "amount",
                    f"Transaction would exceed daily limit of ${daily_limit}",
                    amount,
                    "daily_limit_exceeded"
                )
                
        # Rule: Large transaction validation
        if amount > Decimal("10000.00"):
            # This would typically trigger additional verification
            logger.warning(f"Large transaction detected: ${amount}")
            
        collector.raise_if_errors()


class SecurityValidator:
    """Security-focused validation utilities."""
    
    @staticmethod
    def validate_input_security(value: str, field_name: str) -> str:
        """Validate input for security concerns."""
        # Check for SQL injection patterns
        sql_patterns = [
            r"(\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|UNION)\b)",
            r"(--|#|/\*|\*/)",
            r"(\b(OR|AND)\s+\d+\s*=\s*\d+)",
            r"(\bOR\s+\w+\s*=\s*\w+)",
        ]
        
        for pattern in sql_patterns:
            if re.search(pattern, value, re.IGNORECASE):
                raise ValidationError(f"Invalid characters detected in {field_name}")
                
        # Check for XSS patterns
        xss_patterns = [
            r"<script[^>]*>.*?</script>",
            r"javascript:",
            r"on\w+\s*=",
            r"<iframe[^>]*>",
        ]
        
        for pattern in xss_patterns:
            if re.search(pattern, value, re.IGNORECASE):
                raise ValidationError(f"Invalid content detected in {field_name}")
                
        return value
        
    @staticmethod
    def validate_rate_limit(
        user_id: str,
        operation: str,
        current_count: int,
        limit: int,
        window_seconds: int
    ) -> None:
        """Validate rate limiting constraints."""
        if current_count >= limit:
            from ..exceptions.base import RateLimitError
            raise RateLimitError(
                message=f"Rate limit exceeded for {operation}",
                limit=limit,
                window=window_seconds,
                details={"user_id": user_id, "current_count": current_count}
            )


class ComprehensiveValidator:
    """Main validator that combines all validation types."""
    
    def __init__(self):
        self.account_validator = AccountValidator()
        self.transaction_validator = TransactionValidator()
        self.auth_validator = AuthValidator()
        self.query_validator = QueryValidator()
        self.enhanced_validator = EnhancedValidator()
        self.business_validator = BusinessRuleValidator()
        self.security_validator = SecurityValidator()
        
    def validate_tool_request(
        self,
        tool_name: str,
        params: Dict[str, Any],
        schema: Dict[str, Any],
        user_context: Any = None,
        request_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Comprehensive validation for MCP tool requests.
        
        Args:
            tool_name: Name of the MCP tool
            params: Tool parameters
            schema: Parameter validation schema
            user_context: User context for business rule validation
            request_id: Request identifier
            
        Returns:
            Validated and sanitized parameters
            
        Raises:
            ValidationError: If validation fails
            BusinessRuleError: If business rules are violated
        """
        try:
            # Basic parameter validation
            validated_params = self.enhanced_validator.validate_mcp_tool_input(
                params, schema, request_id
            )
            
            # Security validation for string inputs
            for param_name, param_value in validated_params.items():
                if isinstance(param_value, str):
                    validated_params[param_name] = self.security_validator.validate_input_security(
                        param_value, param_name
                    )
                    
            # Tool-specific business rule validation
            if tool_name.startswith("create_account") and user_context:
                self.business_validator.validate_account_creation_rules(
                    validated_params.get("owner_id"),
                    validated_params.get("account_type"),
                    validated_params.get("initial_balance", Decimal("0")),
                    user_context
                )
            elif tool_name in ["deposit_funds", "withdraw_funds", "transfer_funds"]:
                # Additional transaction validation would go here
                pass
                
            return validated_params
            
        except ValidationError:
            raise
        except Exception as e:
            raise ValidationError(
                message=f"Validation failed for {tool_name}: {str(e)}",
                details={"tool_name": tool_name, "original_error": str(e)},
                request_id=request_id
            )


# Convenience functions for backward compatibility
def validate_account_data(data: Dict[str, Any]) -> Dict[str, Any]:
    """Validate account data dictionary."""
    validated = {}
    
    # Validate required fields
    if "ownerId" not in data:
        raise ValidationError("ownerId is required")
    if "accountType" not in data:
        raise ValidationError("accountType is required")
    
    # Validate individual fields
    validated["ownerId"] = AccountValidator.validate_owner_id(data["ownerId"])
    validated["accountType"] = AccountValidator.validate_account_type(data["accountType"])
    
    if "balance" in data:
        validated["balance"] = TransactionValidator.validate_amount(data["balance"])
    
    return validated


def validate_transaction_data(data: Dict[str, Any]) -> Dict[str, Any]:
    """Validate transaction data dictionary."""
    validated = {}
    
    # Validate required fields
    if "accountId" not in data:
        raise ValidationError("accountId is required")
    if "amount" not in data:
        raise ValidationError("amount is required")
    if "transactionType" not in data:
        raise ValidationError("transactionType is required")
    
    # Validate individual fields
    validated["accountId"] = AccountValidator.validate_account_id(data["accountId"])
    validated["amount"] = TransactionValidator.validate_amount(data["amount"])
    validated["transactionType"] = TransactionValidator.validate_transaction_type(data["transactionType"])
    
    if "description" in data:
        validated["description"] = TransactionValidator.validate_description(data["description"])
    
    return validated


def validate_amount(amount: Union[str, float, Decimal]) -> Decimal:
    """Validate amount value."""
    return TransactionValidator.validate_amount(amount)


def validate_account_id(account_id: str) -> str:
    """Validate account ID."""
    if not account_id:
        raise ValidationError("Account ID is required")
    if len(account_id) < 3:
        raise ValidationError("Account ID must be at least 3 characters")
    if len(account_id) > 50:
        raise ValidationError("Account ID cannot exceed 50 characters")
    if not re.match(r'^[A-Za-z0-9\-_]+$', account_id):
        raise ValidationError("Account ID contains invalid characters")
    return account_id


def validate_user_id(user_id: str) -> str:
    """Validate user ID."""
    if not user_id:
        raise ValidationError("User ID is required")
    if len(user_id) < 3:
        raise ValidationError("User ID must be at least 3 characters")
    if not re.match(r'^[A-Za-z0-9\-_]+$', user_id):
        raise ValidationError("User ID contains invalid characters")
    return user_id


def validate_date_range(start_date: str, end_date: str) -> Dict[str, datetime]:
    """Validate date range."""
    try:
        start_dt = datetime.fromisoformat(start_date.replace('Z', '+00:00'))
    except ValueError:
        raise ValidationError("Invalid date format for start_date")
    
    try:
        end_dt = datetime.fromisoformat(end_date.replace('Z', '+00:00'))
    except ValueError:
        raise ValidationError("Invalid date format for end_date")
    
    if start_dt > end_dt:
        raise ValidationError("End date must be after start date")
    
    # Check if range is too large (more than 1 year)
    if (end_dt - start_dt).days > 365:
        raise ValidationError("Date range cannot exceed 1 year")
    
    return {"start_date": start_dt, "end_date": end_dt}


def validate_pagination_params(page: Optional[int], size: Optional[int]) -> Dict[str, int]:
    """Validate pagination parameters."""
    if page is None:
        page = 0
    if size is None:
        size = 20
    
    if page < 0:
        raise ValidationError("Page number must be non-negative")
    if size < 1 or size > 1000:
        raise ValidationError("Page size must be between 1 and 1000")
    
    return {"page": page, "size": size}


def sanitize_input(value: Optional[str]) -> Optional[str]:
    """Sanitize input string."""
    if value is None:
        return None
    
    if not isinstance(value, str):
        return str(value)
    
    # Remove HTML tags
    import html
    sanitized = html.escape(value)
    
    # Remove script tags and other dangerous content
    sanitized = re.sub(r'<script[^>]*>.*?</script>', '', sanitized, flags=re.IGNORECASE | re.DOTALL)
    sanitized = re.sub(r'<[^>]+>', '', sanitized)
    
    # Strip whitespace
    sanitized = sanitized.strip()
    
    return sanitized


# Global validator instance
validator = ComprehensiveValidator()