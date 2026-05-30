package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.DisputeCreateRequest;
import com.suhasan.finance.transaction_service.dto.DisputeNoteRequest;
import com.suhasan.finance.transaction_service.dto.DisputeStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.TransactionDisputeResponse;
import com.suhasan.finance.transaction_service.entity.DisputeReasonCode;
import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionDispute;
import com.suhasan.finance.transaction_service.entity.TransactionDisputeNote;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionDisputeNoteRepository;
import com.suhasan.finance.transaction_service.repository.TransactionDisputeRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionDisputeServiceTest {

    @Mock
    private TransactionDisputeRepository disputeRepository;

    @Mock
    private TransactionDisputeNoteRepository noteRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditService auditService;

    private TransactionDisputeService disputeService;

    @BeforeEach
    void setUp() {
        disputeService = new TransactionDisputeService(disputeRepository, noteRepository, transactionRepository, auditService);
    }

    @Test
    void createDispute_CreatesOpenDisputeForOwnedCompletedRecentTransaction() {
        Transaction transaction = transaction("txn-1", "customer", TransactionStatus.COMPLETED, LocalDateTime.now().minusDays(5));
        when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(transaction));
        when(disputeRepository.existsActiveByTransactionId("txn-1")).thenReturn(false);
        when(disputeRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(disputeRepository.save(any(TransactionDispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionDisputeResponse result = disputeService.createDispute(
                new DisputeCreateRequest("txn-1", DisputeReasonCode.UNAUTHORIZED, "I did not authorize this transaction."),
                "customer");

        assertThat(result.getTransactionId()).isEqualTo("txn-1");
        assertThat(result.getUserId()).isEqualTo("customer");
        assertThat(result.getStatus()).isEqualTo(DisputeStatus.OPEN);
        assertThat(result.getReasonCode()).isEqualTo(DisputeReasonCode.UNAUTHORIZED);
        assertThat(result.getDisputeNumber()).startsWith("DP-");
        verify(auditService).logDisputeEvent("DISPUTE_CREATED", result.getDisputeId(), "txn-1", "customer", "I did not authorize this transaction.");
    }

    @Test
    void createDispute_RejectsTransactionOwnedByAnotherUser() {
        when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(transaction("txn-1", "other", TransactionStatus.COMPLETED, LocalDateTime.now().minusDays(5))));

        assertThatThrownBy(() -> disputeService.createDispute(
                new DisputeCreateRequest("txn-1", DisputeReasonCode.DUPLICATE, "Duplicate transaction."),
                "customer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available for dispute");
    }

    @Test
    void createDispute_RejectsFailedTransaction() {
        when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(transaction("txn-1", "customer", TransactionStatus.FAILED, LocalDateTime.now().minusDays(5))));

        assertThatThrownBy(() -> disputeService.createDispute(
                new DisputeCreateRequest("txn-1", DisputeReasonCode.OTHER, "Please review."),
                "customer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only completed transactions");
    }

    @Test
    void createDispute_RejectsTransactionsOlderThanSixtyDays() {
        when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(transaction("txn-1", "customer", TransactionStatus.COMPLETED, LocalDateTime.now().minusDays(61))));

        assertThatThrownBy(() -> disputeService.createDispute(
                new DisputeCreateRequest("txn-1", DisputeReasonCode.OTHER, "Please review."),
                "customer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 days");
    }

    @Test
    void createDispute_RejectsDuplicateActiveDispute() {
        when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(transaction("txn-1", "customer", TransactionStatus.COMPLETED, LocalDateTime.now().minusDays(5))));
        when(disputeRepository.existsActiveByTransactionId("txn-1")).thenReturn(true);

        assertThatThrownBy(() -> disputeService.createDispute(
                new DisputeCreateRequest("txn-1", DisputeReasonCode.OTHER, "Please review."),
                "customer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active dispute");
    }

    @Test
    void adminClaimStatusAndNotes_UpdateDisputeWorkflow() {
        TransactionDispute dispute = dispute("dispute-1", DisputeStatus.OPEN);
        when(disputeRepository.findById("dispute-1")).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(TransactionDispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionDisputeResponse claimed = disputeService.claimDispute("dispute-1", "ops");
        assertThat(claimed.getStatus()).isEqualTo(DisputeStatus.IN_REVIEW);
        assertThat(claimed.getAssignedTo()).isEqualTo("ops");

        TransactionDisputeResponse approved = disputeService.updateStatus(
                "dispute-1",
                new DisputeStatusUpdateRequest(DisputeStatus.APPROVED, "Customer claim accepted."),
                "ops");
        assertThat(approved.getStatus()).isEqualTo(DisputeStatus.APPROVED);
        assertThat(approved.getClosedAt()).isNotNull();
        assertThat(approved.getResolutionNote()).isEqualTo("Customer claim accepted.");

        TransactionDisputeResponse withNote = disputeService.addNote("dispute-1", new DisputeNoteRequest("Follow up completed."), "ops");
        assertThat(withNote.getNotes()).extracting(TransactionDisputeResponse.Note::getNote).contains("Follow up completed.");
        verify(noteRepository).save(any(TransactionDisputeNote.class));
    }

    @Test
    void listCustomerDisputes_FiltersByCurrentUser() {
        when(disputeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        Page<TransactionDisputeResponse> result = disputeService.listCustomerDisputes("customer", PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        verify(disputeRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    private Transaction transaction(String id, String createdBy, TransactionStatus status, LocalDateTime createdAt) {
        return Transaction.builder()
                .transactionId(id)
                .fromAccountId("101")
                .toAccountId("202")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(status)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .build();
    }

    private TransactionDispute dispute(String id, DisputeStatus status) {
        return TransactionDispute.builder()
                .disputeId(id)
                .disputeNumber("DP-20260530-0001")
                .transactionId("txn-1")
                .userId("customer")
                .status(status)
                .reasonCode(DisputeReasonCode.UNAUTHORIZED)
                .description("I did not authorize this transaction.")
                .createdBy("customer")
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .notes(new java.util.ArrayList<>())
                .build();
    }
}
