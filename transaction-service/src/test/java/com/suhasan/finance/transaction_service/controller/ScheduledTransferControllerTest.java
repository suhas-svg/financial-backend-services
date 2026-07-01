package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.ScheduledTransferCreateRequest;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferResponse;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferRunResponse;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferRunStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import com.suhasan.finance.transaction_service.service.ScheduledTransferService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ScheduledTransferController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import({com.suhasan.finance.transaction_service.security.SecurityConfig.class, JwtFilterTestConfig.class})
@EnableWebSecurity
class ScheduledTransferControllerTest {

    private static final Instant NEXT_RUN_AT = Instant.parse("2026-07-15T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduledTransferService scheduledTransferService;

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void createScheduleUsesCurrentCustomer() throws Exception {
        when(scheduledTransferService.create(any(ScheduledTransferCreateRequest.class), eq("customer")))
                .thenReturn(response(ScheduledTransferStatus.ACTIVE));

        mockMvc.perform(post("/api/scheduled-transfers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "fromAccountId": "acct-from",
                                  "toAccountId": "acct-to",
                                  "amount": 125.50,
                                  "currency": "USD",
                                  "description": "Monthly savings",
                                  "reference": "save-1",
                                  "scheduleType": "RECURRING",
                                  "frequency": "MONTHLY",
                                  "firstRunAt": "2026-07-15T10:15:30Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.scheduleType").value("RECURRING"))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.nextRunAt").value("2026-07-15T10:15:30Z"));

        verify(scheduledTransferService).create(argThat(request ->
                "acct-from".equals(request.getFromAccountId())
                        && "acct-to".equals(request.getToAccountId())
                        && new BigDecimal("125.50").compareTo(request.getAmount()) == 0
                        && request.getScheduleType() == ScheduledTransferType.RECURRING
                        && request.getFrequency() == ScheduledTransferFrequency.MONTHLY
                        && NEXT_RUN_AT.equals(request.getFirstRunAt())), eq("customer"));
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void listAndGetSchedulesUseCurrentCustomer() throws Exception {
        when(scheduledTransferService.list(eq("customer"), eq(ScheduledTransferStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(response(ScheduledTransferStatus.ACTIVE)), PageRequest.of(0, 20), 1));
        when(scheduledTransferService.get("schedule-1", "customer"))
                .thenReturn(response(ScheduledTransferStatus.ACTIVE));

        mockMvc.perform(get("/api/scheduled-transfers")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].scheduleType").value("RECURRING"))
                .andExpect(jsonPath("$.content[0].frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.content[0].nextRunAt").value("2026-07-15T10:15:30Z"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/scheduled-transfers/schedule-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.scheduleType").value("RECURRING"))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.nextRunAt").value("2026-07-15T10:15:30Z"));

        ArgumentCaptor<Pageable> listPageable = ArgumentCaptor.forClass(Pageable.class);
        verify(scheduledTransferService).list(eq("customer"), eq(ScheduledTransferStatus.ACTIVE), listPageable.capture());
        assertDefaultSort(listPageable.getValue(), "nextRunAt", Sort.Direction.ASC);
        verify(scheduledTransferService).get("schedule-1", "customer");
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void pauseResumeCancelUseCurrentCustomer() throws Exception {
        when(scheduledTransferService.pause("schedule-1", "customer"))
                .thenReturn(response(ScheduledTransferStatus.PAUSED));
        when(scheduledTransferService.resume("schedule-1", "customer"))
                .thenReturn(response(ScheduledTransferStatus.ACTIVE));
        when(scheduledTransferService.cancel("schedule-1", "customer"))
                .thenReturn(response(ScheduledTransferStatus.CANCELED));

        mockMvc.perform(patch("/api/scheduled-transfers/schedule-1/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.scheduleType").value("RECURRING"))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.nextRunAt").value("2026-07-15T10:15:30Z"));

        mockMvc.perform(patch("/api/scheduled-transfers/schedule-1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.scheduleType").value("RECURRING"))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.nextRunAt").value("2026-07-15T10:15:30Z"));

        mockMvc.perform(delete("/api/scheduled-transfers/schedule-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.scheduleType").value("RECURRING"))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.nextRunAt").value("2026-07-15T10:15:30Z"));

        verify(scheduledTransferService).pause("schedule-1", "customer");
        verify(scheduledTransferService).resume("schedule-1", "customer");
        verify(scheduledTransferService).cancel("schedule-1", "customer");
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void listRunsUsesCurrentCustomer() throws Exception {
        ScheduledTransferRunResponse run = ScheduledTransferRunResponse.builder()
                .runId("run-1")
                .scheduleId("schedule-1")
                .scheduledFor(NEXT_RUN_AT)
                .status(ScheduledTransferRunStatus.COMPLETED)
                .transactionId("txn-1")
                .idempotencyKey("scheduled-transfer:schedule-1:2026-07-15T10:15:30Z")
                .build();
        when(scheduledTransferService.listRuns(eq("schedule-1"), eq("customer"), any()))
                .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/scheduled-transfers/schedule-1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].runId").value("run-1"))
                .andExpect(jsonPath("$.content[0].scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].scheduledFor").value("2026-07-15T10:15:30Z"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> runsPageable = ArgumentCaptor.forClass(Pageable.class);
        verify(scheduledTransferService).listRuns(eq("schedule-1"), eq("customer"), runsPageable.capture());
        assertDefaultSort(runsPageable.getValue(), "scheduledFor", Sort.Direction.DESC);
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/scheduled-transfers"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scheduledTransferService);
    }

    private ScheduledTransferResponse response(ScheduledTransferStatus status) {
        return ScheduledTransferResponse.builder()
                .scheduleId("schedule-1")
                .userId("customer")
                .fromAccountId("acct-from")
                .toAccountId("acct-to")
                .amount(new BigDecimal("125.50"))
                .currency("USD")
                .description("Monthly savings")
                .reference("save-1")
                .scheduleType(ScheduledTransferType.RECURRING)
                .frequency(ScheduledTransferFrequency.MONTHLY)
                .nextRunAt(NEXT_RUN_AT)
                .status(status)
                .build();
    }

    private void assertDefaultSort(Pageable pageable, String property, Sort.Direction direction) {
        org.assertj.core.api.Assertions.assertThat(pageable.getPageSize()).isEqualTo(20);
        org.assertj.core.api.Assertions.assertThat(pageable.getSort().getOrderFor(property))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(direction);
    }
}
