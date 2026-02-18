package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.service.AccountService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Dependencies are injected and managed by Spring"
)
public class AccountController {

    private final AccountService service;

    @GetMapping
    public ResponseEntity<Page<AccountResponse>> listAccounts(
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String accountType,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Authentication authentication
    ) {
        String effectiveOwnerId = ownerId;
        if (!isAdmin(authentication) && !isInternalService(authentication)) {
            effectiveOwnerId = authentication.getName();
        }
        Page<AccountResponse> page = service.listAccounts(effectiveOwnerId, accountType, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> get(@PathVariable Long id, Authentication authentication) {
        Account existing = service.findById(id);
        assertOwnerOrPrivileged(existing, authentication);
        return ResponseEntity.ok(existing);
    }

    @PostMapping
    public ResponseEntity<Account> create(@Valid @RequestBody Account account, Authentication authentication) {
        if (!isAdmin(authentication) && !isInternalService(authentication)) {
            account.setOwnerId(authentication.getName());
        } else if (account.getOwnerId() == null || account.getOwnerId().isBlank()) {
            account.setOwnerId(authentication.getName());
        }
        Account created = service.create(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Account> update(
            @PathVariable Long id,
            @Valid @RequestBody Account account,
            Authentication authentication
    ) {
        Account existing = service.findById(id);
        assertOwnerOrPrivileged(existing, authentication);
        if (!isAdmin(authentication) && !isInternalService(authentication)) {
            account.setOwnerId(existing.getOwnerId());
        }
        Account updated = service.update(id, account);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        Account existing = service.findById(id);
        assertOwnerOrPrivileged(existing, authentication);
        service.delete(id);
    }

    private void assertOwnerOrPrivileged(Account account, Authentication authentication) {
        if (isAdmin(authentication) || isInternalService(authentication)) {
            return;
        }
        if (!authentication.getName().equals(account.getOwnerId())) {
            throw new AccessDeniedException("You are not authorized to access this account");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private boolean isInternalService(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INTERNAL_SERVICE".equals(a.getAuthority()));
    }
}
