package com.suhasan.finance.account_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Beneficiary {

    @Id
    @Column(name = "beneficiary_id", length = 36)
    private String beneficiaryId;

    @NotBlank
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @NotBlank
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @NotBlank
    @Column(name = "destination_account_id", nullable = false, length = 64)
    private String destinationAccountId;

    @NotBlank
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 120)
    private String nickname;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BeneficiaryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        if (beneficiaryId == null || beneficiaryId.isBlank()) {
            beneficiaryId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = BeneficiaryStatus.ACTIVE;
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
