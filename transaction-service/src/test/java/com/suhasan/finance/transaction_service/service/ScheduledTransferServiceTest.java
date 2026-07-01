package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferCreateRequest;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferResponse;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferRun;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferRunStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRepository;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledTransferServiceTest {

    @Mock
    private ScheduledTransferRepository scheduleRepository;

    @Mock
    private ScheduledTransferRunRepository runRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private ResilientAccountServiceClient accountServiceClient;

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private ScheduledTransferService service;

    @BeforeEach
    void setUp() {
        lenientOwnedSourceAccount();
    }

    @Test
    void createScheduleRejectsPastFirstRunAt() {
        ScheduledTransferCreateRequest request = baseCreateRequest();
        request.setFirstRunAt(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.create(request, "customer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void createScheduleRejectsDifferentOwnerSourceAccount() {
        ScheduledTransferCreateRequest request = baseCreateRequest();
        AccountDto source = AccountDto.builder()
                .id(101L)
                .ownerId("other-user")
                .accountType("CHECKING")
                .active(true)
                .build();
        when(accountServiceClient.getAccount("101")).thenReturn(source);

        assertThatThrownBy(() -> service.create(request, "customer"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("source account");
    }

    @Test
    void pauseResumeCancelRequireOwnerAndValidState() {
        ScheduledTransfer schedule = activeSchedule("schedule-1", "customer");
        when(scheduleRepository.findByScheduleIdAndUserId("schedule-1", "customer"))
                .thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(ScheduledTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduledTransferResponse paused = service.pause("schedule-1", "customer");
        assertThat(paused.getStatus()).isEqualTo(ScheduledTransferStatus.PAUSED);

        ScheduledTransferResponse resumed = service.resume("schedule-1", "customer");
        assertThat(resumed.getStatus()).isEqualTo(ScheduledTransferStatus.ACTIVE);

        ScheduledTransferResponse canceled = service.cancel("schedule-1", "customer");
        assertThat(canceled.getStatus()).isEqualTo(ScheduledTransferStatus.CANCELED);
    }

    @Test
    void executeDueScheduleCreatesTransferRunAndLinksTransaction() {
        ScheduledTransfer schedule = activeSchedule("schedule-1", "customer");
        schedule.setNextRunAt(Instant.parse("2026-07-01T09:00:00Z"));
        when(scheduleRepository.findDueActiveForUpdate(eq(Instant.parse("2026-07-01T09:01:00Z")), any()))
                .thenReturn(List.of(schedule));
        when(runRepository.existsByScheduleScheduleIdAndScheduledFor("schedule-1", schedule.getNextRunAt()))
                .thenReturn(false);
        when(runRepository.save(any(ScheduledTransferRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionService.processTransfer(any(TransferRequest.class), eq("customer"), anyString()))
                .thenReturn(TransactionResponse.builder().transactionId("txn-1").build());

        int processed = service.executeDueTransfers(Instant.parse("2026-07-01T09:01:00Z"), 50);

        assertThat(processed).isEqualTo(1);
        verify(transactionService).processTransfer(argThat(request ->
                "101".equals(request.getFromAccountId())
                        && "102".equals(request.getToAccountId())
                        && request.getAmount().compareTo(new BigDecimal("125.00")) == 0), eq("customer"), contains("schedule-1"));
        verify(runRepository, atLeastOnce()).save(argThat(run ->
                run.getStatus() == ScheduledTransferRunStatus.COMPLETED
                        && "txn-1".equals(run.getTransactionId())));
    }

    @Test
    void failedOneTimeExecutionCompletesScheduleWithoutRetry() {
        ScheduledTransfer schedule = activeSchedule("schedule-1", "customer");
        schedule.setScheduleType(ScheduledTransferType.ONE_TIME);
        schedule.setFrequency(null);
        schedule.setNextRunAt(Instant.parse("2026-07-01T09:00:00Z"));
        when(scheduleRepository.findDueActiveForUpdate(any(), any())).thenReturn(List.of(schedule));
        when(runRepository.existsByScheduleScheduleIdAndScheduledFor("schedule-1", schedule.getNextRunAt()))
                .thenReturn(false);
        when(runRepository.save(any(ScheduledTransferRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionService.processTransfer(any(TransferRequest.class), eq("customer"), anyString()))
                .thenThrow(new RuntimeException("Insufficient funds"));

        int processed = service.executeDueTransfers(Instant.parse("2026-07-01T09:01:00Z"), 50);

        assertThat(processed).isEqualTo(1);
        assertThat(schedule.getStatus()).isEqualTo(ScheduledTransferStatus.COMPLETED);
        verify(runRepository, atLeastOnce()).save(argThat(run ->
                run.getStatus() == ScheduledTransferRunStatus.FAILED
                        && run.getFailureReason().contains("Insufficient funds")));
    }

    @Test
    void duplicateRunIsSkippedWhenRunAlreadyExists() {
        ScheduledTransfer schedule = activeSchedule("schedule-1", "customer");
        schedule.setNextRunAt(Instant.parse("2026-07-01T09:00:00Z"));
        when(scheduleRepository.findDueActiveForUpdate(any(), any())).thenReturn(List.of(schedule));
        when(runRepository.existsByScheduleScheduleIdAndScheduledFor("schedule-1", schedule.getNextRunAt()))
                .thenReturn(true);

        int processed = service.executeDueTransfers(Instant.parse("2026-07-01T09:01:00Z"), 50);

        assertThat(processed).isZero();
        verify(transactionService, never()).processTransfer(any(), anyString(), anyString());
        verify(metricsService).recordScheduledTransferDuplicatePrevented();
    }

    private ScheduledTransferCreateRequest baseCreateRequest() {
        ScheduledTransferCreateRequest request = new ScheduledTransferCreateRequest();
        request.setFromAccountId("101");
        request.setToAccountId("102");
        request.setAmount(new BigDecimal("125.00"));
        request.setCurrency("USD");
        request.setDescription("Monthly rent");
        request.setReference("rent");
        request.setScheduleType(ScheduledTransferType.RECURRING);
        request.setFrequency(ScheduledTransferFrequency.MONTHLY);
        request.setFirstRunAt(Instant.now().plusSeconds(3600));
        return request;
    }

    private ScheduledTransfer activeSchedule(String scheduleId, String userId) {
        return ScheduledTransfer.builder()
                .scheduleId(scheduleId)
                .userId(userId)
                .fromAccountId("101")
                .toAccountId("102")
                .amount(new BigDecimal("125.00"))
                .currency("USD")
                .description("Monthly rent")
                .reference("rent")
                .scheduleType(ScheduledTransferType.RECURRING)
                .frequency(ScheduledTransferFrequency.MONTHLY)
                .nextRunAt(Instant.now().plusSeconds(3600))
                .status(ScheduledTransferStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private void lenientOwnedSourceAccount() {
        AccountDto source = AccountDto.builder()
                .id(101L)
                .ownerId("customer")
                .accountType("CHECKING")
                .active(true)
                .build();
        org.mockito.Mockito.lenient().when(accountServiceClient.getAccount("101")).thenReturn(source);
    }
}
