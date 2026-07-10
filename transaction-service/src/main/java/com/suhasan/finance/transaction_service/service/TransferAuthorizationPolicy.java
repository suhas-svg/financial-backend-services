package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.BeneficiaryInfo;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferAuthorizationPolicy {
    private final TransactionRepository transactionRepository;
    private final ResilientAccountServiceClient accountServiceClient;

    @Value("${security.step-up.enabled:false}")
    private boolean enabled;
    @Value("${security.step-up.high-value-threshold:5000.00}")
    private BigDecimal highValueThreshold;
    @Value("${security.step-up.beneficiary-cooling-hours:24}")
    private long beneficiaryCoolingHours;
    @Value("${security.step-up.rapid-transfer-count:5}")
    private long rapidTransferCount;
    @Value("${security.step-up.rapid-transfer-window-minutes:10}")
    private long rapidTransferWindowMinutes;
    @Value("${security.step-up.recent-unfreeze-hours:24}")
    private long recentUnfreezeHours;

    public List<TransferAuthorizationReason> evaluate(TransferRequest request, String userId) {
        if (!enabled) {
            return List.of();
        }
        AccountDto source = accountServiceClient.getAccountInternal(request.getFromAccountId());
        AccountDto destination = accountServiceClient.getAccountInternal(request.getToAccountId());
        if (source == null || !userId.equals(source.getOwnerId())) {
            throw new IllegalArgumentException("From account not found");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination account not found");
        }

        List<TransferAuthorizationReason> reasons = new ArrayList<>();
        if (request.getAmount().compareTo(highValueThreshold) >= 0) {
            reasons.add(TransferAuthorizationReason.HIGH_VALUE_TRANSFER);
        }

        if (request.getBeneficiaryId() != null && !request.getBeneficiaryId().isBlank()) {
            BeneficiaryInfo beneficiary = accountServiceClient.getBeneficiary(request.getBeneficiaryId(), userId);
            if (beneficiary == null || !"ACTIVE".equals(beneficiary.getStatus())
                    || !request.getToAccountId().equals(beneficiary.getDestinationAccountId())
                    || !request.getCurrency().equalsIgnoreCase(beneficiary.getCurrency())) {
                throw new IllegalArgumentException("Beneficiary does not match this transfer");
            }
            if (beneficiary.getCreatedAt() != null
                    && beneficiary.getCreatedAt().isAfter(LocalDateTime.now().minusHours(beneficiaryCoolingHours))) {
                reasons.add(TransferAuthorizationReason.NEW_BENEFICIARY);
            }
        } else if (!userId.equals(destination.getOwnerId())) {
            reasons.add(TransferAuthorizationReason.MANUAL_EXTERNAL_DESTINATION);
        }

        long recentTransfers = transactionRepository.countByCreatedByAndTypeAndStatusAndCreatedAtAfter(
                userId, TransactionType.TRANSFER, TransactionStatus.COMPLETED,
                LocalDateTime.now().minusMinutes(rapidTransferWindowMinutes));
        if (recentTransfers >= Math.max(0, rapidTransferCount - 1)) {
            reasons.add(TransferAuthorizationReason.RAPID_TRANSFERS);
        }

        if ("ACTIVE".equalsIgnoreCase(source.getStatus()) && source.getStatusUpdatedAt() != null
                && source.getStatusUpdatedAt().isAfter(LocalDateTime.now().minusHours(recentUnfreezeHours))) {
            reasons.add(TransferAuthorizationReason.RECENTLY_UNFROZEN_SOURCE);
        }
        return List.copyOf(reasons);
    }
}
