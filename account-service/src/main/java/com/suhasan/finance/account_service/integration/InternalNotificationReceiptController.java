package com.suhasan.finance.account_service.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/integration-readiness/notification-receipts")
@RequiredArgsConstructor
public class InternalNotificationReceiptController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{deliveryId}")
    public Map<String, Object> receipt(@PathVariable final String deliveryId) {
        return jdbc.queryForMap("""
                SELECT delivery_id,provider,provider_receipt_id,classification,
                       reconciliation_status,attempt_count,next_attempt_at,
                       terminal_at,reconciled_at,attempted_at
                  FROM notification_provider_receipts WHERE delivery_id=?
                """, deliveryId);
    }
}
