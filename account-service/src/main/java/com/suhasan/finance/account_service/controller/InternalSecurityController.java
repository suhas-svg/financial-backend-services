package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.StepUpInternalDtos;
import com.suhasan.finance.account_service.service.StepUpChallengeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/security/challenges")
@RequiredArgsConstructor
public class InternalSecurityController {
    private final StepUpChallengeService challengeService;

    @PostMapping
    public StepUpInternalDtos.CreateChallengeResponse create(@Valid @RequestBody StepUpInternalDtos.CreateChallengeRequest request) {
        return challengeService.create(request);
    }

    @PostMapping("/{challengeId}/consume")
    public StepUpInternalDtos.ConsumeChallengeResponse consume(@PathVariable String challengeId,
                                                                @Valid @RequestBody StepUpInternalDtos.ConsumeChallengeRequest request) {
        return challengeService.consume(challengeId, request);
    }
}
