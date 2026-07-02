package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.BeneficiaryCreateRequest;
import com.suhasan.finance.account_service.dto.BeneficiaryResponse;
import com.suhasan.finance.account_service.dto.BeneficiaryUpdateRequest;
import com.suhasan.finance.account_service.entity.Beneficiary;
import com.suhasan.finance.account_service.entity.BeneficiaryStatus;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.BeneficiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {

    @Mock
    private BeneficiaryRepository beneficiaryRepository;

    @Mock
    private AccountRepository accountRepository;

    private BeneficiaryService service;

    @BeforeEach
    void setUp() {
        service = new BeneficiaryService(beneficiaryRepository, accountRepository);
    }

    @Test
    @DisplayName("Creates active beneficiary for an existing external destination account")
    void createsActiveBeneficiaryForExternalDestinationAccount() {
        CheckingAccount destination = account(200L, "recipient", "USD");
        when(accountRepository.findById(200L)).thenReturn(Optional.of(destination));
        when(beneficiaryRepository.existsByUserIdAndDestinationAccountIdAndCurrencyAndStatus(
                "customer", "200", "USD", BeneficiaryStatus.ACTIVE)).thenReturn(false);
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BeneficiaryResponse response = service.create("customer", BeneficiaryCreateRequest.builder()
                .displayName("  Rent account  ")
                .destinationAccountId("200")
                .currency("USD")
                .nickname("  Rent  ")
                .notes("  Monthly payment  ")
                .build());

        assertThat(response.getUserId()).isEqualTo("customer");
        assertThat(response.getDisplayName()).isEqualTo("Rent account");
        assertThat(response.getDestinationAccountId()).isEqualTo("200");
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getNickname()).isEqualTo("Rent");
        assertThat(response.getNotes()).isEqualTo("Monthly payment");
        assertThat(response.getStatus()).isEqualTo(BeneficiaryStatus.ACTIVE);
        assertThat(response.getBeneficiaryId()).isNotBlank();
        verify(beneficiaryRepository).save(any(Beneficiary.class));
    }

    @Test
    @DisplayName("Rejects beneficiary pointing at one of the customer's own accounts")
    void rejectsOwnDestinationAccount() {
        when(accountRepository.findById(101L)).thenReturn(Optional.of(account(101L, "customer", "USD")));

        assertThatThrownBy(() -> service.create("customer", BeneficiaryCreateRequest.builder()
                .displayName("My savings")
                .destinationAccountId("101")
                .currency("USD")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be one of your own accounts");

        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects duplicate active beneficiary for the same destination and currency")
    void rejectsDuplicateActiveBeneficiary() {
        when(accountRepository.findById(200L)).thenReturn(Optional.of(account(200L, "recipient", "USD")));
        when(beneficiaryRepository.existsByUserIdAndDestinationAccountIdAndCurrencyAndStatus(
                "customer", "200", "USD", BeneficiaryStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.create("customer", BeneficiaryCreateRequest.builder()
                .displayName("Rent")
                .destinationAccountId("200")
                .currency("USD")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Lists beneficiaries for the current user and requested status")
    void listsForCurrentUserAndStatus() {
        Beneficiary beneficiary = beneficiary("beneficiary-1", "customer", "200", BeneficiaryStatus.ACTIVE);
        when(beneficiaryRepository.findByUserIdAndStatus("customer", BeneficiaryStatus.ACTIVE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(beneficiary)));

        var page = service.list("customer", BeneficiaryStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(BeneficiaryResponse::getBeneficiaryId)
                .containsExactly("beneficiary-1");
    }

    @Test
    @DisplayName("Updates owned active beneficiary display fields")
    void updatesOwnedActiveBeneficiaryDisplayFields() {
        Beneficiary beneficiary = beneficiary("beneficiary-1", "customer", "200", BeneficiaryStatus.ACTIVE);
        when(beneficiaryRepository.findByBeneficiaryIdAndUserId("beneficiary-1", "customer"))
                .thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.save(beneficiary)).thenReturn(beneficiary);

        BeneficiaryResponse response = service.update("beneficiary-1", "customer", BeneficiaryUpdateRequest.builder()
                .displayName("Updated recipient")
                .nickname("Updated")
                .notes("New note")
                .build());

        assertThat(response.getDisplayName()).isEqualTo("Updated recipient");
        assertThat(response.getNickname()).isEqualTo("Updated");
        assertThat(response.getNotes()).isEqualTo("New note");
    }

    @Test
    @DisplayName("Soft disables owned active beneficiary")
    void softDisablesOwnedActiveBeneficiary() {
        Beneficiary beneficiary = beneficiary("beneficiary-1", "customer", "200", BeneficiaryStatus.ACTIVE);
        when(beneficiaryRepository.findByBeneficiaryIdAndUserId("beneficiary-1", "customer"))
                .thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.save(beneficiary)).thenReturn(beneficiary);

        BeneficiaryResponse response = service.disable("beneficiary-1", "customer");

        assertThat(response.getStatus()).isEqualTo(BeneficiaryStatus.DISABLED);
        assertThat(response.getDisabledAt()).isNotNull();
    }

    @Test
    @DisplayName("Rejects access to another user's beneficiary")
    void rejectsAccessToAnotherUsersBeneficiary() {
        when(beneficiaryRepository.findByBeneficiaryIdAndUserId("beneficiary-1", "customer"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("beneficiary-1", "customer"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Beneficiary not found");
    }

    private CheckingAccount account(Long id, String ownerId, String currency) {
        CheckingAccount account = new CheckingAccount();
        account.setId(id);
        account.setOwnerId(ownerId);
        account.setCurrency(currency);
        account.setBalance(BigDecimal.valueOf(100));
        return account;
    }

    private Beneficiary beneficiary(String id, String userId, String destinationAccountId, BeneficiaryStatus status) {
        return Beneficiary.builder()
                .beneficiaryId(id)
                .userId(userId)
                .displayName("Rent")
                .destinationAccountId(destinationAccountId)
                .currency("USD")
                .status(status)
                .build();
    }
}
