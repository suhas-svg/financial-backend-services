package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mfa_recovery_codes", indexes =
        @Index(name = "idx_recovery_method_unused", columnList = "mfa_method_id,used_at"))
@Getter
@Setter
@NoArgsConstructor
public class MfaRecoveryCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mfa_method_id", nullable = false)
    private Long mfaMethodId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "used_at")
    private Instant usedAt;
}
