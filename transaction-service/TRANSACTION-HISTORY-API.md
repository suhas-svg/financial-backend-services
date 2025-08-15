# Transaction History and Query API

This document describes the new transaction history and query endpoints implemented as part of Task 12.

## New Endpoints

### 1. Search Transactions with Filters

**Endpoint:** `GET /api/transactions/search`

**Description:** Search transactions with various filters and pagination support.

**Query Parameters:**
- `accountId` (optional): Filter by specific account ID
- `type` (optional): Filter by transaction type (TRANSFER, DEPOSIT, WITHDRAWAL, etc.)
- `status` (optional): Filter by transaction status (COMPLETED, PENDING, FAILED, etc.)
- `startDate` (optional): Filter transactions from this date (ISO format: 2024-01-01T00:00:00)
- `endDate` (optional): Filter transactions until this date (ISO format: 2024-12-31T23:59:59)
- `minAmount` (optional): Filter transactions with amount >= this value
- `maxAmount` (optional): Filter transactions with amount <= this value
- `description` (optional): Filter by description (partial match, case-insensitive)
- `reference` (optional): Filter by exact reference match
- `fromAccountId` (optional): Filter by source account ID
- `toAccountId` (optional): Filter by destination account ID
- Standard pagination parameters: `page`, `size`, `sort`

**Example Request:**
```
GET /api/transactions/search?accountId=account-123&type=TRANSFER&status=COMPLETED&minAmount=100.00&maxAmount=1000.00&page=0&size=20&sort=createdAt,desc
```

**Response:** Paginated list of transactions matching the filters.

### 2. Get Account Transaction Statistics

**Endpoint:** `GET /api/transactions/account/{accountId}/stats`

**Description:** Get comprehensive transaction statistics for a specific account.

**Path Parameters:**
- `accountId`: The account ID to get statistics for

**Query Parameters:**
- `startDate` (optional): Statistics from this date (defaults to 30 days ago)
- `endDate` (optional): Statistics until this date (defaults to now)

**Example Request:**
```
GET /api/transactions/account/account-123/stats?startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59
```

**Response:**
```json
{
  "accountId": "account-123",
  "periodStart": "2024-01-01T00:00:00",
  "periodEnd": "2024-12-31T23:59:59",
  "totalTransactions": 10,
  "completedTransactions": 8,
  "pendingTransactions": 1,
  "failedTransactions": 1,
  "reversedTransactions": 0,
  "totalAmount": 5000.00,
  "totalIncoming": 3000.00,
  "totalOutgoing": 2000.00,
  "totalDeposits": 2000.00,
  "totalWithdrawals": 1000.00,
  "totalTransfers": 2000.00,
  "averageTransactionAmount": 500.00,
  "largestTransaction": 1000.00,
  "smallestTransaction": 50.00,
  "transactionCountsByType": {
    "DEPOSIT": 3,
    "WITHDRAWAL": 2,
    "TRANSFER": 5
  },
  "transactionAmountsByType": {
    "DEPOSIT": 2000.00,
    "WITHDRAWAL": 1000.00,
    "TRANSFER": 2000.00
  },
  "dailyTotal": 500.00,
  "monthlyTotal": 2000.00,
  "dailyCount": 2,
  "monthlyCount": 5,
  "successRate": 80.0,
  "currency": "USD"
}
```

### 3. Get User Transaction Statistics

**Endpoint:** `GET /api/transactions/user/stats`

**Description:** Get comprehensive transaction statistics for the authenticated user across all their accounts.

**Query Parameters:**
- `startDate` (optional): Statistics from this date (defaults to 30 days ago)
- `endDate` (optional): Statistics until this date (defaults to now)

**Example Request:**
```
GET /api/transactions/user/stats?startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59
```

**Response:** Same format as account statistics but aggregated across all user accounts.

## Enhanced Existing Endpoints

### Updated Account Transaction History

**Endpoint:** `GET /api/transactions/account/{accountId}/stats`

This endpoint has been enhanced to provide comprehensive statistics instead of just a placeholder message.

## Features Implemented

### ✅ Transaction History Retrieval by Account
- Enhanced existing `/api/transactions/account/{accountId}` endpoint with pagination
- Added comprehensive filtering capabilities via `/api/transactions/search`

### ✅ Transaction Search and Filtering Capabilities
- Filter by account ID, transaction type, status
- Date range filtering (start/end dates)
- Amount range filtering (min/max amounts)
- Text search in descriptions
- Reference-based filtering
- Support for filtering by source/destination accounts

### ✅ Transaction Statistics Endpoints
- Account-level statistics with comprehensive metrics
- User-level statistics aggregating across accounts
- Transaction counts by type and status
- Amount summaries by type
- Success rate calculations
- Daily and monthly summaries
- Min/max transaction amounts
- Average transaction amounts

### ✅ Pagination for Large Result Sets
- All endpoints support standard Spring Data pagination
- Configurable page size and sorting
- Default sorting by creation date (newest first)

## Database Enhancements

New repository methods were added to support the advanced querying and statistics:

- `findTransactionsWithFilters()` - Advanced filtering with multiple criteria
- Various count and sum methods for statistics calculation
- Optimized queries for performance with large datasets

## Security

All new endpoints maintain the same security model:
- JWT authentication required
- User context automatically applied to filter results
- No unauthorized access to other users' transaction data

## Testing

Comprehensive test coverage has been added:
- Controller tests for all new endpoints
- Service layer tests for business logic
- Integration tests for database queries
- Mock-based testing for external dependencies

## Performance Considerations

- Database indexes are in place for efficient querying
- Pagination prevents large result sets from impacting performance
- Statistics queries are optimized with appropriate aggregations
- Caching can be added for frequently accessed statistics