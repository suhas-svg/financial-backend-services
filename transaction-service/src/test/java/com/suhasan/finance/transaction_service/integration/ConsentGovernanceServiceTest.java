package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsentGovernanceServiceTest {
    @Test
    void writesPostgresTimestampAndReturnsEvidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq("customer-1")))
                .thenReturn(List.of(Map.of("event_type", "EVIDENCE_EXPORTED")));
        var service = new ConsentGovernanceService(jdbc, new ObjectMapper());

        Map<String, Object> result = service.record("customer-1", "EVIDENCE_EXPORTED",
                new ConsentGovernanceService.GovernanceRequest(
                        "balance-shield", "2026-07-16.1", "TEST", "export"),
                "export-1");

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertThat(values.getValue()[12]).isInstanceOf(Timestamp.class);
        assertThat(result.get("eventType")).isEqualTo("EVIDENCE_EXPORTED");
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentEvidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForMap(anyString(), eq("customer-1"), eq("export-1")))
                .thenReturn(Map.of("event_id", "existing", "request_fingerprint", "different"));
        var service = new ConsentGovernanceService(jdbc, new ObjectMapper());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(
                        "customer-1", "EVIDENCE_EXPORTED",
                        new ConsentGovernanceService.GovernanceRequest(
                                "balance-shield", "2026-07-16.1", "TEST", "export"),
                        "export-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different consent evidence");
    }
}
