package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
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
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.entity.TransactionProcessingState;
import com.suhasan.finance.transaction_service.ledger.domain.JournalState;
import com.suhasan.finance.transaction_service.ledger.domain.JournalType;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.PostingDirection;
import com.suhasan.finance.transaction_service.ledger.domain.PostingDraft;
import com.suhasan.finance.transaction_service.ledger.service.AccountLedgerResolver;
import com.suhasan.finance.transaction_service.ledger.service.JournalCommand;
import com.suhasan.finance.transaction_service.ledger.service.JournalResult;
import com.suhasan.finance.transaction_service.ledger.service.LedgerPostingService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

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
    private final LedgerPostingService ledgerPostingService;
    private final AccountLedgerResolver accountLedgerResolver;

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
        if (dispute.getStatus().isClosedStatus()) {
            throw new IllegalArgumentException("Closed disputes cannot change status");
        }
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

    /**
     * Credit an approved dispute through the authoritative double-entry ledger.
     * Approval is deliberately separate from reimbursement so a review decision
     * cannot move money without an explicit operator action and idempotency key.
     */
    @Transactional
    public TransactionDisputeResponse reimburseApprovedDispute(
            String disputeId, String admin, String idempotencyKey) {
        String key = normalizeReimbursementKey(idempotencyKey);
        TransactionDispute dispute = disputeRepository.findByIdWithLock(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));
        if (dispute.getStatus() != DisputeStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved disputes can be reimbursed");
        }
        if (hasText(dispute.getReimbursementTransactionId())) {
            return toResponse(dispute);
        }

        Transaction original = transactionRepository.findById(dispute.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("Disputed transaction is not available"));
        if (original.getStatus() != TransactionStatus.COMPLETED
                || (original.getType() != TransactionType.TRANSFER
                && original.getType() != TransactionType.WITHDRAWAL)) {
            throw new IllegalArgumentException(
                    "Only completed customer-funded transfers or withdrawals can be reimbursed automatically");
        }
        if (!hasText(original.getFromAccountId()) || "EXTERNAL".equalsIgnoreCase(original.getFromAccountId())) {
            throw new IllegalArgumentException("Disputed transaction has no eligible customer debit account");
        }

        AccountDto customerAccount = accountServiceClient.getAccountInternal(original.getFromAccountId());
        if (customerAccount == null || !dispute.getUserId().equals(customerAccount.getOwnerId())) {
            throw new IllegalArgumentException("Reimbursement target account ownership could not be verified");
        }
        String currency = normalizeCurrency(original.getCurrency());
        if (customerAccount.getCurrency() != null
                && !currency.equalsIgnoreCase(customerAccount.getCurrency())) {
            throw new IllegalArgumentException("Reimbursement currency does not match the customer account");
        }

        String fingerprint = reimbursementFingerprint(dispute, original);
        Optional<Transaction> existing = transactionRepository
                .findFirstByCreatedByAndTypeAndIdempotencyKey(admin, TransactionType.REFUND, key);
        if (existing.isPresent()) {
            Transaction prior = existing.get();
            if (!fingerprint.equals(prior.getRequestFingerprint())) {
                throw new IllegalStateException("Idempotency key was already used for a different reimbursement");
            }
            if (prior.getStatus() != TransactionStatus.COMPLETED) {
                throw new IllegalStateException("Reimbursement is already processing; check Transactions before retrying");
            }
            attachReimbursement(dispute, prior);
            return toResponse(disputeRepository.save(dispute));
        }

        UUID customerLedgerAccount = accountLedgerResolver.resolveCustomerAccount(
                original.getFromAccountId(), customerAccount);
        UUID suspenseLedgerAccount = accountLedgerResolver.resolveSystemAccount(
                LedgerAccountKind.SUSPENSE, currency);
        String refundId = UUID.randomUUID().toString();
        Transaction reimbursement = Transaction.builder()
                .transactionId(refundId)
                .fromAccountId("SUSPENSE")
                .toAccountId(original.getFromAccountId())
                .amount(original.getAmount())
                .currency(currency)
                .type(TransactionType.REFUND)
                .status(TransactionStatus.PROCESSING)
                .processingState(TransactionProcessingState.INITIATED)
                .description("[DISPUTE] Reimbursement " + dispute.getDisputeNumber())
                .reference("DISPUTE:" + dispute.getDisputeNumber())
                .idempotencyKey(key)
                .requestFingerprint(fingerprint)
                .createdBy(admin)
                .toAccountBalanceBefore(customerAccount.ledgerBalanceOrBalance())
                .build();
        reimbursement = transactionRepository.save(reimbursement);

        JournalCommand command = new JournalCommand(
                JournalType.CORRECTION,
                currency,
                LocalDate.now(),
                reimbursement.getDescription(),
                refundId,
                admin,
                admin + ":DISPUTE_REIMBURSEMENT:" + disputeId,
                key,
                fingerprint,
                List.of(
                        new PostingDraft(suspenseLedgerAccount, PostingDirection.DEBIT,
                                reimbursement.getAmount(), currency, "Dispute reimbursement suspense debit"),
                        new PostingDraft(customerLedgerAccount, PostingDirection.CREDIT,
                                reimbursement.getAmount(), currency, "Dispute reimbursement customer credit")));
        JournalResult pending = ledgerPostingService.createPending(command);
        JournalResult posted = ledgerPostingService.post(pending.journalId(), admin);
        if (posted.state() != JournalState.POSTED) {
            throw new IllegalStateException("Reimbursement journal did not post: " + posted.state());
        }

        reimbursement.setJournalId(posted.journalId());
        reimbursement.setStatus(TransactionStatus.COMPLETED);
        reimbursement.setProcessingState(TransactionProcessingState.COMPLETED);
        reimbursement.setProcessedBy(admin);
        reimbursement.setProcessedAt(LocalDateTime.now());
        reimbursement.setToAccountBalanceAfter(customerAccount.ledgerBalanceOrBalance().add(reimbursement.getAmount()));
        Transaction savedReimbursement = transactionRepository.save(reimbursement);
        attachReimbursement(dispute, savedReimbursement);
        TransactionDispute savedDispute = disputeRepository.save(dispute);
        auditService.logDisputeEvent("DISPUTE_REIMBURSED", dispute.getDisputeId(),
                original.getTransactionId(), admin,
                "refundTransactionId=" + savedReimbursement.getTransactionId()
                        + ", amount=" + savedReimbursement.getAmount() + " " + currency);
        emitReimbursementNotification(dispute, savedReimbursement);
        return toResponse(savedDispute);
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
                .reimbursementTransactionId(dispute.getReimbursementTransactionId())
                .reimbursementAmount(dispute.getReimbursementAmount())
                .reimbursementCurrency(dispute.getReimbursementCurrency())
                .reimbursedAt(dispute.getReimbursedAt())
                .notes(dispute.getNotes().stream().map(this::toNoteResponse).toList())
                .build();
    }

    private void attachReimbursement(TransactionDispute dispute, Transaction reimbursement) {
        dispute.setReimbursementTransactionId(reimbursement.getTransactionId());
        dispute.setReimbursementAmount(reimbursement.getAmount());
        dispute.setReimbursementCurrency(reimbursement.getCurrency());
        dispute.setReimbursedAt(reimbursement.getProcessedAt() != null
                ? reimbursement.getProcessedAt() : LocalDateTime.now());
    }

    private String normalizeReimbursementKey(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("Idempotency-Key is required for reimbursement");
        }
        String normalized = value.trim();
        if (normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-128 URL-safe characters");
        }
        return normalized;
    }

    private String reimbursementFingerprint(TransactionDispute dispute, Transaction original) {
        String canonical = "DISPUTE_REIMBURSEMENT|" + dispute.getDisputeId() + "|"
                + original.getTransactionId() + "|" + original.getAmount().stripTrailingZeros().toPlainString()
                + "|" + normalizeCurrency(original.getCurrency());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String normalizeCurrency(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("Transaction currency is required for reimbursement");
        }
        return value.trim().toUpperCase(Locale.ROOT);
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

    private void emitReimbursementNotification(TransactionDispute dispute, Transaction reimbursement) {
        try {
            accountServiceClient.createNotification(ResilientAccountServiceClient.NotificationRequest.builder()
                    .userId(dispute.getUserId())
                    .type("DISPUTE_REIMBURSEMENT_COMPLETED")
                    .severity("SUCCESS")
                    .title("Dispute reimbursement completed")
                    .message("Reimbursement " + reimbursement.getAmount() + " " + reimbursement.getCurrency()
                            + " was credited to your account.")
                    .sourceType("DISPUTE")
                    .sourceId(dispute.getDisputeId())
                    .dedupeKey("dispute:%s:reimbursement:%s".formatted(
                            dispute.getDisputeId(), reimbursement.getTransactionId()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to create reimbursement notification for dispute {}: {}",
                    dispute.getDisputeId(), e.getMessage());
        }
    }
}
