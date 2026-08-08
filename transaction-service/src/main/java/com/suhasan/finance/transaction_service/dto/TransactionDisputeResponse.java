package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.DisputeReasonCode;
import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDisputeResponse {
    private String disputeId;
    private String disputeNumber;
    private String transactionId;
    private String userId;
    private DisputeStatus status;
    private DisputeReasonCode reasonCode;
    private String description;
    private String assignedTo;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime claimedAt;
    private LocalDateTime closedAt;
    private String resolutionNote;
    private String reimbursementTransactionId;
    private BigDecimal reimbursementAmount;
    private String reimbursementCurrency;
    private LocalDateTime reimbursedAt;
    private List<Note> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Note {
        private String noteId;
        private String author;
        private String note;
        private LocalDateTime createdAt;
    }
}
