package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MonthlyStatementService {

    private final CustomerMonthlyStatementRepository statementRepository;
    private final CustomerMonthlyStatementLineRepository lineRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final ResilientAccountServiceClient accountServiceClient;
    private final AccountLedgerResolver accountLedgerResolver;
    private final JournalPostingRepository postingRepository;

    public MonthlyStatementService(
            CustomerMonthlyStatementRepository statementRepository,
            CustomerMonthlyStatementLineRepository lineRepository,
            LedgerAccountRepository ledgerAccountRepository,
            LedgerBalanceProjectionRepository projectionRepository,
            ResilientAccountServiceClient accountServiceClient,
            AccountLedgerResolver accountLedgerResolver,
            JournalPostingRepository postingRepository) {
        this.statementRepository = statementRepository;
        this.lineRepository = lineRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.projectionRepository = projectionRepository;
        this.accountServiceClient = accountServiceClient;
        this.accountLedgerResolver = accountLedgerResolver;
        this.postingRepository = postingRepository;
    }

    @Transactional
    public CustomerMonthlyStatementResult generate(String ownerId, String externalAccountId, YearMonth period) {
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.plusMonths(1).atDay(1);
        var latest = statementRepository.findLatestByOwnerAndAccountAndPeriod(
                ownerId, externalAccountId, periodStart, periodEnd);
        LedgerAccount account = resolveLedgerAccount(ownerId, externalAccountId);
        List<StatementMovementProjection> movements = postingRepository.findPostedStatementMovements(
                account.getLedgerAccountId(), periodStart, periodEnd);

        if (latest.isPresent()) {
            CustomerMonthlyStatement existing = latest.get();
            List<CustomerMonthlyStatementLine> existingLines =
                    lineRepository.findByStatementIdOrderByLineSequence(existing.getStatementId());
            if (matchesPostedMovements(existingLines, movements)) {
                return toResult(existing, existingLines);
            }
        }

        int nextVersion = latest.map(statement -> statement.getStatementVersion() + 1).orElse(1);
        return generateNew(account, periodStart, periodEnd, nextVersion, movements);
    }

    @Transactional(readOnly = true)
    public List<CustomerMonthlyStatementResult> listForOwner(String ownerId) {
        return statementRepository.findByOwnerIdOrderByPeriodStartDescExternalAccountIdAscStatementVersionDesc(
                        ownerId, PageRequest.of(0, 100))
                .stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerMonthlyStatementResult getForOwner(String ownerId, UUID statementId) {
        CustomerMonthlyStatement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new IllegalArgumentException("Statement not found"));
        if (!ownerId.equals(statement.getOwnerId())) {
            throw new AccessDeniedException("Statement belongs to another customer");
        }
        return toResult(statement);
    }

    @Transactional(readOnly = true)
    public String exportCsvForOwner(String ownerId, UUID statementId) {
        return toCsv(getForOwner(ownerId, statementId));
    }

    private CustomerMonthlyStatementResult generateNew(
            LedgerAccount account,
            LocalDate periodStart,
            LocalDate periodEnd,
            int statementVersion,
            List<StatementMovementProjection> movements) {

        BigDecimal currentBalance = projectionRepository.findById(account.getLedgerAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Ledger projection not found"))
                .getPostedBalance();
        BigDecimal openingBalance = currentBalance.subtract(postingRepository.postedMovementFrom(
                account.getLedgerAccountId(), periodStart));
        BigDecimal runningBalance = openingBalance;
        List<CustomerMonthlyStatementLine> lines = new ArrayList<>();
        CustomerMonthlyStatement statement = CustomerMonthlyStatement.create(
                account,
                periodStart,
                periodEnd,
                statementVersion,
                openingBalance,
                openingBalance.add(movements.stream()
                        .map(StatementMovementProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        statementRepository.save(statement);

        int sequence = 1;
        for (StatementMovementProjection movement : movements) {
            runningBalance = runningBalance.add(movement.getAmount());
            lines.add(CustomerMonthlyStatementLine.create(
                    statement.getStatementId(),
                    movement.getJournalId(),
                    sequence++,
                    movement.getEffectiveDate(),
                    movement.getDescription(),
                    movement.getAmount(),
                    runningBalance,
                    account.getCurrency()));
        }
        List<CustomerMonthlyStatementLine> savedLines = lineRepository.saveAll(lines);
        return toResult(statement, savedLines);
    }

    private boolean matchesPostedMovements(
            List<CustomerMonthlyStatementLine> existingLines,
            List<StatementMovementProjection> currentMovements) {
        if (existingLines.size() != currentMovements.size()) {
            return false;
        }
        return existingLines.stream().allMatch(line -> currentMovements.stream().anyMatch(movement ->
                line.getJournalId().equals(movement.getJournalId())
                        && line.getAmount().compareTo(movement.getAmount()) == 0));
    }

    private LedgerAccount resolveLedgerAccount(String ownerId, String externalAccountId) {
        return ledgerAccountRepository.findByExternalAccountId(externalAccountId)
                .filter(candidate -> ownerId.equals(candidate.getOwnerId()))
                .orElseGet(() -> {
                    AccountDto account = accountServiceClient.getAccount(externalAccountId);
                    if (account == null || !ownerId.equals(account.getOwnerId())) {
                        throw new IllegalArgumentException("Ledger account not found");
                    }
                    UUID ledgerAccountId = accountLedgerResolver.resolveCustomerAccount(externalAccountId, account);
                    return ledgerAccountRepository.findById(ledgerAccountId)
                            .filter(candidate -> ownerId.equals(candidate.getOwnerId()))
                            .orElseThrow(() -> new IllegalArgumentException("Ledger account not found"));
                });
    }

    private CustomerMonthlyStatementResult toResult(CustomerMonthlyStatement statement) {
        return toResult(statement, lineRepository.findByStatementIdOrderByLineSequence(statement.getStatementId()));
    }

    private CustomerMonthlyStatementResult toResult(
            CustomerMonthlyStatement statement,
            List<CustomerMonthlyStatementLine> lines) {
        return new CustomerMonthlyStatementResult(
                statement.getStatementId(),
                statement.getOwnerId(),
                statement.getExternalAccountId(),
                statement.getCurrency(),
                statement.getPeriodStart(),
                statement.getPeriodEnd(),
                statement.getStatementVersion(),
                statement.getOpeningBalance(),
                statement.getClosingBalance(),
                statement.getGeneratedAt(),
                lines.stream().map(this::toLineResult).toList());
    }

    private CustomerMonthlyStatementLineResult toLineResult(CustomerMonthlyStatementLine line) {
        return new CustomerMonthlyStatementLineResult(
                line.getLineId(),
                line.getJournalId(),
                line.getLineSequence(),
                line.getEffectiveDate(),
                line.getDescription(),
                line.getAmount(),
                line.getRunningBalance(),
                line.getCurrency());
    }

    private String toCsv(CustomerMonthlyStatementResult statement) {
        StringBuilder csv = new StringBuilder("statementId,externalAccountId,periodStart,periodEnd,lineDate,description,amount,runningBalance,currency\n");
        for (CustomerMonthlyStatementLineResult line : statement.lines()) {
            csv.append(csv(statement.statementId().toString())).append(',')
                    .append(csv(statement.externalAccountId())).append(',')
                    .append(csv(statement.periodStart().toString())).append(',')
                    .append(csv(statement.periodEnd().toString())).append(',')
                    .append(csv(line.effectiveDate().toString())).append(',')
                    .append(csv(line.description())).append(',')
                    .append(csv(line.amount().toPlainString())).append(',')
                    .append(csv(line.runningBalance().toPlainString())).append(',')
                    .append(csv(line.currency())).append('\n');
        }
        return csv.toString();
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

}
