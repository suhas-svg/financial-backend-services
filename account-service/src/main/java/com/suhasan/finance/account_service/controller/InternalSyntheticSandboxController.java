package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.sandbox.SyntheticAccountSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/sandbox")
@RequiredArgsConstructor
public class InternalSyntheticSandboxController {
    private final SyntheticAccountSeedService seedService;

    @PostMapping("/seed-accounts")
    public SyntheticAccountSeedService.SeededAccounts seedAccounts(@RequestParam final String owner) {
        return seedService.seed(owner);
    }
}
