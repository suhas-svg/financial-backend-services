package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.integration.NotificationProviderDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notification-provider")
public class NotificationProviderOperationsController {
    private final NotificationProviderDispatcher dispatcher;

    @PostMapping("/receipts/{receiptId}/replay")
    public ResponseEntity<Void> replay(@PathVariable final long receiptId) {
        dispatcher.replay(receiptId);
        return ResponseEntity.accepted().build();
    }
}
