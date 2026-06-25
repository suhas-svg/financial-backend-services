package com.suhasan.finance.transaction_service.ledger.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
public class CustomerLedgerController {

    private final CustomerLedgerQueryService customerLedgerQueryService;

    public CustomerLedgerController(CustomerLedgerQueryService customerLedgerQueryService) {
        this.customerLedgerQueryService = customerLedgerQueryService;
    }

    @GetMapping("/accounts")
    public List<LedgerAccountSummaryResponse> listAccounts(Authentication authentication) {
        return customerLedgerQueryService.listAccounts(authentication.getName());
    }

    @GetMapping("/accounts/{externalAccountId}/balance")
    public LedgerAccountSummaryResponse getBalance(
            @PathVariable String externalAccountId,
            Authentication authentication) {
        return customerLedgerQueryService.getBalance(authentication.getName(), externalAccountId);
    }

    @PostMapping("/accounts/balances:batch")
    public List<LedgerAccountSummaryResponse> getBalances(
            @RequestBody LedgerBalanceBatchRequest request,
            Authentication authentication) {
        return customerLedgerQueryService.getBalances(authentication.getName(), request.externalAccountIds());
    }

    @GetMapping("/journals/{journalId}")
    public CustomerJournalResponse getJournal(
            @PathVariable UUID journalId,
            Authentication authentication) {
        return customerLedgerQueryService.getJournal(authentication.getName(), journalId);
    }

    @ExceptionHandler(LedgerAccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void forbidden() {
    }
}
