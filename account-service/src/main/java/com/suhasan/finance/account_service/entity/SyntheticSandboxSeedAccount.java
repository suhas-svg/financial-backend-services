package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "synthetic_sandbox_seed_accounts")
@Getter @Setter @NoArgsConstructor
public class SyntheticSandboxSeedAccount {
    @Id @Column(name = "seed_key", length = 100)
    private String seedKey;
    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
