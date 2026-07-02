package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.BeneficiaryCreateRequest;
import com.suhasan.finance.account_service.dto.BeneficiaryResponse;
import com.suhasan.finance.account_service.dto.BeneficiaryUpdateRequest;
import com.suhasan.finance.account_service.entity.BeneficiaryStatus;
import com.suhasan.finance.account_service.service.BeneficiaryService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/beneficiaries")
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Dependencies are injected and managed by Spring")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> create(
            @Valid @RequestBody BeneficiaryCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(beneficiaryService.create(authentication.getName(), request));
    }

    @GetMapping
    public ResponseEntity<Page<BeneficiaryResponse>> list(
            @RequestParam(required = false) BeneficiaryStatus status,
            @PageableDefault(size = 20, sort = "displayName") Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(beneficiaryService.list(authentication.getName(), status, pageable));
    }

    @GetMapping("/{beneficiaryId}")
    public ResponseEntity<BeneficiaryResponse> get(
            @PathVariable String beneficiaryId,
            Authentication authentication) {
        return ResponseEntity.ok(beneficiaryService.get(beneficiaryId, authentication.getName()));
    }

    @PutMapping("/{beneficiaryId}")
    public ResponseEntity<BeneficiaryResponse> update(
            @PathVariable String beneficiaryId,
            @Valid @RequestBody BeneficiaryUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(beneficiaryService.update(beneficiaryId, authentication.getName(), request));
    }

    @DeleteMapping("/{beneficiaryId}")
    public ResponseEntity<BeneficiaryResponse> disable(
            @PathVariable String beneficiaryId,
            Authentication authentication) {
        return ResponseEntity.ok(beneficiaryService.disable(beneficiaryId, authentication.getName()));
    }

    @PatchMapping("/{beneficiaryId}/disable")
    public ResponseEntity<BeneficiaryResponse> disablePatch(
            @PathVariable String beneficiaryId,
            Authentication authentication) {
        return disable(beneficiaryId, authentication);
    }
}
