package com.suhasan.finance.transaction_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderWebhookReplayServiceTest {
    @Test
    void unconfiguredVerifierFailsClosedBeforeReplayEvidenceIsWritten() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new ProviderWebhookReplayService(jdbc, new FailClosedProviderWebhookVerifier());

        assertThatThrownBy(() -> service.verify("activation-1", "delivery-1", Instant.now(),
                "signature-reference", "{}".getBytes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed closed");
    }

    @Test
    void verifiedWebhookPersistsDigestAndRejectsReplay() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProviderWebhookVerifier verifier = (activationId, deliveryId, timestamp, signature, payload) ->
                new ProviderWebhookVerifier.Verification(true, "verifier://sandbox/1", "VERIFIED");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new ProviderWebhookReplayService(jdbc, verifier);

        var accepted = service.verify("activation-1", "delivery-1", Instant.now(),
                "signature-reference", "{}".getBytes());
        assertThat(accepted.get("accepted")).isEqualTo(true);
        assertThat(accepted.get("payloadDigest")).asString().hasSize(64);

        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate delivery"));
        assertThatThrownBy(() -> service.verify("activation-1", "delivery-1", Instant.now(),
                "signature-reference", "{}".getBytes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replay");
    }

    @Test
    void staleWebhookTimestampIsRejectedBeforeSignatureVerification() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProviderWebhookVerifier verifier = mock(ProviderWebhookVerifier.class);
        var service = new ProviderWebhookReplayService(jdbc, verifier);

        assertThatThrownBy(() -> service.verify("activation-1", "delivery-1",
                Instant.now().minusSeconds(600), "signature-reference", "{}".getBytes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replay window");
    }
}
