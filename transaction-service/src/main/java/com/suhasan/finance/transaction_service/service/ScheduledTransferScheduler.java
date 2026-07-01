package com.suhasan.finance.transaction_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTransferScheduler {

    private final ScheduledTransferService scheduledTransferService;

    @Scheduled(fixedDelayString = "${scheduled-transfers.worker.fixed-delay-ms:60000}")
    public void runDueTransfers() {
        int processed = scheduledTransferService.executeDueTransfers(Instant.now(), 50);
        if (processed > 0) {
            log.info("Processed {} due scheduled transfers", processed);
        }
    }
}
