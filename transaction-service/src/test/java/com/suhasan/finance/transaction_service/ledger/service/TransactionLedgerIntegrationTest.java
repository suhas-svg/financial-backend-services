package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.ledger.domain.JournalState;
import com.suhasan.finance.transaction_service.ledger.domain.JournalType;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.PostingDirection;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import com.suhasan.finance.transaction_service.service.AuditService;
import com.suhasan.finance.transaction_service.service.MetricsService;
import com.suhasan.finance.transaction_service.service.RiskEvaluationService;
import com.suhasan.finance.transaction_service.service.TransactionLimitService;
import com.suhasan.finance.transaction_service.service.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionLedgerIntegrationTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private ResilientAccountServiceClient accountServiceClient;
    @Mock private AuditService auditService;
    @Mock private MetricsService metricsService;
    @Mock private TransactionLimitService transactionLimitService;
    @Mock private RiskEvaluationService riskEvaluationService;
    @Mock private LedgerPostingService ledgerPostingService;
    @Mock private AccountLedgerResolver accountLedgerResolver;

    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(
                transactionRepository,
                accountServiceClient,
                auditService,
                metricsService,
                transactionLimitService,
                riskEvaluationService,
                ledgerPostingService,
                accountLedgerResolver);
        ReflectionTestUtils.setField(transactionService, "ledgerAuthoritative", true);

        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(anyString(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            if (transaction.getTransactionId() == null) {
                transaction.setTransactionId("tx-ledger-1");
            }
            return transaction;
        });
    }

    @Test
    void depositPostsLedgerJournalAndDoesNotMutateLegacyAccountBalance() {
        UUID clearingLedgerAccountId = UUID.randomUUID();
        UUID customerLedgerAccountId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        AccountDto account = AccountDto.builder()
                .id(101L)
                .ownerId("user-1")
                .ledgerBalance(new BigDecimal("10.00"))
                .availableBalance(new BigDecimal("10.00"))
                .accountType("CHECKING")
                .status("ACTIVE")
                .build();
        when(accountServiceClient.getAccount("101")).thenReturn(account);
        when(transactionLimitService.validateTransactionLimits(anyString(), anyString(), any(), any()))
                .thenReturn(true);
        when(accountLedgerResolver.resolveCustomerAccount("101", account)).thenReturn(customerLedgerAccountId);
        when(accountLedgerResolver.resolveSystemAccount(LedgerAccountKind.CLEARING, "USD"))
                .thenReturn(clearingLedgerAccountId);
        when(ledgerPostingService.createPending(any(JournalCommand.class)))
                .thenReturn(new JournalResult(journalId, JournalState.PENDING, false));
        when(ledgerPostingService.post(journalId, "SYSTEM"))
                .thenReturn(new JournalResult(journalId, JournalState.POSTED, false));

        TransactionResponse response = transactionService.processDeposit(
                "101", new BigDecimal("25.00"), "payroll", "DEP-001", "user-1", "idem-deposit-1");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.getJournalId()).isEqualTo(journalId);
        verify(accountServiceClient, never()).applyBalanceOperation(anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());

        ArgumentCaptor<JournalCommand> commandCaptor = ArgumentCaptor.forClass(JournalCommand.class);
        verify(ledgerPostingService).createPending(commandCaptor.capture());
        JournalCommand command = commandCaptor.getValue();
        assertThat(command.journalType()).isEqualTo(JournalType.DEPOSIT);
        assertThat(command.currency()).isEqualTo("USD");
        assertThat(command.idempotencyScope()).isEqualTo("user-1:DEPOSIT");
        assertThat(command.idempotencyKey()).isEqualTo("idem-deposit-1");
        assertThat(command.postings()).extracting("ledgerAccountId", "direction", "amount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(clearingLedgerAccountId, PostingDirection.DEBIT, new BigDecimal("25.00")),
                        org.assertj.core.groups.Tuple.tuple(customerLedgerAccountId, PostingDirection.CREDIT, new BigDecimal("25.00")));
    }

    @Test
    void transferPostsLedgerJournalAndDoesNotUseLegacyHoldsOrBalanceOps() {
        UUID sourceLedgerAccountId = UUID.randomUUID();
        UUID destinationLedgerAccountId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        AccountDto source = AccountDto.builder()
                .id(101L)
                .ownerId("user-1")
                .ledgerBalance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("100.00"))
                .accountType("CHECKING")
                .status("ACTIVE")
                .build();
        AccountDto destination = AccountDto.builder()
                .id(202L)
                .ownerId("user-2")
                .ledgerBalance(new BigDecimal("5.00"))
                .availableBalance(new BigDecimal("5.00"))
                .accountType("SAVINGS")
                .status("ACTIVE")
                .build();
        when(accountServiceClient.getAccount("101")).thenReturn(source);
        when(accountServiceClient.getAccount("202")).thenReturn(destination);
        when(transactionLimitService.validateTransactionLimits(anyString(), anyString(), any(), any()))
                .thenReturn(true);
        when(accountLedgerResolver.resolveCustomerAccount("101", source)).thenReturn(sourceLedgerAccountId);
        when(accountLedgerResolver.resolveCustomerAccount("202", destination)).thenReturn(destinationLedgerAccountId);
        when(ledgerPostingService.createPending(any(JournalCommand.class)))
                .thenReturn(new JournalResult(journalId, JournalState.PENDING, false));
        when(ledgerPostingService.post(journalId, "SYSTEM"))
                .thenReturn(new JournalResult(journalId, JournalState.POSTED, false));

        TransactionResponse response = transactionService.processTransfer(TransferRequest.builder()
                .fromAccountId("101")
                .toAccountId("202")
                .amount(new BigDecimal("40.00"))
                .currency("USD")
                .description("rent")
                .reference("TRF-001")
                .build(), "user-1", "idem-transfer-1");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(response.getJournalId()).isEqualTo(journalId);
        verify(accountServiceClient, never()).placeDebitHold(anyString(), anyString(), any(), anyString(), anyString());
        verify(accountServiceClient, never()).captureDebitHold(anyString(), anyString(), anyString(), anyString());
        verify(accountServiceClient, never()).applyBalanceOperation(anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());

        ArgumentCaptor<JournalCommand> commandCaptor = ArgumentCaptor.forClass(JournalCommand.class);
        verify(ledgerPostingService).createPending(commandCaptor.capture());
        JournalCommand command = commandCaptor.getValue();
        assertThat(command.journalType()).isEqualTo(JournalType.TRANSFER);
        assertThat(command.idempotencyScope()).isEqualTo("user-1:TRANSFER");
        assertThat(command.idempotencyKey()).isEqualTo("idem-transfer-1");
        assertThat(command.postings()).extracting("ledgerAccountId", "direction", "amount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(sourceLedgerAccountId, PostingDirection.DEBIT, new BigDecimal("40.00")),
                        org.assertj.core.groups.Tuple.tuple(destinationLedgerAccountId, PostingDirection.CREDIT, new BigDecimal("40.00")));
    }

    @Test
    void withdrawalPostsLedgerJournalAndDoesNotUseLegacyHolds() {
        UUID customerLedgerAccountId = UUID.randomUUID();
        UUID clearingLedgerAccountId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        AccountDto account = AccountDto.builder()
                .id(101L)
                .ownerId("user-1")
                .ledgerBalance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("100.00"))
                .accountType("CHECKING")
                .status("ACTIVE")
                .build();
        when(accountServiceClient.getAccount("101")).thenReturn(account);
        when(transactionLimitService.validateTransactionLimits(anyString(), anyString(), any(), any()))
                .thenReturn(true);
        when(accountLedgerResolver.resolveCustomerAccount("101", account)).thenReturn(customerLedgerAccountId);
        when(accountLedgerResolver.resolveSystemAccount(LedgerAccountKind.CLEARING, "USD"))
                .thenReturn(clearingLedgerAccountId);
        when(ledgerPostingService.createPending(any(JournalCommand.class)))
                .thenReturn(new JournalResult(journalId, JournalState.PENDING, false));
        when(ledgerPostingService.post(journalId, "SYSTEM"))
                .thenReturn(new JournalResult(journalId, JournalState.POSTED, false));

        TransactionResponse response = transactionService.processWithdrawal(
                "101", new BigDecimal("35.00"), "atm", "WDR-001", "user-1", "idem-withdrawal-1");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(response.getJournalId()).isEqualTo(journalId);
        verify(accountServiceClient, never()).placeDebitHold(anyString(), anyString(), any(), anyString(), anyString());
        verify(accountServiceClient, never()).captureDebitHold(anyString(), anyString(), anyString(), anyString());

        ArgumentCaptor<JournalCommand> commandCaptor = ArgumentCaptor.forClass(JournalCommand.class);
        verify(ledgerPostingService).createPending(commandCaptor.capture());
        JournalCommand command = commandCaptor.getValue();
        assertThat(command.journalType()).isEqualTo(JournalType.WITHDRAWAL);
        assertThat(command.idempotencyScope()).isEqualTo("user-1:WITHDRAWAL");
        assertThat(command.idempotencyKey()).isEqualTo("idem-withdrawal-1");
        assertThat(command.postings()).extracting("ledgerAccountId", "direction", "amount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(customerLedgerAccountId, PostingDirection.DEBIT, new BigDecimal("35.00")),
                        org.assertj.core.groups.Tuple.tuple(clearingLedgerAccountId, PostingDirection.CREDIT, new BigDecimal("35.00")));
    }

    @Test
    void reversalUsesCompensatingLedgerJournalAndDoesNotMutateLegacyBalances() {
        UUID originalJournalId = UUID.randomUUID();
        UUID reversalJournalId = UUID.randomUUID();
        Transaction original = Transaction.builder()
                .transactionId("tx-original")
                .fromAccountId("101")
                .toAccountId("202")
                .amount(new BigDecimal("40.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description("rent")
                .reference("TRF-001")
                .createdBy("user-1")
                .createdAt(LocalDateTime.now())
                .journalId(originalJournalId)
                .build();
        when(transactionRepository.findByIdWithLock("tx-original")).thenReturn(Optional.of(original));
        when(transactionRepository.isTransactionReversed("tx-original")).thenReturn(false);
        when(ledgerPostingService.reverse(originalJournalId, "user-1", "duplicate", "idem-reversal-1"))
                .thenReturn(new JournalResult(reversalJournalId, JournalState.POSTED, false));

        TransactionResponse response = transactionService.reverseTransaction(
                "tx-original", "duplicate", "user-1", "idem-reversal-1");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(response.getJournalId()).isEqualTo(reversalJournalId);
        assertThat(original.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(original.getReversalTransactionId()).isEqualTo(response.getTransactionId());
        verify(accountServiceClient, never()).applyBalanceOperation(anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());
        verify(ledgerPostingService).reverse(originalJournalId, "user-1", "duplicate", "idem-reversal-1");
    }
}
