package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.BeneficiaryResponse;
import com.suhasan.finance.account_service.service.BeneficiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/beneficiaries")
@RequiredArgsConstructor
public class InternalBeneficiaryController {
    private final BeneficiaryService beneficiaryService;

    @GetMapping("/{beneficiaryId}")
    public BeneficiaryResponse get(@PathVariable String beneficiaryId, @RequestParam String userId) {
        return beneficiaryService.get(beneficiaryId, userId);
    }
}
