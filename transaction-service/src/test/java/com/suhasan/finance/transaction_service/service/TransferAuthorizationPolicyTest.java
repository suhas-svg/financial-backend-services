package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.BeneficiaryInfo;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferAuthorizationPolicyTest {
    @Mock TransactionRepository transactionRepository;
    @Mock ResilientAccountServiceClient accountClient;
    private TransferAuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new TransferAuthorizationPolicy(transactionRepository, accountClient);
        ReflectionTestUtils.setField(policy, "enabled", true);
        ReflectionTestUtils.setField(policy, "highValueThreshold", new BigDecimal("5000.00"));
        ReflectionTestUtils.setField(policy, "beneficiaryCoolingHours", 24L);
        ReflectionTestUtils.setField(policy, "rapidTransferCount", 5L);
        ReflectionTestUtils.setField(policy, "rapidTransferWindowMinutes", 10L);
        ReflectionTestUtils.setField(policy, "recentUnfreezeHours", 24L);
        when(accountClient.getAccountInternal("1")).thenReturn(account(1L, "alice"));
        when(accountClient.getAccountInternal("2")).thenReturn(account(2L, "bob"));
        when(transactionRepository.countByCreatedByAndTypeAndStatusAndCreatedAtAfter(
                eq("alice"), eq(TransactionType.TRANSFER), eq(TransactionStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(0L);
    }

    @Test
    void manualExternalHighValueTransferCombinesPolicyReasons() {
        assertThat(policy.evaluate(request("6000.00", null), "alice"))
                .containsExactly(TransferAuthorizationReason.HIGH_VALUE_TRANSFER,
                        TransferAuthorizationReason.MANUAL_EXTERNAL_DESTINATION);
    }

    @Test
    void newlySavedBeneficiaryRequiresStepUpButEstablishedBeneficiaryDoesNot() {
        BeneficiaryInfo beneficiary = new BeneficiaryInfo();
        beneficiary.setBeneficiaryId("beneficiary-1");
        beneficiary.setUserId("alice");
        beneficiary.setDestinationAccountId("2");
        beneficiary.setCurrency("USD");
        beneficiary.setStatus("ACTIVE");
        beneficiary.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(accountClient.getBeneficiary("beneficiary-1", "alice")).thenReturn(beneficiary);

        assertThat(policy.evaluate(request("100.00", "beneficiary-1"), "alice"))
                .containsExactly(TransferAuthorizationReason.NEW_BENEFICIARY);
        beneficiary.setCreatedAt(LocalDateTime.now().minusDays(2));
        assertThat(policy.evaluate(request("100.00", "beneficiary-1"), "alice")).isEmpty();
    }

    private TransferRequest request(String amount, String beneficiaryId) {
        return TransferRequest.builder().fromAccountId("1").toAccountId("2")
                .beneficiaryId(beneficiaryId).amount(new BigDecimal(amount)).currency("USD").build();
    }

    private AccountDto account(long id, String owner) {
        return AccountDto.builder().id(id).ownerId(owner).status("ACTIVE")
                .statusUpdatedAt(LocalDateTime.now().minusDays(2)).build();
    }
}
