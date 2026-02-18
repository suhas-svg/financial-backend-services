#!/usr/bin/env python3
"""
Create a test JWT token for testing with the financial services.
"""

import jwt
import time
from datetime import datetime, timedelta

# JWT secret from the services
JWT_SECRET = "AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14="

def create_test_token():
    """Create a test JWT token."""
    now = int(time.time())
    payload = {
        "sub": "admin_test",
        "username": "admin_test",
        "roles": ["admin", "financial_officer"],
        "permissions": [
            "account:create", "account:read", "account:update", "account:delete",
            "transaction:create", "transaction:read", "transaction:reverse",
            "account:balance:update"
        ],
        "iat": now,
        "exp": now + 3600  # 1 hour expiration
    }
    
    token = jwt.encode(payload, JWT_SECRET, algorithm="HS256")
    return token

if __name__ == "__main__":
    token = create_test_token()
    print(f"Test JWT Token:")
    print(token)
    print(f"\nUse this token in Authorization header:")
    print(f"Authorization: Bearer {token}")