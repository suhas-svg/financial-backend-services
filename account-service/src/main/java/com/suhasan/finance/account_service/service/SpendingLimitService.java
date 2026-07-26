package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.*;
import com.suhasan.finance.account_service.entity.*;
import com.suhasan.finance.account_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Locale;

@Service @RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // Stable API error text intentionally repeats across operations.
public class SpendingLimitService {
 private final AccountRepository accounts; private final AccountSpendingLimitRepository limits; private final SpendingLimitReservationRepository reservations; private final SpendingLimitAuditEventRepository audits; private final MfaService mfa; private final NotificationService notifications;
 @Transactional(readOnly=true) public List<SpendingLimitDtos.LimitResponse> list(final String user){ return accounts.findAll().stream().filter(a->user.equals(a.getOwnerId())).map(a->view(a.getId(),limits.findById(a.getId()).orElse(null))).toList(); }
 @Transactional public SpendingLimitDtos.LimitResponse update(final Long accountId,final String user,final SpendingLimitDtos.UpdateRequest r){
  final Account a=accounts.findById(accountId).orElseThrow(()->new IllegalArgumentException("Account not found")); if(!user.equals(a.getOwnerId())) throw new AccessDeniedException("Account ownership required");
  final AccountSpendingLimit l=lockedOrCreate(accountId,user); applyDue(l);
  final boolean transferIncrease=r.transferDailyLimit().compareTo(l.getTransferDailyLimit())>0, withdrawalIncrease=r.withdrawalDailyLimit().compareTo(l.getWithdrawalDailyLimit())>0, increase=transferIncrease||withdrawalIncrease;
  if(increase){
   if(r.credential()==null||r.credential().isBlank()||!mfa.verifyCredential(mfa.activeMethod(user),r.credential())) throw new IllegalArgumentException("Valid MFA credential required for limit increases");
   if(!transferIncrease) l.setTransferDailyLimit(r.transferDailyLimit());
   if(!withdrawalIncrease) l.setWithdrawalDailyLimit(r.withdrawalDailyLimit());
   l.setPendingTransferDailyLimit(transferIncrease?r.transferDailyLimit():l.getTransferDailyLimit());
   l.setPendingWithdrawalDailyLimit(withdrawalIncrease?r.withdrawalDailyLimit():l.getWithdrawalDailyLimit());
   l.setPendingEffectiveAt(LocalDateTime.now().plusHours(24)); audit(accountId,user,"LIMIT_INCREASE_SCHEDULED",null,null,null,null,"Increases verified and cooling; any reductions applied immediately");
  } else { l.setTransferDailyLimit(r.transferDailyLimit()); l.setWithdrawalDailyLimit(r.withdrawalDailyLimit()); l.setPendingTransferDailyLimit(null); l.setPendingWithdrawalDailyLimit(null); l.setPendingEffectiveAt(null); audit(accountId,user,"LIMIT_REDUCED",null,null,null,null,"Reduction applied immediately"); }
  l.setUpdatedAt(LocalDateTime.now()); l.setUpdatedBy(user); limits.save(l); notify(user,accountId,increase?"Limit increase scheduled":"Spending limits updated",increase?"Your verified increase will take effect after the 24-hour cooling period.":"Your lower limits are effective immediately.","limit-change-"+accountId+"-"+l.getUpdatedAt()); return view(accountId,l);
 }
 @Transactional public SpendingLimitDtos.ReserveResponse reserve(final Long accountId,final SpendingLimitDtos.ReserveRequest r){
  final Account a=accounts.findById(accountId).orElseThrow(()->new IllegalArgumentException("Account not found")); if(!a.getOwnerId().equals(r.userId())) throw new AccessDeniedException("Account ownership required"); final String type=r.operationType().toUpperCase(Locale.ROOT); if(!type.equals("TRANSFER")&&!type.equals("WITHDRAWAL")) throw new IllegalArgumentException("Unsupported limit operation");
  final AccountSpendingLimit l=lockedOrCreate(accountId,r.userId()); applyDue(l); final BigDecimal limit=type.equals("TRANSFER")?l.getTransferDailyLimit():l.getWithdrawalDailyLimit(); final BigDecimal used=reservations.used(accountId,type,LocalDate.now());
  if(reservations.existsByAccountIdAndOperationTypeAndIdempotencyKey(accountId,type,r.idempotencyKey())) return new SpendingLimitDtos.ReserveResponse(true,true,limit,used,limit.subtract(used).max(BigDecimal.ZERO),null);
  final BigDecimal projected=used.add(r.amount()); if(projected.compareTo(limit)>0){ audit(accountId,r.userId(),"LIMIT_REJECTED",type,r.amount(),limit,used,"Daily spending limit exceeded"); notify(r.userId(),accountId,"Operation rejected","A "+type.toLowerCase(Locale.ROOT)+" was rejected because it exceeds your daily limit.","limit-reject-"+accountId+"-"+type+"-"+r.idempotencyKey()); return new SpendingLimitDtos.ReserveResponse(false,false,limit,used,limit.subtract(used).max(BigDecimal.ZERO),"Daily spending limit exceeded"); }
  reservations.save(SpendingLimitReservation.builder().accountId(accountId).operationType(type).amount(r.amount()).usageDate(LocalDate.now()).idempotencyKey(r.idempotencyKey()).createdAt(LocalDateTime.now()).build()); audit(accountId,r.userId(),projected.compareTo(limit.multiply(new BigDecimal("0.80")))>=0?"LIMIT_APPROACHING":"LIMIT_ENFORCED",type,r.amount(),limit,projected,"Daily usage reserved before debit processing"); if(projected.compareTo(limit.multiply(new BigDecimal("0.80")))>=0) notify(r.userId(),accountId,"Approaching daily limit","Your "+type.toLowerCase(Locale.ROOT)+" usage has reached at least 80% of today's limit.","limit-near-"+accountId+"-"+type+"-"+LocalDate.now()); return new SpendingLimitDtos.ReserveResponse(true,false,limit,projected,limit.subtract(projected),null);
 }
 @Transactional public boolean release(final Long accountId,final String operationType,final String idempotencyKey,final String userId){
  final Account a=accounts.findByIdForUpdate(accountId).orElseThrow(()->new IllegalArgumentException("Account not found")); if(!a.getOwnerId().equals(userId)) throw new AccessDeniedException("Account ownership required"); final String type=operationType.toUpperCase(Locale.ROOT);
  return reservations.findByAccountIdAndOperationTypeAndIdempotencyKey(accountId,type,idempotencyKey).map(r->{ reservations.delete(r); audit(accountId,userId,"LIMIT_RESERVATION_RELEASED",type,r.getAmount(),null,null,"Downstream debit processing failed; daily allowance restored"); return true; }).orElse(false);
 }
 @Transactional(readOnly=true) public List<SpendingLimitDtos.AuditResponse> auditEvents(){ return audits.findAll().stream().sorted((a,b)->b.getCreatedAt().compareTo(a.getCreatedAt())).limit(200).map(e->new SpendingLimitDtos.AuditResponse(e.getEventId(),e.getAccountId(),e.getUserId(),e.getEventType(),e.getOperationType(),e.getAmount(),e.getDailyLimit(),e.getDailyUsed(),e.getDetails(),e.getCreatedAt())).toList(); }
 private AccountSpendingLimit lockedOrCreate(final Long id,final String user){ accounts.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Account not found")); return limits.lock(id).orElseGet(()->{ final AccountSpendingLimit l=new AccountSpendingLimit(); l.setAccountId(id); l.setUpdatedAt(LocalDateTime.now()); l.setUpdatedBy(user); return limits.saveAndFlush(l); }); }
 private void applyDue(final AccountSpendingLimit l){ if(l.getPendingEffectiveAt()!=null&&!l.getPendingEffectiveAt().isAfter(LocalDateTime.now())){ l.setTransferDailyLimit(l.getPendingTransferDailyLimit()); l.setWithdrawalDailyLimit(l.getPendingWithdrawalDailyLimit()); l.setPendingTransferDailyLimit(null); l.setPendingWithdrawalDailyLimit(null); l.setPendingEffectiveAt(null); limits.save(l); } }
 private SpendingLimitDtos.LimitResponse view(final Long id,final AccountSpendingLimit l){ final BigDecimal t=l==null?new BigDecimal("10000.00"):l.getTransferDailyLimit(),w=l==null?new BigDecimal("2000.00"):l.getWithdrawalDailyLimit(); return new SpendingLimitDtos.LimitResponse(id,t,w,reservations.used(id,"TRANSFER",LocalDate.now()),reservations.used(id,"WITHDRAWAL",LocalDate.now()),l==null?null:l.getPendingTransferDailyLimit(),l==null?null:l.getPendingWithdrawalDailyLimit(),l==null?null:l.getPendingEffectiveAt()); }
 private void audit(final Long a,final String u,final String e,final String o,final BigDecimal amount,final BigDecimal limit,final BigDecimal used,final String d){ audits.save(SpendingLimitAuditEvent.builder().accountId(a).userId(u).eventType(e).operationType(o).amount(amount).dailyLimit(limit).dailyUsed(used).details(d).createdAt(LocalDateTime.now()).build()); }
 private void notify(final String user,final Long account,final String title,final String message,final String key){ notifications.createInternal(NotificationCreateRequest.builder().userId(user).type(NotificationType.SECURITY_ALERT).severity(NotificationSeverity.WARNING).title(title).message(message).sourceType(NotificationSourceType.ACCOUNT).sourceId(String.valueOf(account)).dedupeKey(key).build()); }
}
