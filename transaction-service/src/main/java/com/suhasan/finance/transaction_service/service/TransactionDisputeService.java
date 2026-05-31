package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.DisputeCreateRequest;
import com.suhasan.finance.transaction_service.dto.DisputeFilter;
import com.suhasan.finance.transaction_service.dto.DisputeNoteRequest;
import com.suhasan.finance.transaction_service.dto.DisputeStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.DisputeSummaryResponse;
import com.suhasan.finance.transaction_service.dto.TransactionDisputeResponse;
import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionDispute;
import com.suhasan.finance.transaction_service.entity.TransactionDisputeNote;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.repository.TransactionDisputeNoteRepository;
import com.suhasan.finance.transaction_service.repository.TransactionDisputeRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionDisputeService {

    private static final DateTimeFormatter DISPUTE_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int DISPUTE_WINDOW_DAYS = 60;

    private final TransactionDisputeRepository disputeRepository;
    private final TransactionDisputeNoteRepository noteRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final ResilientAccountServiceClient accountServiceClient;

    @Transactional
    public TransactionDisputeResponse createDispute(DisputeCreateRequest request, String userId) {
        if (request == null || !hasText(request.getTransactionId()) || request.getReasonCode() == null || !hasText(request.getDescription())) {
            throw new IllegalArgumentException("Transaction, reason, and description are required");
        }
        Transaction transaction = transactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not available for dispute"));
        if (!userId.equals(transaction.getCreatedBy())) {
            throw new IllegalArgumentException("Transaction not available for dispute");
        }
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new IllegalArgumentException("Only completed transactions can be disputed");
        }
        if (transaction.getCreatedAt() == null || transaction.getCreatedAt().isBefore(LocalDateTime.now().minusDays(DISPUTE_WINDOW_DAYS))) {
            throw new IllegalArgumentException("Transactions can only be disputed within 60 days");
        }
        if (disputeRepository.existsActiveByTransactionId(transaction.getTransactionId())) {
            throw new IllegalArgumentException("Transaction already has an active dispute");
        }
        TransactionDispute dispute = TransactionDispute.builder()
                .disputeNumber(nextDisputeNumber())
                .transactionId(transaction.getTransactionId())
                .userId(userId)
                .status(DisputeStatus.OPEN)
                .reasonCode(request.getReasonCode())
                .description(request.getDescription().trim())
                .createdBy(userId)
                .build();
        TransactionDispute saved = disputeRepository.save(dispute);
        auditService.logDisputeEvent("DISPUTE_CREATED", saved.getDisputeId(), saved.getTransactionId(), userId, saved.getDescription());
        emitDisputeCreatedNotification(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TransactionDisputeResponse> listCustomerDisputes(String userId, Pageable pageable) {
        return disputeRepository.findAll((root, query, cb) -> cb.equal(root.get("userId"), userId), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionDisputeResponse getCustomerDispute(String disputeId, String userId) {
        TransactionDispute dispute = findDispute(disputeId);
        if (!userId.equals(dispute.getUserId())) {
            throw new IllegalArgumentException("Dispute not found: " + disputeId);
        }
        return toResponse(dispute);
    }

    @Transactional(readOnly = true)
    public Page<TransactionDisputeResponse> searchAdminDisputes(DisputeFilter filter, Pageable pageable) {
        return disputeRepository.findAll(toSpecification(filter), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DisputeSummaryResponse getSummary(LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveFrom = from != null ? from : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now();
        return new DisputeSummaryResponse(
                disputeRepository.countByCreatedAtBetween(effectiveFrom, effectiveTo),
                disputeRepository.countByStatusAndCreatedAtBetween(DisputeStatus.OPEN, effectiveFrom, effectiveTo),
                disputeRepository.countByStatusAndCreatedAtBetween(DisputeStatus.IN_REVIEW, effectiveFrom, effectiveTo),
                disputeRepository.countByStatusAndCreatedAtBetween(DisputeStatus.APPROVED, effectiveFrom, effectiveTo),
                disputeRepository.countByStatusAndCreatedAtBetween(DisputeStatus.DENIED, effectiveFrom, effectiveTo),
                disputeRepository.countByStatusAndCreatedAtBetween(DisputeStatus.CLOSED, effectiveFrom, effectiveTo),
                disputeRepository.countByAssignedToIsNullAndCreatedAtBetween(effectiveFrom, effectiveTo)
        );
    }

    @Transactional
    public TransactionDisputeResponse claimDispute(String disputeId, String admin) {
        TransactionDispute dispute = findDispute(disputeId);
        if (hasText(dispute.getAssignedTo()) && !dispute.getAssignedTo().equals(admin)) {
            throw new IllegalArgumentException("Dispute is already assigned to " + dispute.getAssignedTo());
        }
        if (dispute.getStatus().isClosedStatus()) {
            throw new IllegalArgumentException("Closed disputes cannot be claimed");
        }
        dispute.setAssignedTo(admin);
        dispute.setClaimedAt(LocalDateTime.now());
        if (dispute.getStatus() == DisputeStatus.OPEN) {
            dispute.setStatus(DisputeStatus.IN_REVIEW);
        }
        TransactionDispute saved = disputeRepository.save(dispute);
        auditService.logDisputeEvent("DISPUTE_CLAIMED", saved.getDisputeId(), saved.getTransactionId(), admin, "Assigned to " + admin);
        return toResponse(saved);
    }

    @Transactional
    public TransactionDisputeResponse updateStatus(String disputeId, DisputeStatusUpdateRequest request, String admin) {
        if (request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Dispute status is required");
        }
        TransactionDispute dispute = findDispute(disputeId);
        dispute.setStatus(request.getStatus());
        dispute.setResolutionNote(trimOrNull(request.getResolutionNote()));
        if (request.getStatus().isClosedStatus() && dispute.getClosedAt() == null) {
            dispute.setClosedAt(LocalDateTime.now());
        }
        if (!request.getStatus().isClosedStatus()) {
            dispute.setClosedAt(null);
        }
        TransactionDispute saved = disputeRepository.save(dispute);
        auditService.logDisputeEvent("DISPUTE_STATUS_UPDATED", saved.getDisputeId(), saved.getTransactionId(), admin, request.getStatus().name());
        emitDisputeStatusNotification(saved);
        return toResponse(saved);
    }

    @Transactional
    public TransactionDisputeResponse addNote(String disputeId, DisputeNoteRequest request, String admin) {
        if (request == null || !hasText(request.getNote())) {
            throw new IllegalArgumentException("Note is required");
        }
        TransactionDispute dispute = findDispute(disputeId);
        TransactionDisputeNote note = TransactionDisputeNote.builder()
                .dispute(dispute)
                .author(admin)
                .note(request.getNote().trim())
                .build();
        dispute.getNotes().add(note);
        noteRepository.save(note);
        auditService.logDisputeEvent("DISPUTE_NOTE_ADDED", dispute.getDisputeId(), dispute.getTransactionId(), admin, note.getNote());
        return toResponse(dispute);
    }

    Specification<TransactionDispute> toSpecification(DisputeFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (filter.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.getStatus()));
                }
                if (filter.getReasonCode() != null) {
                    predicates.add(cb.equal(root.get("reasonCode"), filter.getReasonCode()));
                }
                if (hasText(filter.getAssignedTo())) {
                    if ("UNASSIGNED".equalsIgnoreCase(filter.getAssignedTo())) {
                        predicates.add(cb.isNull(root.get("assignedTo")));
                    } else {
                        predicates.add(cb.equal(root.get("assignedTo"), filter.getAssignedTo()));
                    }
                }
                if (hasText(filter.getUserId())) {
                    predicates.add(cb.equal(root.get("userId"), filter.getUserId()));
                }
                if (hasText(filter.getTransactionId())) {
                    predicates.add(cb.equal(root.get("transactionId"), filter.getTransactionId()));
                }
                if (filter.getFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFrom()));
                }
                if (filter.getTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getTo()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private TransactionDispute findDispute(String disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));
    }

    private String nextDisputeNumber() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);
        long sequence = disputeRepository.countByCreatedAtBetween(start, end) + 1;
        return "DP-" + today.format(DISPUTE_DAY) + "-" + String.format("%04d", sequence);
    }

    private TransactionDisputeResponse toResponse(TransactionDispute dispute) {
        return TransactionDisputeResponse.builder()
                .disputeId(dispute.getDisputeId())
                .disputeNumber(dispute.getDisputeNumber())
                .transactionId(dispute.getTransactionId())
                .userId(dispute.getUserId())
                .status(dispute.getStatus())
                .reasonCode(dispute.getReasonCode())
                .description(dispute.getDescription())
                .assignedTo(dispute.getAssignedTo())
                .createdBy(dispute.getCreatedBy())
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt())
                .claimedAt(dispute.getClaimedAt())
                .closedAt(dispute.getClosedAt())
                .resolutionNote(dispute.getResolutionNote())
                .notes(dispute.getNotes().stream().map(this::toNoteResponse).toList())
                .build();
    }

    private TransactionDisputeResponse.Note toNoteResponse(TransactionDisputeNote note) {
        return TransactionDisputeResponse.Note.builder()
                .noteId(note.getNoteId())
                .author(note.getAuthor())
                .note(note.getNote())
                .createdAt(note.getCreatedAt())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimOrNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void emitDisputeCreatedNotification(TransactionDispute dispute) {
        try {
            accountServiceClient.createNotification(ResilientAccountServiceClient.NotificationRequest.builder()
                    .userId(dispute.getUserId())
                    .type("DISPUTE_CREATED")
                    .severity("INFO")
                    .title("Dispute submitted")
                    .message("Your dispute %s has been submitted for review.".formatted(dispute.getDisputeNumber()))
                    .sourceType("DISPUTE")
                    .sourceId(dispute.getDisputeId())
                    .dedupeKey("dispute:%s:created".formatted(dispute.getDisputeId()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to create dispute notification for dispute {}: {}", dispute.getDisputeId(), e.getMessage());
        }
    }

    private void emitDisputeStatusNotification(TransactionDispute dispute) {
        if (dispute.getStatus() != DisputeStatus.APPROVED
                && dispute.getStatus() != DisputeStatus.DENIED
                && dispute.getStatus() != DisputeStatus.CLOSED) {
            return;
        }
        try {
            accountServiceClient.createNotification(ResilientAccountServiceClient.NotificationRequest.builder()
                    .userId(dispute.getUserId())
                    .type("DISPUTE_STATUS_UPDATED")
                    .severity(dispute.getStatus() == DisputeStatus.APPROVED ? "SUCCESS" : "WARNING")
                    .title("Dispute status updated")
                    .message("Your dispute %s is now %s.%s".formatted(
                            dispute.getDisputeNumber(),
                            dispute.getStatus(),
                            hasText(dispute.getResolutionNote()) ? " " + dispute.getResolutionNote() : ""))
                    .sourceType("DISPUTE")
                    .sourceId(dispute.getDisputeId())
                    .dedupeKey("dispute:%s:status:%s".formatted(dispute.getDisputeId(), dispute.getStatus()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to create dispute status notification for dispute {}: {}", dispute.getDisputeId(), e.getMessage());
        }
    }
}
