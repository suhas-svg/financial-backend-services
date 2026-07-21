package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "synthetic_sandbox_bootstrap")
@Getter @Setter @NoArgsConstructor
public class SyntheticSandboxBootstrap {
    @Id @Column(name = "singleton_id")
    private Short singletonId;
    @Column(name = "operator_username", nullable = false)
    private String operatorUsername;
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
