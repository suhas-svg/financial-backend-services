package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.RequestFingerprintStatus;
import com.suhasan.finance.transaction_service.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class TransactionLegacyReplayTest {

    @Test
    void ambiguousLegacyReplayFailsClosedForOperatorReconciliation() {
        TransactionServiceImpl service = mock(TransactionServiceImpl.class, CALLS_REAL_METHODS);
        Transaction existing = Transaction.builder()
                .requestFingerprint("a".repeat(64))
                .requestFingerprintStatus(RequestFingerprintStatus.LEGACY_AMBIGUOUS)
                .build();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "requireReplayFingerprint", existing, "a".repeat(64)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires operator reconciliation");
    }

    @Test
    void missingFingerprintNeverSilentlyAcceptsReplay() {
        TransactionServiceImpl service = mock(TransactionServiceImpl.class, CALLS_REAL_METHODS);
        Transaction existing = Transaction.builder()
                .requestFingerprint(null)
                .requestFingerprintStatus(RequestFingerprintStatus.LEGACY_RECONSTRUCTED)
                .build();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "requireReplayFingerprint", existing, "b".repeat(64)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no verifiable request fingerprint");
    }
}
