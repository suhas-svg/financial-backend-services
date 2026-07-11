package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.SpendingLimitDtos;
import com.suhasan.finance.account_service.entity.*;
import com.suhasan.finance.account_service.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpendingLimitServiceTest {
 @Mock AccountRepository accounts; @Mock AccountSpendingLimitRepository limits; @Mock SpendingLimitReservationRepository reservations; @Mock SpendingLimitAuditEventRepository audits; @Mock MfaService mfa; @Mock NotificationService notifications;
 SpendingLimitService service;
 @BeforeEach void setUp(){ service=new SpendingLimitService(accounts,limits,reservations,audits,mfa,notifications); }
 @Test void reservesUsageOnceAndRejectsAmountAboveRemainingDailyLimit(){
  CheckingAccount account=new CheckingAccount(); account.setOwnerId("alice");
  AccountSpendingLimit limit=new AccountSpendingLimit(); limit.setAccountId(7L); limit.setTransferDailyLimit(new BigDecimal("100.00")); limit.setWithdrawalDailyLimit(new BigDecimal("50.00")); limit.setUpdatedAt(LocalDateTime.now()); limit.setUpdatedBy("alice");
  when(accounts.findById(7L)).thenReturn(Optional.of(account)); when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account)); when(limits.lock(7L)).thenReturn(Optional.of(limit)); when(reservations.used(7L,"TRANSFER",java.time.LocalDate.now())).thenReturn(new BigDecimal("80.00")); when(audits.save(any())).thenAnswer(i->i.getArgument(0));
  SpendingLimitDtos.ReserveResponse response=service.reserve(7L,new SpendingLimitDtos.ReserveRequest("TRANSFER",new BigDecimal("25.00"),"key-1","alice"));
  assertThat(response.allowed()).isFalse(); assertThat(response.remaining()).isEqualByComparingTo("20.00"); verify(reservations,never()).save(any()); verify(notifications).createInternal(any());
 }
 @Test void idempotentReplayDoesNotReserveAgain(){
  CheckingAccount account=new CheckingAccount(); account.setOwnerId("alice"); AccountSpendingLimit limit=new AccountSpendingLimit(); limit.setAccountId(7L); limit.setTransferDailyLimit(new BigDecimal("100.00")); limit.setWithdrawalDailyLimit(new BigDecimal("50.00")); limit.setUpdatedAt(LocalDateTime.now()); limit.setUpdatedBy("alice");
  when(accounts.findById(7L)).thenReturn(Optional.of(account)); when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account)); when(limits.lock(7L)).thenReturn(Optional.of(limit)); when(reservations.used(7L,"TRANSFER",java.time.LocalDate.now())).thenReturn(new BigDecimal("25.00")); when(reservations.existsByAccountIdAndOperationTypeAndIdempotencyKey(7L,"TRANSFER","key-1")).thenReturn(true);
  SpendingLimitDtos.ReserveResponse response=service.reserve(7L,new SpendingLimitDtos.ReserveRequest("TRANSFER",new BigDecimal("25.00"),"key-1","alice"));
  assertThat(response.allowed()).isTrue(); assertThat(response.replay()).isTrue(); verify(reservations,never()).save(any());
 }
 @Test void mixedUpdateAppliesReductionImmediatelyAndCoolsOnlyIncrease(){
  CheckingAccount account=new CheckingAccount(); account.setOwnerId("alice"); AccountSpendingLimit limit=new AccountSpendingLimit(); limit.setAccountId(7L); limit.setTransferDailyLimit(new BigDecimal("100.00")); limit.setWithdrawalDailyLimit(new BigDecimal("50.00")); limit.setUpdatedAt(LocalDateTime.now()); limit.setUpdatedBy("alice"); MfaMethod method=new MfaMethod();
  when(accounts.findById(7L)).thenReturn(Optional.of(account)); when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account)); when(limits.lock(7L)).thenReturn(Optional.of(limit)); when(mfa.activeMethod("alice")).thenReturn(method); when(mfa.verifyCredential(method,"123456")).thenReturn(true); when(limits.save(any())).thenAnswer(i->i.getArgument(0)); when(audits.save(any())).thenAnswer(i->i.getArgument(0));
  SpendingLimitDtos.LimitResponse response=service.update(7L,"alice",new SpendingLimitDtos.UpdateRequest(new BigDecimal("80.00"),new BigDecimal("75.00"),"123456"));
  assertThat(response.transferDailyLimit()).isEqualByComparingTo("80.00"); assertThat(response.withdrawalDailyLimit()).isEqualByComparingTo("50.00"); assertThat(response.pendingTransferDailyLimit()).isEqualByComparingTo("80.00"); assertThat(response.pendingWithdrawalDailyLimit()).isEqualByComparingTo("75.00"); assertThat(response.pendingEffectiveAt()).isAfter(LocalDateTime.now().plusHours(23));
 }
 @Test void downstreamFailureReleasesReservedDailyUsageIdempotently(){
  CheckingAccount account=new CheckingAccount(); account.setOwnerId("alice"); SpendingLimitReservation reservation=SpendingLimitReservation.builder().accountId(7L).operationType("WITHDRAWAL").amount(new BigDecimal("25.00")).idempotencyKey("key-1").build();
  when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account)); when(reservations.findByAccountIdAndOperationTypeAndIdempotencyKey(7L,"WITHDRAWAL","key-1")).thenReturn(Optional.of(reservation),Optional.empty()); when(audits.save(any())).thenAnswer(i->i.getArgument(0));
  assertThat(service.release(7L,"WITHDRAWAL","key-1","alice")).isTrue(); assertThat(service.release(7L,"WITHDRAWAL","key-1","alice")).isFalse(); verify(reservations,times(1)).delete(reservation);
 }
}
