package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailControlEvent;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailRuntimeControl;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeGuardrailControlEventRepository;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeGuardrailRuntimeControlRepository;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.GuardrailControlUpdateRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutcomeGuardrailControlServiceTest {
    private final OutcomeGuardrailRuntimeControlRepository controls = mock(OutcomeGuardrailRuntimeControlRepository.class);
    private final OutcomeGuardrailControlEventRepository events = mock(OutcomeGuardrailControlEventRepository.class);
    private final OutcomeGuardrailControlService service = new OutcomeGuardrailControlService(controls, events);

    @Test
    void readsUpdatesAndListsControlEvents() {
        OutcomeGuardrailRuntimeControl control = control(false, "initial", "system");
        when(controls.findById(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)).thenReturn(Optional.of(control));
        when(controls.lockById(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)).thenReturn(Optional.of(control));
        when(events.findByActorAndIdempotencyKey("operator", "key-1")).thenReturn(Optional.empty());

        assertThat(service.current().executionEnabled()).isFalse();
        assertThat(service.update(new GuardrailControlUpdateRequest(true, " enable "), "operator", " key-1 ")
                .executionEnabled()).isTrue();
        verify(controls).save(control);
        verify(events).save(any());

        OutcomeGuardrailControlEvent event = OutcomeGuardrailControlEvent.builder()
                .eventId("event-1").executionEnabled(true).reason("enable").actor("operator")
                .createdAt(Instant.parse("2026-07-26T00:00:00Z")).build();
        when(events.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(event));
        assertThat(service.events()).singleElement().extracting("eventId").isEqualTo("event-1");
    }

    @Test
    void enforcesConfigurationAndIdempotency() {
        assertThatThrownBy(service::current).isInstanceOf(IllegalStateException.class);
        GuardrailControlUpdateRequest request = new GuardrailControlUpdateRequest(true, "reason");
        assertThatThrownBy(() -> service.update(request, "operator", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(request, "operator", "x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);

        OutcomeGuardrailControlEvent replay = OutcomeGuardrailControlEvent.builder()
                .eventId("event").executionEnabled(true).reason("reason").actor("operator")
                .idempotencyKey("key").requestFingerprint("not-the-fingerprint").build();
        when(events.findByActorAndIdempotencyKey("operator", "key")).thenReturn(Optional.of(replay));
        assertThatThrownBy(() -> service.update(request, "operator", "key"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void returnsAnIdempotentReplay() {
        GuardrailControlUpdateRequest request = new GuardrailControlUpdateRequest(true, "reason");
        OutcomeGuardrailControlEvent first = OutcomeGuardrailControlEvent.builder()
                .eventId("event").executionEnabled(true).reason("reason").actor("operator")
                .idempotencyKey("key").requestFingerprint(fingerprint("true|reason"))
                .createdAt(Instant.parse("2026-07-26T00:00:00Z")).build();
        when(events.findByActorAndIdempotencyKey("operator", "key")).thenReturn(Optional.of(first));
        assertThat(service.update(request, "operator", "key").changedBy()).isEqualTo("operator");
    }

    private OutcomeGuardrailRuntimeControl control(boolean enabled, String reason, String actor) {
        return OutcomeGuardrailRuntimeControl.builder()
                .controlId(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)
                .executionEnabled(enabled).reason(reason).changedBy(actor).updatedAt(Instant.now()).build();
    }

    private String fingerprint(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
