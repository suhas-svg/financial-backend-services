package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.RiskCaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskCaseResponse {
    private String caseId;
    private String caseNumber;
    private RiskCaseStatus status;
    private RiskCasePriority priority;
    private String title;
    private String userId;
    private String transactionId;
    private String primaryAlertId;
    private String assignedTo;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime claimedAt;
    private LocalDateTime closedAt;
    private String resolutionNote;
    private List<RiskAlertResponse> linkedAlerts;
    private List<Note> notes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Note {
        private String noteId;
        private String author;
        private String note;
        private LocalDateTime createdAt;
    }
}
