package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "journal_state_events")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JournalStateEvent {
    @Id
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "journal_id", nullable = false)
    private UUID journalId;
    @Column(name = "event_sequence", nullable = false)
    private int eventSequence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalState state;
    @Column(nullable = false)
    private String actor;
    private String reason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
