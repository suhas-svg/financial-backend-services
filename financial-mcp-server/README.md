# MCP Financial Server

A Model Context Protocol (MCP) server that provides AI agents and external systems with secure, controlled access to financial operations through a standardized protocol interface.

## Overview

The MCP Financial Server acts as an intelligent middleware layer that exposes financial operations as MCP tools, enabling AI-powered financial assistants, automated compliance systems, and third-party integrations to interact with financial services securely.

## Features

- **MCP Protocol Compliance**: Full implementation of the Model Context Protocol specification
- **JWT Authentication**: Compatible with existing financial services authentication
- **Role-Based Access Control**: Granular permissions for different user types
- **Circuit Breaker Pattern**: Resilient service integration with automatic failover
- **Comprehensive Monitoring**: Prometheus metrics and structured logging
- **Input Validation**: Robust validation for all financial operations
- **Audit Logging**: Complete audit trail for compliance requirements

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   AI Agents     │    │   Kiro IDE      │    │ External Apps   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │   MCP Financial Server    │
                    │   (Python + FastMCP)      │
                    └─────────────┬─────────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                      │
┌─────────┴───────┐    ┌─────────┴───────┐    ┌─────────┴───────┐
│ Account Service │    │Transaction Svc  │    │   Monitoring    │
│    (Port 8080)  │    │   (Port 8081)   │    │   & Metrics     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Quick Start

### Prerequisites

- Python 3.9+
- Account Service running on port 8080
- Transaction Service running on port 8081
- PostgreSQL database

### Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd financial-mcp-server
```

2. Create virtual environment:
```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

3. Install dependencies:
```bash
pip install -r requirements.txt
```

4. Configure environment:
```bash
cp .env.example .env
# Edit .env with your configuration
```

5. Run the server:
```bash
python src/main.py
```

### Docker Deployment

1. Build and run with Docker Compose:
```bash
docker-compose up -d
```

2. Check service health:
```bash
curl http://localhost:8082/health
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MCP_HOST` | Server host | `localhost` |
| `MCP_PORT` | Server port | `8082` |
| `ACCOUNT_SERVICE_URL` | Account service URL | `http://localhost:8080` |
| `TRANSACTION_SERVICE_URL` | Transaction service URL | `http://localhost:8081` |
| `JWT_SECRET` | JWT signing secret | `your-secret-key` |
| `LOG_LEVEL` | Logging level | `INFO` |
| `METRICS_ENABLED` | Enable metrics collection | `true` |

### Service Configuration

The server automatically configures:
- HTTP clients with retry logic and circuit breakers
- Prometheus metrics collection
- Structured JSON logging
- JWT authentication middleware

## Available MCP Tools

### Account Management
- `create_account` - Create new financial account
- `get_account` - Retrieve account details
- `update_account` - Modify account information
- `delete_account` - Close account
- `get_account_balance` - Get current balance
- `update_account_balance` - Modify account balance

### Transaction Processing
- `deposit_funds` - Make deposit to account
- `withdraw_funds` - Withdraw from account
- `transfer_funds` - Transfer between accounts
- `reverse_transaction` - Reverse a transaction

### Data Queries
- `get_transaction_history` - Get paginated transaction history
- `search_transactions` - Search transactions with filters
- `get_account_analytics` - Get account analytics and metrics

### System Monitoring
- `health_check` - Check system health
- `get_metrics` - Get performance metrics
- `get_service_status` - Get backend service status

## Authentication & Authorization

### JWT Token Format

The server expects JWT tokens with the following claims:
```json
{
  "sub": "user_id",
  "username": "username",
  "roles": ["customer", "account_manager"],
  "permissions": ["account:read", "transaction:create"],
  "exp": 1234567890
}
```

### Role-Based Permissions

| Role | Permissions |
|------|-------------|
| `admin` | All operations |
| `financial_officer` | Account and transaction management |
| `account_manager` | Account operations and queries |
| `customer_service` | Read-only access |
| `customer` | Own account access only |

## Monitoring & Observability

### Metrics

Prometheus metrics are available at `http://localhost:9090/metrics`:

- `mcp_requests_total` - Total MCP requests by tool and status
- `mcp_request_duration_seconds` - Request duration histogram
- `service_requests_total` - Backend service requests
- `circuit_breaker_state` - Circuit breaker states
- `auth_requests_total` - Authentication requests

### Logging

Structured JSON logs include:
- Request/response details
- Authentication events
- Service interactions
- Error conditions
- Performance metrics

### Health Checks

Health endpoint: `http://localhost:8082/health`

Returns service status and backend connectivity.

## Development

### Running Tests

```bash
# Unit tests
pytest tests/unit/

# Integration tests
pytest tests/integration/

# All tests with coverage
pytest --cov=src tests/
```

### Code Quality

```bash
# Format code
black src/ tests/

# Sort imports
isort src/ tests/

# Lint code
flake8 src/ tests/

# Type checking
mypy src/
```

### Project Structure

```
financial-mcp-server/
├── src/
│   ├── mcp_financial/
│   │   ├── auth/           # Authentication & authorization
│   │   ├── clients/        # HTTP clients for backend services
│   │   ├── config/         # Configuration management
│   │   ├── models/         # Data models
│   │   ├── tools/          # MCP tool implementations
│   │   ├── utils/          # Utilities (logging, metrics, validation)
│   │   └── server.py       # Main server implementation
│   └── main.py             # Application entry point
├── tests/                  # Test suite
├── requirements.txt        # Python dependencies
├── pyproject.toml         # Project configuration
├── Dockerfile             # Container configuration
└── docker-compose.yml     # Multi-service deployment
```

## Security Considerations

- All requests require valid JWT authentication
- Role-based access control for all operations
- Input validation and sanitization
- Rate limiting to prevent abuse
- Audit logging for compliance
- Circuit breakers for service resilience

## Troubleshooting

### Common Issues

1. **Connection refused to backend services**
   - Verify Account Service is running on port 8080
   - Verify Transaction Service is running on port 8081
   - Check network connectivity

2. **Authentication failures**
   - Verify JWT_SECRET matches across services
   - Check token expiration
   - Validate token format

3. **Circuit breaker activation**
   - Check backend service health
   - Review error logs
   - Adjust circuit breaker thresholds if needed

### Debug Mode

Enable debug logging:
```bash
export LOG_LEVEL=DEBUG
python src/main.py
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Run quality checks
5. Submit a pull request

## License

MIT License - see LICENSE file for details.