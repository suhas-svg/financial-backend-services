// package com.suhasan.finance.account_service.controller;

// import com.suhasan.finance.account_service.entity.Account;
// import com.suhasan.finance.account_service.service.AccountService;
// import jakarta.validation.Valid;
// import org.springframework.http.*;
// import org.springframework.web.bind.annotation.*;
// import java.util.List;

// @RestController
// @RequestMapping("/api/accounts")
// public class AccountController {
//     private final AccountService service;
//     public AccountController(AccountService service) {
//         this.service = service;
//     }

//     @GetMapping
//     public List<Account> list() {
//         return service.findAll();
//     }

//     @GetMapping("/{id}")
//     public Account get(@PathVariable Long id) {
//         return service.findById(id);
//     }

//     @PostMapping
//     public ResponseEntity<Account> create(@Valid @RequestBody Account account) {
//         Account created = service.create(account);
//         return ResponseEntity.status(HttpStatus.CREATED).body(created);
//     }

//     @PutMapping("/{id}")
//     public Account update(@PathVariable Long id,
//                           @Valid @RequestBody Account account) {
//         return service.update(id, account);
//     }

//     @DeleteMapping("/{id}")
//     @ResponseStatus(HttpStatus.NO_CONTENT)
//     public void delete(@PathVariable Long id) {
//         service.delete(id);
//     }
// }

package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;

    /**
     * GET /api/accounts
     * Supports paging, sorting, and filtering by ownerId or accountType
     * e.g. /api/accounts?page=0&size=10&ownerId=alice
     */
    @GetMapping
    public ResponseEntity<Page<AccountResponse>> listAccounts(
        @RequestParam(required = false) String ownerId,
        @RequestParam(required = false) String accountType,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<AccountResponse> page = service.listAccounts(ownerId, accountType, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> get(@PathVariable Long id) {
        Account existing = service.findById(id);
        return ResponseEntity.ok(existing);
    }

    @PostMapping
    public ResponseEntity<Account> create(@Valid @RequestBody Account account) {
        Account created = service.create(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Account> update(
        @PathVariable Long id,
        @Valid @RequestBody Account account
    ) {
        Account updated = service.update(id, account);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/test/error")
    public void testError() {
        throw new RuntimeException("boom");
    }

    /**
     * Update account balance (for Transaction Service integration)
     */
    @PutMapping("/{id}/balance")
    public ResponseEntity<Void> updateAccountBalance(
        @PathVariable Long id,
        @Valid @RequestBody BalanceUpdateRequest request
    ) {
        service.updateBalance(id, request.getBalance());
        return ResponseEntity.ok().build();
    }

    // Inner class for balance update requests
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BalanceUpdateRequest {
        @jakarta.validation.constraints.NotNull(message = "Balance is required")
        @jakarta.validation.constraints.PositiveOrZero(message = "Balance must be zero or positive")
        private java.math.BigDecimal balance;
    }

}
