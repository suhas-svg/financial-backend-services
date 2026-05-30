package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.DisputeCreateRequest;
import com.suhasan.finance.transaction_service.dto.DisputeFilter;
import com.suhasan.finance.transaction_service.dto.DisputeNoteRequest;
import com.suhasan.finance.transaction_service.dto.DisputeStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.TransactionDisputeResponse;
import com.suhasan.finance.transaction_service.entity.DisputeReasonCode;
import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import com.suhasan.finance.transaction_service.service.TransactionDisputeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TransactionDisputeController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import({com.suhasan.finance.transaction_service.security.SecurityConfig.class, JwtFilterTestConfig.class})
@EnableWebSecurity
class TransactionDisputeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionDisputeService disputeService;

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void createDispute_UsesCurrentCustomer() throws Exception {
        TransactionDisputeResponse response = response(DisputeStatus.OPEN);
        when(disputeService.createDispute(any(DisputeCreateRequest.class), eq("customer"))).thenReturn(response);

        mockMvc.perform(post("/api/disputes")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionId": "txn-1",
                                  "reasonCode": "UNAUTHORIZED",
                                  "description": "I did not authorize this transaction."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeId").value("dispute-1"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));

        verify(disputeService).createDispute(argThat(request ->
                "txn-1".equals(request.getTransactionId())
                        && request.getReasonCode() == DisputeReasonCode.UNAUTHORIZED
                        && "I did not authorize this transaction.".equals(request.getDescription())), eq("customer"));
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void listCustomerDisputes_UsesCurrentCustomer() throws Exception {
        when(disputeService.listCustomerDisputes(eq("customer"), any())).thenReturn(new PageImpl<>(List.of(response(DisputeStatus.OPEN)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].disputeId").value("dispute-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void getCustomerDispute_UsesCurrentCustomer() throws Exception {
        when(disputeService.getCustomerDispute("dispute-1", "customer")).thenReturn(response(DisputeStatus.OPEN));

        mockMvc.perform(get("/api/disputes/dispute-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeId").value("dispute-1"));
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void adminListDisputes_AllowsAdminAndPassesFilters() throws Exception {
        when(disputeService.searchAdminDisputes(any(DisputeFilter.class), any())).thenReturn(new PageImpl<>(List.of(response(DisputeStatus.IN_REVIEW)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/disputes/admin")
                        .param("status", "IN_REVIEW")
                        .param("reasonCode", "DUPLICATE")
                        .param("assignedTo", "ops")
                        .param("userId", "customer")
                        .param("transactionId", "txn-1")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-31T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("IN_REVIEW"));

        verify(disputeService).searchAdminDisputes(argThat(filter ->
                filter.getStatus() == DisputeStatus.IN_REVIEW
                        && filter.getReasonCode() == DisputeReasonCode.DUPLICATE
                        && "ops".equals(filter.getAssignedTo())
                        && "customer".equals(filter.getUserId())
                        && "txn-1".equals(filter.getTransactionId())
                        && LocalDateTime.parse("2026-05-01T00:00:00").equals(filter.getFrom())
                        && LocalDateTime.parse("2026-05-31T00:00:00").equals(filter.getTo())), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminListDisputes_RejectsNormalUsers() throws Exception {
        mockMvc.perform(get("/api/disputes/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void adminClaimStatusAndNote_UseCurrentAdmin() throws Exception {
        when(disputeService.claimDispute("dispute-1", "ops")).thenReturn(response(DisputeStatus.IN_REVIEW));
        when(disputeService.updateStatus(eq("dispute-1"), any(DisputeStatusUpdateRequest.class), eq("ops"))).thenReturn(response(DisputeStatus.APPROVED));
        when(disputeService.addNote(eq("dispute-1"), any(DisputeNoteRequest.class), eq("ops"))).thenReturn(response(DisputeStatus.IN_REVIEW));

        mockMvc.perform(patch("/api/disputes/admin/dispute-1/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mockMvc.perform(patch("/api/disputes/admin/dispute-1/status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "APPROVED",
                                  "resolutionNote": "Customer claim accepted."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/disputes/admin/dispute-1/notes")
                        .contentType("application/json")
                        .content("""
                                {
                                  "note": "Reviewed account history."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeId").value("dispute-1"));
    }

    private TransactionDisputeResponse response(DisputeStatus status) {
        return TransactionDisputeResponse.builder()
                .disputeId("dispute-1")
                .disputeNumber("DP-20260530-0001")
                .transactionId("txn-1")
                .userId("customer")
                .status(status)
                .reasonCode(DisputeReasonCode.UNAUTHORIZED)
                .description("I did not authorize this transaction.")
                .createdBy("customer")
                .createdAt(LocalDateTime.parse("2026-05-30T10:00:00"))
                .updatedAt(LocalDateTime.parse("2026-05-30T10:00:00"))
                .build();
    }
}
