package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_exception_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconciliationExceptionNote {

    @Id
    @Column(name = "note_id", nullable = false)
    private UUID noteId;

    @Column(name = "exception_id", nullable = false)
    private UUID exceptionId;

    @Column(name = "author", nullable = false, length = 120)
    private String author;

    @Column(name = "note", nullable = false, length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ReconciliationExceptionNote create(UUID exceptionId, String author, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Exception note is required");
        }
        return ReconciliationExceptionNote.builder()
                .noteId(UUID.randomUUID())
                .exceptionId(exceptionId)
                .author(author)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
