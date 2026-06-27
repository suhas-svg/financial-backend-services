package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
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
    private final JournalTransactionRepository journalRepository;
    private final JournalPostingRepository postingRepository;
    private final JournalStateEventRepository stateEventRepository;

    public MonthlyStatementService(
            CustomerMonthlyStatementRepository statementRepository,
            CustomerMonthlyStatementLineRepository lineRepository,
            LedgerAccountRepository ledgerAccountRepository,
            JournalTransactionRepository journalRepository,
            JournalPostingRepository postingRepository,
            JournalStateEventRepository stateEventRepository) {
        this.statementRepository = statementRepository;
        this.lineRepository = lineRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.journalRepository = journalRepository;
        this.postingRepository = postingRepository;
        this.stateEventRepository = stateEventRepository;
    }

    @Transactional
    public CustomerMonthlyStatementResult generate(String ownerId, String externalAccountId, YearMonth period) {
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.plusMonths(1).atDay(1);
        return statementRepository
                .findLatestByOwnerAndAccountAndPeriod(ownerId, externalAccountId, periodStart, periodEnd)
                .map(this::toResult)
                .orElseGet(() -> generateNew(ownerId, externalAccountId, periodStart, periodEnd));
    }

    @Transactional(readOnly = true)
    public List<CustomerMonthlyStatementResult> listForOwner(String ownerId) {
        return statementRepository.findByOwnerIdOrderByPeriodStartDescExternalAccountIdAscStatementVersionDesc(ownerId)
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
            String ownerId,
            String externalAccountId,
            LocalDate periodStart,
            LocalDate periodEnd) {
        LedgerAccount account = ledgerAccountRepository.findByExternalAccountId(externalAccountId)
                .filter(candidate -> ownerId.equals(candidate.getOwnerId()))
                .orElseThrow(() -> new IllegalArgumentException("Ledger account not found"));

        BigDecimal openingBalance = BigDecimal.ZERO;
        List<StatementMovement> movements = new ArrayList<>();

        for (JournalTransaction journal : journalRepository.findAllByEffectiveDateLessThan(periodEnd).stream()
                .sorted(Comparator
                        .comparing(JournalTransaction::getEffectiveDate)
                        .thenComparing(JournalTransaction::getCreatedAt)
                        .thenComparing(JournalTransaction::getJournalReference))
                .toList()) {
            if (!isPosted(journal.getJournalId())) {
                continue;
            }
            BigDecimal amount = signedAmountForAccount(journal.getJournalId(), account.getLedgerAccountId());
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            if (journal.getEffectiveDate().isBefore(periodStart)) {
                openingBalance = openingBalance.add(amount);
            } else {
                movements.add(new StatementMovement(journal, amount));
            }
        }

        BigDecimal runningBalance = openingBalance;
        List<CustomerMonthlyStatementLine> lines = new ArrayList<>();
        CustomerMonthlyStatement statement = CustomerMonthlyStatement.create(
                account,
                periodStart,
                periodEnd,
                openingBalance,
                openingBalance.add(movements.stream()
                        .map(StatementMovement::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        statementRepository.save(statement);

        int sequence = 1;
        for (StatementMovement movement : movements) {
            runningBalance = runningBalance.add(movement.amount());
            lines.add(CustomerMonthlyStatementLine.create(
                    statement.getStatementId(),
                    movement.journal().getJournalId(),
                    sequence++,
                    movement.journal().getEffectiveDate(),
                    movement.journal().getDescription(),
                    movement.amount(),
                    runningBalance,
                    account.getCurrency()));
        }
        List<CustomerMonthlyStatementLine> savedLines = lineRepository.saveAll(lines);
        return toResult(statement, savedLines);
    }

    private boolean isPosted(UUID journalId) {
        return stateEventRepository.findFirstByJournalIdOrderByEventSequenceDesc(journalId)
                .map(event -> event.getState() == JournalState.POSTED)
                .orElse(false);
    }

    private BigDecimal signedAmountForAccount(UUID journalId, UUID ledgerAccountId) {
        return postingRepository.findByJournalIdOrderByPostingSequence(journalId).stream()
                .filter(posting -> ledgerAccountId.equals(posting.getLedgerAccountId()))
                .map(posting -> posting.getDirection() == PostingDirection.CREDIT
                        ? posting.getAmount()
                        : posting.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private record StatementMovement(JournalTransaction journal, BigDecimal amount) {
    }
}
