package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.BeneficiaryCreateRequest;
import com.suhasan.finance.account_service.dto.BeneficiaryResponse;
import com.suhasan.finance.account_service.dto.BeneficiaryUpdateRequest;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.Beneficiary;
import com.suhasan.finance.account_service.entity.BeneficiaryStatus;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;

    public BeneficiaryResponse create(String userId, BeneficiaryCreateRequest request) {
        String destinationAccountId = requiredText(request.getDestinationAccountId(), "Destination account is required");
        String currency = requiredText(request.getCurrency(), "Currency is required").toUpperCase(Locale.ROOT);
        Account destination = accountRepository.findById(parseAccountId(destinationAccountId))
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));
        if (userId.equals(destination.getOwnerId())) {
            throw new IllegalArgumentException("Beneficiary destination cannot be one of your own accounts");
        }
        if (!currency.equals(destination.getCurrency())) {
            throw new IllegalArgumentException("Beneficiary currency must match destination account currency");
        }
        if (beneficiaryRepository.existsByUserIdAndDestinationAccountIdAndCurrencyAndStatus(
                userId, destinationAccountId, currency, BeneficiaryStatus.ACTIVE)) {
            throw new IllegalArgumentException("Active beneficiary already exists for this destination");
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .beneficiaryId(UUID.randomUUID().toString())
                .userId(userId)
                .displayName(requiredText(request.getDisplayName(), "Display name is required"))
                .destinationAccountId(destinationAccountId)
                .currency(currency)
                .nickname(optionalText(request.getNickname()))
                .notes(optionalText(request.getNotes()))
                .status(BeneficiaryStatus.ACTIVE)
                .build();
        return toResponse(beneficiaryRepository.save(beneficiary));
    }

    @Transactional(readOnly = true)
    public Page<BeneficiaryResponse> list(String userId, BeneficiaryStatus status, Pageable pageable) {
        Page<Beneficiary> page = status == null
                ? beneficiaryRepository.findByUserId(userId, pageable)
                : beneficiaryRepository.findByUserIdAndStatus(userId, status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BeneficiaryResponse get(String beneficiaryId, String userId) {
        return toResponse(findOwned(beneficiaryId, userId));
    }

    public BeneficiaryResponse update(String beneficiaryId, String userId, BeneficiaryUpdateRequest request) {
        Beneficiary beneficiary = findOwned(beneficiaryId, userId);
        if (beneficiary.getStatus() != BeneficiaryStatus.ACTIVE) {
            throw new IllegalStateException("Only active beneficiaries can be updated");
        }
        beneficiary.setDisplayName(requiredText(request.getDisplayName(), "Display name is required"));
        beneficiary.setNickname(optionalText(request.getNickname()));
        beneficiary.setNotes(optionalText(request.getNotes()));
        return toResponse(beneficiaryRepository.save(beneficiary));
    }

    public BeneficiaryResponse disable(String beneficiaryId, String userId) {
        Beneficiary beneficiary = findOwned(beneficiaryId, userId);
        if (beneficiary.getStatus() != BeneficiaryStatus.DISABLED) {
            beneficiary.setStatus(BeneficiaryStatus.DISABLED);
            beneficiary.setDisabledAt(LocalDateTime.now());
            beneficiary = beneficiaryRepository.save(beneficiary);
        }
        return toResponse(beneficiary);
    }

    private Beneficiary findOwned(String beneficiaryId, String userId) {
        return beneficiaryRepository.findByBeneficiaryIdAndUserId(beneficiaryId, userId)
                .orElseThrow(() -> new AccessDeniedException("Beneficiary not found"));
    }

    private Long parseAccountId(String accountId) {
        try {
            return Long.parseLong(accountId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Destination account ID must be numeric");
        }
    }

    private String requiredText(String value, String message) {
        String text = optionalText(value);
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BeneficiaryResponse toResponse(Beneficiary beneficiary) {
        return BeneficiaryResponse.builder()
                .beneficiaryId(beneficiary.getBeneficiaryId())
                .userId(beneficiary.getUserId())
                .displayName(beneficiary.getDisplayName())
                .destinationAccountId(beneficiary.getDestinationAccountId())
                .currency(beneficiary.getCurrency())
                .nickname(beneficiary.getNickname())
                .notes(beneficiary.getNotes())
                .status(beneficiary.getStatus())
                .createdAt(beneficiary.getCreatedAt())
                .updatedAt(beneficiary.getUpdatedAt())
                .disabledAt(beneficiary.getDisabledAt())
                .version(beneficiary.getVersion())
                .build();
    }
}
