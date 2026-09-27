package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_mfa_methods", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_mfa_method", columnNames = {"user_id", "method_type"}))
@Getter
@Setter
@NoArgsConstructor
public class MfaMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "method_type", nullable = false, length = 20)
    private String methodType = "TOTP";

    @Column(name = "secret_ciphertext", nullable = false, length = 1024)
    private String secretCiphertext;

    @Column(name = "secret_key_id", nullable = false, length = 64)
    private String secretKeyId = "legacy";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MfaMethodStatus status = MfaMethodStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * Consecutive failed TOTP verifications. Reset to zero on any successful verification and
     * cleared when the lockout window elapses, so a user who mistypes a few codes is not
     * permanently locked out.
     */
    @Column(name = "failed_verification_attempts", nullable = false)
    private int failedVerificationAttempts = 0;

    /**
     * Set when failed_verification_attempts reaches the configured maximum. While this is in the
     * future, TOTP verification is refused without consulting the secret at all, which bounds an
     * online guessing attack against the six-digit code space.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = MfaMethodStatus.PENDING;
        }
        if (version == null) {
            version = 0L;
        }
    }
}
