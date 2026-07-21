package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.entity.MfaMethod;
import com.suhasan.finance.account_service.entity.MfaMethodStatus;
import com.suhasan.finance.account_service.entity.MfaRecoveryCode;
import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.exception.MfaVerificationException;
import com.suhasan.finance.account_service.integration.MfaSecretManager;
import com.suhasan.finance.account_service.repository.MfaMethodRepository;
import com.suhasan.finance.account_service.repository.MfaRecoveryCodeRepository;
import com.suhasan.finance.account_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {
    @Mock MfaMethodRepository methodRepository;
    @Mock MfaRecoveryCodeRepository recoveryCodeRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock MfaSecretManager secretManager;
    @Mock TotpService totpService;

    private MfaService service;

    @BeforeEach
    void setUp() {
        service = new MfaService(methodRepository, recoveryCodeRepository, userRepository,
                passwordEncoder, secretManager, totpService);
    }

    @Test
    void statusDistinguishesNotEnrolledFromActiveEnrollment() {
        when(methodRepository.findByUserIdAndMethodType("alice", "TOTP"))
                .thenReturn(Optional.empty());

        var notEnrolled = service.status("alice");

        assertThat(notEnrolled.enrolled()).isFalse();
        assertThat(notEnrolled.status()).isEqualTo("NOT_ENROLLED");
        assertThat(notEnrolled.recoveryCodesRemaining()).isZero();

        MfaMethod active = method(7L, MfaMethodStatus.ACTIVE);
        when(methodRepository.findByUserIdAndMethodType("alice", "TOTP"))
                .thenReturn(Optional.of(active));
        when(recoveryCodeRepository.countByMfaMethodIdAndUsedAtIsNull(7L)).thenReturn(5L);

        var enrolled = service.status("alice");

        assertThat(enrolled.enrolled()).isTrue();
        assertThat(enrolled.status()).isEqualTo("ACTIVE");
        assertThat(enrolled.recoveryCodesRemaining()).isEqualTo(5);
    }

    @Test
    void enrollVerifiesPasswordAndReplacesAnyPendingSecret() {
        User user = new User();
        user.setPassword("stored-hash");
        MfaMethod pending = method(7L, MfaMethodStatus.PENDING);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "stored-hash")).thenReturn(true);
        when(methodRepository.findByUserIdAndMethodType("alice", "TOTP"))
                .thenReturn(Optional.of(pending));
        when(totpService.generateSecret()).thenReturn("NEWSECRET");
        when(secretManager.encrypt("NEWSECRET"))
                .thenReturn(new MfaSecretManager.Ciphertext("ciphertext", "local-v1"));
        when(totpService.provisioningUri("alice", "NEWSECRET")).thenReturn("otpauth://totp/alice");
        when(methodRepository.save(any(MfaMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.enroll("alice", "current-password");

        assertThat(response.secret()).isEqualTo("NEWSECRET");
        assertThat(response.otpauthUri()).isEqualTo("otpauth://totp/alice");
        assertThat(pending.getSecretCiphertext()).isEqualTo("ciphertext");
        assertThat(pending.getSecretKeyId()).isEqualTo("local-v1");
        assertThat(pending.getStatus()).isEqualTo(MfaMethodStatus.PENDING);
        assertThat(pending.getVerifiedAt()).isNull();
        verify(recoveryCodeRepository).deleteByMfaMethodId(7L);
    }

    @Test
    void enrollRejectsAnInvalidCurrentPasswordBeforeChangingMfaState() {
        User user = new User();
        user.setPassword("stored-hash");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.enroll("alice", "wrong-password"))
                .isInstanceOf(MfaVerificationException.class)
                .hasMessage("Current password is invalid");

        verify(methodRepository, never()).save(any());
        verify(totpService, never()).generateSecret();
    }

    @Test
    void confirmActivatesTotpAndReturnsEightHashedRecoveryCodes() {
        MfaMethod pending = method(7L, MfaMethodStatus.PENDING);
        pending.setSecretCiphertext("ciphertext");
        when(methodRepository.findByUserIdAndMethodTypeAndStatus(
                "alice", "TOTP", MfaMethodStatus.PENDING)).thenReturn(Optional.of(pending));
        when(secretManager.decrypt("ciphertext", "legacy")).thenReturn("secret");
        when(totpService.verify(anyString(), anyString(), any())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("recovery-hash");
        when(methodRepository.save(any(MfaMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recoveryCodeRepository.save(any(MfaRecoveryCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.confirm("alice", "123456");

        assertThat(response.active()).isTrue();
        assertThat(response.recoveryCodes()).hasSize(8).allMatch(code -> code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertThat(pending.getStatus()).isEqualTo(MfaMethodStatus.ACTIVE);
        assertThat(pending.getVerifiedAt()).isNotNull();
        ArgumentCaptor<MfaRecoveryCode> codes = ArgumentCaptor.forClass(MfaRecoveryCode.class);
        verify(recoveryCodeRepository, org.mockito.Mockito.times(8)).save(codes.capture());
        assertThat(codes.getAllValues()).allSatisfy(code -> {
            assertThat(code.getMfaMethodId()).isEqualTo(7L);
            assertThat(code.getCodeHash()).isEqualTo("recovery-hash");
        });
    }

    @Test
    void recoveryCredentialIsSingleUseAndUpdatesLastUsedTime() {
        MfaMethod active = method(7L, MfaMethodStatus.ACTIVE);
        active.setSecretCiphertext("ciphertext");
        MfaRecoveryCode recoveryCode = new MfaRecoveryCode();
        recoveryCode.setMfaMethodId(7L);
        recoveryCode.setCodeHash("recovery-hash");
        when(secretManager.decrypt("ciphertext", "legacy")).thenReturn("secret");
        when(totpService.verify(anyString(), anyString(), any())).thenReturn(false);
        when(recoveryCodeRepository.findByMfaMethodIdAndUsedAtIsNull(7L))
                .thenReturn(List.of(recoveryCode));
        when(passwordEncoder.matches("ABCD-EFGH", "recovery-hash")).thenReturn(true);

        assertThat(service.verifyCredential(active, "ABCD-EFGH")).isTrue();

        assertThat(recoveryCode.getUsedAt()).isNotNull();
        assertThat(active.getLastUsedAt()).isNotNull();
        verify(recoveryCodeRepository).save(recoveryCode);
        verify(methodRepository).save(active);
    }

    @Test
    void confirmRejectsInvalidAuthenticatorCodeWithoutIssuingRecoveryCodes() {
        MfaMethod pending = method(7L, MfaMethodStatus.PENDING);
        pending.setSecretCiphertext("ciphertext");
        when(methodRepository.findByUserIdAndMethodTypeAndStatus(
                "alice", "TOTP", MfaMethodStatus.PENDING)).thenReturn(Optional.of(pending));
        when(secretManager.decrypt("ciphertext", "legacy")).thenReturn("secret");
        when(totpService.verify(anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.confirm("alice", "bad-code"))
                .isInstanceOf(MfaVerificationException.class)
                .hasMessage("Invalid authentication code");

        verify(recoveryCodeRepository, never()).save(any());
        assertThat(pending.getStatus()).isEqualTo(MfaMethodStatus.PENDING);
    }

    private MfaMethod method(Long id, MfaMethodStatus status) {
        MfaMethod method = new MfaMethod();
        method.setId(id);
        method.setUserId("alice");
        method.setMethodType("TOTP");
        method.setStatus(status);
        return method;
    }
}
