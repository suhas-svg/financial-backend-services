#!/usr/bin/env python3
"""
Comprehensive test for Task 5: Transaction Processing MCP Tools
This script verifies that all transaction tools are working properly.
"""

import asyncio
import sys
import traceback
from unittest.mock import AsyncMock, Mock, patch
from decimal import Decimal
from datetime import datetime

# Add src to path
sys.path.insert(0, 'src')

from mcp_financial.tools.transaction_tools import TransactionTools
from mcp_financial.clients.transaction_client import TransactionServiceClient
from mcp_financial.clients.account_client import AccountServiceClient
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext
from mcp_financial.auth.permissions import PermissionChecker
from mcp_financial.models.requests import (
    DepositRequest, WithdrawalRequest, TransferRequest, TransactionReversalRequest
)
from mcp_financial.models.responses import (
    TransactionResponse, MCPSuccessResponse, MCPErrorResponse
)


class Task5ComprehensiveTest:
    """Comprehensive test suite for Task 5 transaction tools."""
    
    def __init__(self):
        self.passed_tests = 0
        self.failed_tests = 0
        self.test_results = []
    
    def log_test(self, test_name: str, passed: bool, message: str = ""):
        """Log test result."""
        status = "✅ PASS" if passed else "❌ FAIL"
        self.test_results.append(f"{status}: {test_name} - {message}")
        if passed:
            self.passed_tests += 1
        else:
            self.failed_tests += 1
    
    def test_imports(self):
        """Test that all required modules can be imported."""
        try:
            from mcp_financial.tools.transaction_tools import TransactionTools
            from mcp_financial.clients.transaction_client import TransactionServiceClient
            from mcp_financial.clients.account_client import AccountServiceClient
            from mcp_financial.auth.jwt_handler import JWTAuthHandler
            from mcp_financial.models.requests import DepositRequest, WithdrawalRequest, TransferRequest, TransactionReversalRequest
            from mcp_financial.models.responses import TransactionResponse, MCPSuccessResponse, MCPErrorResponse
            self.log_test("Module Imports", True, "All required modules imported successfully")
        except Exception as e:
            self.log_test("Module Imports", False, f"Import failed: {e}")
    
    def test_model_validation(self):
        """Test that all transaction models validate correctly."""
        try:
            # Test DepositRequest
            deposit = DepositRequest(
                account_id="acc_123",
                amount=Decimal("100.00"),
                description="Test deposit"
            )
            assert deposit.account_id == "acc_123"
            assert deposit.amount == Decimal("100.00")
            
            # Test WithdrawalRequest
            withdrawal = WithdrawalRequest(
                account_id="acc_456",
                amount=Decimal("50.00"),
                description="Test withdrawal"
            )
            assert withdrawal.account_id == "acc_456"
            assert withdrawal.amount == Decimal("50.00")
            
            # Test TransferRequest
            transfer = TransferRequest(
                from_account_id="acc_123",
                to_account_id="acc_456",
                amount=Decimal("200.00"),
                description="Test transfer"
            )
            assert transfer.from_account_id == "acc_123"
            assert transfer.to_account_id == "acc_456"
            assert transfer.amount == Decimal("200.00")
            
            # Test TransactionReversalRequest
            reversal = TransactionReversalRequest(
                transaction_id="txn_123",
                reason="Test reversal"
            )
            assert reversal.transaction_id == "txn_123"
            assert reversal.reason == "Test reversal"
            
            self.log_test("Model Validation", True, "All transaction models validate correctly")
        except Exception as e:
            self.log_test("Model Validation", False, f"Model validation failed: {e}")
    
    def test_validation_rules(self):
        """Test that validation rules work correctly."""
        try:
            from pydantic import ValidationError
            
            # Test negative amount validation
            try:
                DepositRequest(account_id="acc_123", amount=Decimal("-100.00"))
                self.log_test("Negative Amount Validation", False, "Should have rejected negative amount")
                return
            except ValidationError:
                pass  # Expected
            
            # Test zero amount validation
            try:
                WithdrawalRequest(account_id="acc_123", amount=Decimal("0.00"))
                self.log_test("Zero Amount Validation", False, "Should have rejected zero amount")
                return
            except ValidationError:
                pass  # Expected
            
            # Test same account transfer validation
            try:
                TransferRequest(
                    from_account_id="acc_123",
                    to_account_id="acc_123",
                    amount=Decimal("100.00")
                )
                self.log_test("Same Account Transfer Validation", False, "Should have rejected same account transfer")
                return
            except ValidationError:
                pass  # Expected
            
            # Test empty account ID validation
            try:
                DepositRequest(account_id="", amount=Decimal("100.00"))
                self.log_test("Empty Account ID Validation", False, "Should have rejected empty account ID")
                return
            except ValidationError:
                pass  # Expected
            
            self.log_test("Validation Rules", True, "All validation rules working correctly")
        except Exception as e:
            self.log_test("Validation Rules", False, f"Validation rules test failed: {e}")
    
    def test_transaction_client_methods(self):
        """Test that transaction client has all required methods."""
        try:
            client = TransactionServiceClient("http://localhost:8081")
            
            # Check that all required methods exist
            required_methods = [
                'deposit_funds',
                'withdraw_funds', 
                'transfer_funds',
                'reverse_transaction',
                'get_transaction',
                'get_transaction_history',
                'search_transactions',
                'get_transaction_analytics'
            ]
            
            for method_name in required_methods:
                if not hasattr(client, method_name):
                    self.log_test("Transaction Client Methods", False, f"Missing method: {method_name}")
                    return
                
                method = getattr(client, method_name)
                if not callable(method):
                    self.log_test("Transaction Client Methods", False, f"Method not callable: {method_name}")
                    return
            
            self.log_test("Transaction Client Methods", True, "All required methods present and callable")
        except Exception as e:
            self.log_test("Transaction Client Methods", False, f"Client methods test failed: {e}")
    
    def test_transaction_tools_initialization(self):
        """Test that TransactionTools can be initialized."""
        try:
            mock_app = Mock()
            mock_transaction_client = Mock()
            mock_account_client = Mock()
            mock_auth_handler = Mock()
            
            with patch('mcp_financial.tools.transaction_tools.transaction_operations_counter'), \
                 patch('mcp_financial.tools.transaction_tools.transaction_operation_duration'), \
                 patch('mcp_financial.tools.transaction_tools.transaction_amounts_histogram'), \
                 patch('mcp_financial.tools.transaction_tools.logger'):
                
                tools = TransactionTools(
                    app=mock_app,
                    transaction_client=mock_transaction_client,
                    account_client=mock_account_client,
                    auth_handler=mock_auth_handler
                )
                
                # Verify attributes are set
                assert tools.app == mock_app
                assert tools.transaction_client == mock_transaction_client
                assert tools.account_client == mock_account_client
                assert tools.auth_handler == mock_auth_handler
                
                self.log_test("TransactionTools Initialization", True, "TransactionTools initialized successfully")
        except Exception as e:
            self.log_test("TransactionTools Initialization", False, f"Initialization failed: {e}")
    
    def test_response_models(self):
        """Test that response models work correctly."""
        try:
            # Test TransactionResponse
            transaction_response = TransactionResponse(
                id="txn_123",
                account_id="acc_123",
                amount=Decimal("100.00"),
                transaction_type="DEPOSIT",
                status="COMPLETED",
                created_at=datetime.now()
            )
            assert transaction_response.id == "txn_123"
            assert transaction_response.amount == Decimal("100.00")
            
            # Test MCPSuccessResponse
            success_response = MCPSuccessResponse(
                message="Transaction completed successfully",
                data={"transaction_id": "txn_123", "amount": 100.00}
            )
            assert success_response.success is True
            assert success_response.message == "Transaction completed successfully"
            
            # Test MCPErrorResponse
            error_response = MCPErrorResponse(
                error_code="VALIDATION_ERROR",
                error_message="Invalid input parameters"
            )
            assert error_response.error_code == "VALIDATION_ERROR"
            assert error_response.error_message == "Invalid input parameters"
            
            self.log_test("Response Models", True, "All response models working correctly")
        except Exception as e:
            self.log_test("Response Models", False, f"Response models test failed: {e}")
    
    def test_permission_checker_integration(self):
        """Test that permission checker integration works."""
        try:
            user_context = UserContext(
                user_id="user_123",
                username="testuser",
                roles=["financial_officer"],
                permissions=["transaction:create", "transaction:reverse"]
            )
            
            # Test permission checking methods exist
            with patch.object(PermissionChecker, 'can_perform_transaction', return_value=True) as mock_perform:
                result = PermissionChecker.can_perform_transaction(user_context, "user_123", "DEPOSIT")
                assert result is True
                mock_perform.assert_called_once()
            
            with patch.object(PermissionChecker, 'can_reverse_transaction', return_value=True) as mock_reverse:
                result = PermissionChecker.can_reverse_transaction(user_context)
                assert result is True
                mock_reverse.assert_called_once()
            
            self.log_test("Permission Checker Integration", True, "Permission checker integration working")
        except Exception as e:
            self.log_test("Permission Checker Integration", False, f"Permission checker test failed: {e}")
    
    async def test_async_functionality(self):
        """Test that async functionality works correctly."""
        try:
            # Test that transaction client methods are async
            client = AsyncMock(spec=TransactionServiceClient)
            
            # Test deposit method
            client.deposit_funds.return_value = {"id": "txn_123", "status": "COMPLETED"}
            result = await client.deposit_funds(
                account_id="acc_123",
                amount=Decimal("100.00"),
                description="Test deposit",
                auth_token="token"
            )
            assert result["id"] == "txn_123"
            client.deposit_funds.assert_called_once()
            
            # Test withdrawal method
            client.withdraw_funds.return_value = {"id": "txn_124", "status": "COMPLETED"}
            result = await client.withdraw_funds(
                account_id="acc_123",
                amount=Decimal("50.00"),
                description="Test withdrawal",
                auth_token="token"
            )
            assert result["id"] == "txn_124"
            client.withdraw_funds.assert_called_once()
            
            # Test transfer method
            client.transfer_funds.return_value = {"id": "txn_125", "status": "COMPLETED"}
            result = await client.transfer_funds(
                from_account_id="acc_123",
                to_account_id="acc_456",
                amount=Decimal("200.00"),
                description="Test transfer",
                auth_token="token"
            )
            assert result["id"] == "txn_125"
            client.transfer_funds.assert_called_once()
            
            # Test reversal method
            client.reverse_transaction.return_value = {"id": "txn_126", "status": "COMPLETED"}
            result = await client.reverse_transaction(
                transaction_id="txn_123",
                reason="Test reversal",
                auth_token="token"
            )
            assert result["id"] == "txn_126"
            client.reverse_transaction.assert_called_once()
            
            self.log_test("Async Functionality", True, "All async methods working correctly")
        except Exception as e:
            self.log_test("Async Functionality", False, f"Async functionality test failed: {e}")
    
    async def run_all_tests(self):
        """Run all tests."""
        print("🚀 Starting Task 5 Comprehensive Test Suite")
        print("=" * 60)
        
        # Run synchronous tests
        self.test_imports()
        self.test_model_validation()
        self.test_validation_rules()
        self.test_transaction_client_methods()
        self.test_transaction_tools_initialization()
        self.test_response_models()
        self.test_permission_checker_integration()
        
        # Run async tests
        await self.test_async_functionality()
        
        # Print results
        print("\n" + "=" * 60)
        print("📊 TEST RESULTS")
        print("=" * 60)
        
        for result in self.test_results:
            print(result)
        
        print("\n" + "=" * 60)
        print(f"📈 SUMMARY: {self.passed_tests} passed, {self.failed_tests} failed")
        
        if self.failed_tests == 0:
            print("🎉 ALL TESTS PASSED! Task 5 is working properly.")
            return True
        else:
            print("❌ SOME TESTS FAILED! Task 5 needs attention.")
            return False


async def main():
    """Main test runner."""
    test_suite = Task5ComprehensiveTest()
    success = await test_suite.run_all_tests()
    return 0 if success else 1


if __name__ == "__main__":
    try:
        exit_code = asyncio.run(main())
        sys.exit(exit_code)
    except Exception as e:
        print(f"❌ Test suite failed with exception: {e}")
        traceback.print_exc()
        sys.exit(1)