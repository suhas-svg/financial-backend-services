package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AccountCreateRequest;
import com.suhasan.finance.account_service.dto.AccountMetadataUpdateRequest;
import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.dto.AccountStatusUpdateRequest;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.AccountStatus;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
            @RequestParam(required = false) final String ownerId,
            @RequestParam(required = false) final String accountType,
            @RequestParam(required = false) final AccountStatus status,
            @PageableDefault(size = 20, sort = "createdAt") final Pageable pageable,
            final Authentication authentication
    ) {
        String effectiveOwnerId = ownerId;
        if (!isAdmin(authentication) && !isInternalService(authentication)) {
            effectiveOwnerId = authentication.getName();
        }
        final Page<AccountResponse> page = service.listAccounts(effectiveOwnerId, accountType, status, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> get(@PathVariable final Long id, final Authentication authentication) {
        final Account existing = service.findById(id);
        assertOwnerOrPrivileged(existing, authentication);
        return ResponseEntity.ok(existing);
    }

    @PostMapping
    public ResponseEntity<Account> create(@Valid @RequestBody final AccountCreateRequest request, final Authentication authentication) {
        String ownerId = isAdmin(authentication) || isInternalService(authentication)
                ? request.ownerId() : authentication.getName();
        if (ownerId == null || ownerId.isBlank()) ownerId = authentication.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, ownerId));
    }
    @PutMapping("/{id}")
    public ResponseEntity<Account> update(
            @PathVariable final Long id,
            @Valid @RequestBody final AccountMetadataUpdateRequest request,
            final Authentication authentication
    ) {
        final Account existing = service.findById(id);
        assertOwnerOrPrivileged(existing, authentication);
        return ResponseEntity.ok(service.updateMetadata(id, request));
    }
    @PatchMapping("/{id}/status")
    public ResponseEntity<Account> updateStatus(
            @PathVariable final Long id,
            @Valid @RequestBody final AccountStatusUpdateRequest request,
            final Authentication authentication
    ) {
        if (!isAdmin(authentication)) {
            throw new AccessDeniedException("Only admins can update account status");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("Status reason is required");
        }
        final Account updated = service.updateStatus(id, request.getStatus(), request.getReason(), authentication.getName());
        return ResponseEntity.ok(updated);
    }

    private void assertOwnerOrPrivileged(final Account account, final Authentication authentication) {
        if (isAdmin(authentication) || isInternalService(authentication)) {
            return;
        }
        if (!authentication.getName().equals(account.getOwnerId())) {
            throw new AccessDeniedException("You are not authorized to access this account");
        }
    }

    private boolean isAdmin(final Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private boolean isInternalService(final Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INTERNAL_SERVICE".equals(a.getAuthority()));
    }
}
