package com.suhasan.finance.transaction_service.ledger.web;

import java.util.List;

public record LedgerBalanceBatchRequest(List<String> externalAccountIds) {
}
