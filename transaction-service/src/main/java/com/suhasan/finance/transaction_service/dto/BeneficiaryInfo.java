package com.suhasan.finance.transaction_service.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BeneficiaryInfo {
    private String beneficiaryId;
    private String userId;
    private String destinationAccountId;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
}
