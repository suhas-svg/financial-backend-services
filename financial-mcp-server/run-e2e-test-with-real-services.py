#!/usr/bin/env python3
"""
End-to-End Testing with Real Financial Services
Tests the MCP Financial Server with actual Account and Transaction services.
"""

import asyncio
import json
import sys
import time
import httpx
import jwt
from datetime import datetime, timedelta
from pathlib import Path
import logging
from typing import Dict, Any, List, Optional

# Add src directory to Python path
sys.path.insert(0, str(Path(__file__).parent / "src"))

from mcp_financial.server import FinancialMCPServer
from mcp_financial.auth.jwt_handler import JWTAuthHandler
from mcp_financial.clients.account_client import AccountServiceClient
from mcp_financial.clients.transaction_client import TransactionServiceClient

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class RealServiceE2ETest:
    """End-to-end testing with real financial services."""
    
    def __init__(self):
        self.account_service_url = "http://localhost:8083"
        self.transaction_service_url = "http://localhost:8082"
        self.jwt_secret = "AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14="
        
        # Initialize clients
        self.account_client = AccountServiceClient(self.account_service_url)
        self.transaction_client = TransactionServiceClient(self.transaction_service_url)
        self.jwt_handler = JWTAuthHandler(self.jwt_secret)
        
        # Test results
        self.test_results = []
        self.created_accounts = []
        self.created_transactions = []
    
    async def check_service_health(self) -> bool:
        """Check if both services are healthy."""
        logger.info("🔍 Checking service health...")
        
        try:
            # Check Account Service
            async with httpx.AsyncClient() as client:
                account_health = await client.get(f"{self.account_service_url}/actuator/health")
                if account_health.status_code != 200:
                    logger.error(f"❌ Account Service unhealthy: {account_health.status_code}")
                    return False
                logger.info("✅ Account Service is healthy")
                
                # Check Transaction Service
                transaction_health = await client.get(f"{self.transaction_service_url}/actuator/health")
                if transaction_health.status_code != 200:
                    logger.error(f"❌ Transaction Service unhealthy: {transaction_health.status_code}")
                    return False
                logger.info("✅ Transaction Service is healthy")
                
            return True
            
        except Exception as e:
            logger.error(f"❌ Service health check failed: {e}")
            return False
    
    def create_test_jwt_token(self, user_id: str, username: str, roles: List[str], permissions: List[str]) -> str:
        """Create a test JWT token."""
        return self.jwt_handler.create_token(
            user_id=user_id,
            username=username,
            roles=roles,
            permissions=permissions,
            expires_in=3600
        )
    
    async def test_direct_service_integration(self) -> bool:
        """Test direct integration with services using HTTP clients."""
        logger.info("🧪 Testing direct service integration...")
        
        try:
            # Create admin token
            admin_token = self.create_test_jwt_token(
                user_id="admin_e2e",
                username="admin_test",
                roles=["admin"],
                permissions=["account:create", "account:read", "transaction:create", "transaction:read"]
            )
            
            # Test 1: Create account via Account Service
            logger.info("📝 Creating test account...")
            account_data = {
                "ownerId": "e2e_test_user",
                "accountType": "CHECKING",
                "balance": 0.0
            }
            
            account = await self.account_client.create_account(account_data, f"Bearer {admin_token}")
            account_id = account["id"]
            self.created_accounts.append(account_id)
            
            logger.info(f"✅ Account created: {account_id}")
            
            # Test 2: Get account details
            logger.info("📖 Retrieving account details...")
            retrieved_account = await self.account_client.get_account(account_id, f"Bearer {admin_token}")
            
            assert retrieved_account["id"] == account_id
            assert retrieved_account["ownerId"] == "e2e_test_user"
            assert retrieved_account["accountType"] == "CHECKING"
            
            logger.info("✅ Account retrieval successful")
            
            # Test 3: Make a deposit via Transaction Service
            logger.info("💰 Making deposit transaction...")
            deposit_result = await self.transaction_client.deposit_funds(
                account_id, 1000.0, "E2E test deposit", f"Bearer {admin_token}"
            )
            
            deposit_id = deposit_result["id"]
            self.created_transactions.append(deposit_id)
            
            assert deposit_result["accountId"] == account_id
            assert deposit_result["amount"] == 1000.0
            assert deposit_result["transactionType"] == "DEPOSIT"
            
            logger.info(f"✅ Deposit successful: {deposit_id}")
            
            # Test 4: Check updated balance
            logger.info("💳 Checking account balance...")
            balance = await self.account_client.get_account_balance(account_id, f"Bearer {admin_token}")
            
            assert balance["balance"] == 1000.0
            logger.info(f"✅ Balance verified: ${balance['balance']}")
            
            # Test 5: Make a withdrawal
            logger.info("💸 Making withdrawal transaction...")
            withdrawal_result = await self.transaction_client.withdraw_funds(
                account_id, 250.0, "E2E test withdrawal", f"Bearer {admin_token}"
            )
            
            withdrawal_id = withdrawal_result["id"]
            self.created_transactions.append(withdrawal_id)
            
            assert withdrawal_result["accountId"] == account_id
            assert withdrawal_result["amount"] == 250.0
            assert withdrawal_result["transactionType"] == "WITHDRAWAL"
            
            logger.info(f"✅ Withdrawal successful: {withdrawal_id}")
            
            # Test 6: Get transaction history
            logger.info("📊 Retrieving transaction history...")
            history = await self.transaction_client.get_transaction_history(
                account_id, page=0, size=10, auth_token=f"Bearer {admin_token}"
            )
            
            assert "content" in history
            assert len(history["content"]) >= 2  # At least deposit and withdrawal
            
            logger.info(f"✅ Transaction history retrieved: {len(history['content'])} transactions")
            
            return True
            
        except Exception as e:
            logger.error(f"❌ Direct service integration test failed: {e}")
            return False
    
    async def test_mcp_server_integration(self) -> bool:
        """Test MCP server integration with real services."""
        logger.info("🚀 Testing MCP server integration...")
        
        try:
            # Initialize MCP server with real service URLs
            from unittest.mock import patch, MagicMock
            
            with patch('mcp_financial.server.Settings') as mock_settings_class:
                mock_settings = MagicMock()
                mock_settings.account_service_url = self.account_service_url
                mock_settings.transaction_service_url = self.transaction_service_url
                mock_settings.jwt_secret = self.jwt_secret
                mock_settings.server_timeout = 5000
                mock_settings_class.return_value = mock_settings
                
                # Create MCP server
                mcp_server = FinancialMCPServer()
                
                # Create test token
                customer_token = self.create_test_jwt_token(
                    user_id="customer_e2e",
                    username="customer_test",
                    roles=["customer"],
                    permissions=["account:create", "account:read", "transaction:create", "transaction:read"]
                )
                
                # Test 1: Create account via MCP
                logger.info("📝 Creating account via MCP...")
                
                # Mock authentication for MCP tools
                from mcp_financial.auth.jwt_handler import UserContext
                user_context = UserContext(
                    user_id="customer_e2e",
                    username="customer_test",
                    roles=["customer"],
                    permissions=["account:create", "account:read", "transaction:create", "transaction:read"]
                )
                
                with patch.object(mcp_server.auth_handler, 'extract_user_context') as mock_auth:
                    mock_auth.return_value = user_context
                    
                    # Mock permission checks
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True), \
                         patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True), \
                         patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                        
                        # Create account
                        create_result = await mcp_server.account_tools.create_account(
                            "mcp_test_user", "SAVINGS", 500.0, f"Bearer {customer_token}"
                        )
                        
                        create_data = json.loads(create_result[0].text)
                        assert create_data["success"] is True
                        
                        mcp_account_id = create_data["data"]["id"]
                        self.created_accounts.append(mcp_account_id)
                        
                        logger.info(f"✅ MCP account created: {mcp_account_id}")
                        
                        # Test 2: Get account via MCP
                        logger.info("📖 Retrieving account via MCP...")
                        get_result = await mcp_server.account_tools.get_account(
                            mcp_account_id, f"Bearer {customer_token}"
                        )
                        
                        get_data = json.loads(get_result[0].text)
                        assert get_data["success"] is True
                        assert get_data["data"]["id"] == mcp_account_id
                        
                        logger.info("✅ MCP account retrieval successful")
                        
                        # Test 3: Make deposit via MCP
                        logger.info("💰 Making deposit via MCP...")
                        deposit_result = await mcp_server.transaction_tools.deposit_funds(
                            mcp_account_id, 750.0, "MCP test deposit", f"Bearer {customer_token}"
                        )
                        
                        deposit_data = json.loads(deposit_result[0].text)
                        assert deposit_data["success"] is True
                        
                        mcp_deposit_id = deposit_data["data"]["id"]
                        self.created_transactions.append(mcp_deposit_id)
                        
                        logger.info(f"✅ MCP deposit successful: {mcp_deposit_id}")
                        
                        # Test 4: Check balance via MCP
                        logger.info("💳 Checking balance via MCP...")
                        balance_result = await mcp_server.account_tools.get_account_balance(
                            mcp_account_id, f"Bearer {customer_token}"
                        )
                        
                        balance_data = json.loads(balance_result[0].text)
                        assert balance_data["success"] is True
                        # Balance should be initial 500 + deposit 750 = 1250
                        assert balance_data["data"]["balance"] == 1250.0
                        
                        logger.info(f"✅ MCP balance verified: ${balance_data['data']['balance']}")
                        
                        # Test 5: Get transaction history via MCP
                        logger.info("📊 Retrieving transaction history via MCP...")
                        with patch('mcp_financial.tools.query_tools.PermissionChecker.can_access_account', return_value=True):
                            history_result = await mcp_server.query_tools.get_transaction_history(
                                mcp_account_id, 0, 10, None, None, f"Bearer {customer_token}"
                            )
                            
                            history_data = json.loads(history_result[0].text)
                            assert history_data["success"] is True
                            assert len(history_data["data"]["content"]) >= 1  # At least the deposit
                            
                            logger.info(f"✅ MCP transaction history retrieved: {len(history_data['data']['content'])} transactions")
            
            return True
            
        except Exception as e:
            logger.error(f"❌ MCP server integration test failed: {e}")
            import traceback
            logger.error(f"Traceback: {traceback.format_exc()}")
            return False
    
    async def test_cross_service_operations(self) -> bool:
        """Test operations that span multiple services."""
        logger.info("🔄 Testing cross-service operations...")
        
        try:
            # Create admin token
            admin_token = self.create_test_jwt_token(
                user_id="admin_cross",
                username="admin_cross_test",
                roles=["admin"],
                permissions=["account:create", "account:read", "transaction:create", "transaction:read"]
            )
            
            # Create two accounts for transfer testing
            logger.info("📝 Creating source account...")
            source_account_data = {
                "ownerId": "cross_test_user_1",
                "accountType": "CHECKING",
                "balance": 2000.0
            }
            source_account = await self.account_client.create_account(source_account_data, f"Bearer {admin_token}")
            source_account_id = source_account["id"]
            self.created_accounts.append(source_account_id)
            
            logger.info("📝 Creating destination account...")
            dest_account_data = {
                "ownerId": "cross_test_user_2",
                "accountType": "SAVINGS",
                "balance": 500.0
            }
            dest_account = await self.account_client.create_account(dest_account_data, f"Bearer {admin_token}")
            dest_account_id = dest_account["id"]
            self.created_accounts.append(dest_account_id)
            
            # Test transfer between accounts
            logger.info("💸 Testing transfer between accounts...")
            transfer_result = await self.transaction_client.transfer_funds(
                source_account_id, dest_account_id, 300.0, "Cross-service transfer test", f"Bearer {admin_token}"
            )
            
            transfer_id = transfer_result["id"]
            self.created_transactions.append(transfer_id)
            
            assert transfer_result["fromAccountId"] == source_account_id
            assert transfer_result["toAccountId"] == dest_account_id
            assert transfer_result["amount"] == 300.0
            
            logger.info(f"✅ Transfer successful: {transfer_id}")
            
            # Verify balances after transfer
            logger.info("💳 Verifying balances after transfer...")
            source_balance = await self.account_client.get_account_balance(source_account_id, f"Bearer {admin_token}")
            dest_balance = await self.account_client.get_account_balance(dest_account_id, f"Bearer {admin_token}")
            
            # Source should have 2000 - 300 = 1700
            assert source_balance["balance"] == 1700.0
            # Destination should have 500 + 300 = 800
            assert dest_balance["balance"] == 800.0
            
            logger.info(f"✅ Balances verified - Source: ${source_balance['balance']}, Dest: ${dest_balance['balance']}")
            
            return True
            
        except Exception as e:
            logger.error(f"❌ Cross-service operations test failed: {e}")
            return False
    
    async def test_error_scenarios(self) -> bool:
        """Test error handling scenarios."""
        logger.info("⚠️ Testing error scenarios...")
        
        try:
            # Create test token
            test_token = self.create_test_jwt_token(
                user_id="error_test",
                username="error_test",
                roles=["customer"],
                permissions=["account:read", "transaction:read"]
            )
            
            # Test 1: Try to access non-existent account
            logger.info("🔍 Testing non-existent account access...")
            try:
                await self.account_client.get_account("non_existent_account", f"Bearer {test_token}")
                logger.error("❌ Should have failed for non-existent account")
                return False
            except Exception as e:
                logger.info(f"✅ Correctly failed for non-existent account: {type(e).__name__}")
            
            # Test 2: Try to make transaction with insufficient funds
            if self.created_accounts:
                logger.info("💸 Testing insufficient funds scenario...")
                try:
                    # Try to withdraw more than available
                    await self.transaction_client.withdraw_funds(
                        self.created_accounts[0], 999999.0, "Insufficient funds test", f"Bearer {test_token}"
                    )
                    logger.error("❌ Should have failed for insufficient funds")
                    return False
                except Exception as e:
                    logger.info(f"✅ Correctly failed for insufficient funds: {type(e).__name__}")
            
            # Test 3: Try with invalid JWT token
            logger.info("🔐 Testing invalid JWT token...")
            try:
                await self.account_client.get_account("any_account", "Bearer invalid_token")
                logger.error("❌ Should have failed for invalid token")
                return False
            except Exception as e:
                logger.info(f"✅ Correctly failed for invalid token: {type(e).__name__}")
            
            return True
            
        except Exception as e:
            logger.error(f"❌ Error scenarios test failed: {e}")
            return False
    
    async def cleanup_test_data(self):
        """Clean up test data created during testing."""
        logger.info("🧹 Cleaning up test data...")
        
        try:
            # Create admin token for cleanup
            admin_token = self.create_test_jwt_token(
                user_id="admin_cleanup",
                username="admin_cleanup",
                roles=["admin"],
                permissions=["account:delete", "transaction:reverse"]
            )
            
            # Note: In a real scenario, you might want to clean up test data
            # For now, we'll just log what was created
            logger.info(f"📊 Test created {len(self.created_accounts)} accounts and {len(self.created_transactions)} transactions")
            logger.info("ℹ️ Test data cleanup would be implemented based on service capabilities")
            
        except Exception as e:
            logger.warning(f"⚠️ Cleanup failed: {e}")
    
    async def run_all_tests(self) -> Dict[str, Any]:
        """Run all end-to-end tests."""
        logger.info("🚀 Starting comprehensive end-to-end testing with real services")
        
        start_time = time.time()
        test_results = {
            "timestamp": datetime.utcnow().isoformat(),
            "tests": [],
            "summary": {
                "total_tests": 0,
                "passed_tests": 0,
                "failed_tests": 0,
                "success_rate": 0.0,
                "duration": 0.0
            }
        }
        
        # Define test cases
        test_cases = [
            ("Service Health Check", self.check_service_health),
            ("Direct Service Integration", self.test_direct_service_integration),
            ("MCP Server Integration", self.test_mcp_server_integration),
            ("Cross-Service Operations", self.test_cross_service_operations),
            ("Error Scenarios", self.test_error_scenarios)
        ]
        
        # Run each test case
        for test_name, test_func in test_cases:
            logger.info(f"\n{'='*60}")
            logger.info(f"🧪 Running: {test_name}")
            logger.info(f"{'='*60}")
            
            test_start = time.time()
            
            try:
                result = await test_func()
                test_duration = time.time() - test_start
                
                test_result = {
                    "name": test_name,
                    "status": "PASSED" if result else "FAILED",
                    "duration": test_duration,
                    "timestamp": datetime.utcnow().isoformat()
                }
                
                if result:
                    logger.info(f"✅ {test_name}: PASSED ({test_duration:.2f}s)")
                    test_results["summary"]["passed_tests"] += 1
                else:
                    logger.error(f"❌ {test_name}: FAILED ({test_duration:.2f}s)")
                    test_results["summary"]["failed_tests"] += 1
                
            except Exception as e:
                test_duration = time.time() - test_start
                logger.error(f"💥 {test_name}: ERROR - {e}")
                
                test_result = {
                    "name": test_name,
                    "status": "ERROR",
                    "duration": test_duration,
                    "error": str(e),
                    "timestamp": datetime.utcnow().isoformat()
                }
                
                test_results["summary"]["failed_tests"] += 1
            
            test_results["tests"].append(test_result)
            test_results["summary"]["total_tests"] += 1
        
        # Calculate summary
        total_duration = time.time() - start_time
        test_results["summary"]["duration"] = total_duration
        
        if test_results["summary"]["total_tests"] > 0:
            test_results["summary"]["success_rate"] = (
                test_results["summary"]["passed_tests"] / test_results["summary"]["total_tests"] * 100
            )
        
        # Cleanup
        await self.cleanup_test_data()
        
        return test_results
    
    def print_summary(self, results: Dict[str, Any]):
        """Print test summary."""
        summary = results["summary"]
        
        print(f"\n{'='*80}")
        print("  END-TO-END TESTING SUMMARY")
        print(f"{'='*80}")
        
        print(f"📊 Test Results:")
        print(f"   Total Tests: {summary['total_tests']}")
        print(f"   Passed: {summary['passed_tests']}")
        print(f"   Failed: {summary['failed_tests']}")
        print(f"   Success Rate: {summary['success_rate']:.1f}%")
        print(f"   Total Duration: {summary['duration']:.2f}s")
        
        print(f"\n📋 Individual Test Results:")
        for test in results["tests"]:
            status_emoji = {"PASSED": "✅", "FAILED": "❌", "ERROR": "💥"}.get(test["status"], "❓")
            print(f"   {status_emoji} {test['name']}: {test['status']} ({test['duration']:.2f}s)")
        
        if summary["failed_tests"] == 0:
            print(f"\n🎉 All end-to-end tests passed!")
            print(f"   The MCP Financial Server is working correctly with real services.")
        else:
            print(f"\n⚠️ Some tests failed.")
            print(f"   Please review the test results and fix issues.")
        
        print(f"\n📈 Test Data Created:")
        print(f"   Accounts: {len(self.created_accounts)}")
        print(f"   Transactions: {len(self.created_transactions)}")


async def main():
    """Main execution function."""
    tester = RealServiceE2ETest()
    
    try:
        # Run all tests
        results = await tester.run_all_tests()
        
        # Print summary
        tester.print_summary(results)
        
        # Save results to file
        results_file = Path("e2e-test-results.json")
        with open(results_file, 'w') as f:
            json.dump(results, f, indent=2)
        
        logger.info(f"📄 Results saved to: {results_file}")
        
        # Exit with appropriate code
        exit_code = 0 if results["summary"]["failed_tests"] == 0 else 1
        sys.exit(exit_code)
        
    except KeyboardInterrupt:
        logger.error("❌ Testing interrupted by user")
        sys.exit(1)
    except Exception as e:
        logger.error(f"❌ Testing failed with error: {e}")
        import traceback
        logger.error(f"Traceback: {traceback.format_exc()}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())