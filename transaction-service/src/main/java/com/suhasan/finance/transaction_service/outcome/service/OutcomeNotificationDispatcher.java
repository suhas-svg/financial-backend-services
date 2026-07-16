package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.repository.OutcomeNotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutcomeNotificationDispatcher {
    private final OutcomeNotificationDeliveryRepository repository;
    private final OutcomeNotificationDeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${outcome-protection.notifications.dispatch-delay-ms:5000}",
            initialDelayString = "${outcome-protection.notifications.dispatch-initial-delay-ms:5000}")
    public void dispatchDue() {
        Instant now = Instant.now();
        for (var delivery : repository.findDue(now, PageRequest.of(0, 50))) {
            try { deliveryService.dispatch(delivery.getDeliveryId(), now); }
            catch (RuntimeException failure) {
                log.warn("Outcome notification delivery {} dispatch failed: {}", delivery.getDeliveryId(), failure.getMessage());
            }
        }
    }
}
