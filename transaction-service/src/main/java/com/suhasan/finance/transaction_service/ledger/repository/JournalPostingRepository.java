package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.JournalPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface JournalPostingRepository extends JpaRepository<JournalPosting, UUID> {
    List<JournalPosting> findByJournalIdOrderByPostingSequence(UUID journalId);

    @Query(value = """
            select coalesce(sum(case when posting.direction = 'CREDIT'
                then posting.amount else -posting.amount end), 0)
            from journal_postings posting
            join journal_transactions journal on journal.journal_id = posting.journal_id
            where posting.ledger_account_id = :accountId
              and journal.effective_date < :periodStart
              and (select event.state from journal_state_events event
                   where event.journal_id = journal.journal_id
                   order by event.event_sequence desc limit 1) = 'POSTED'
            """, nativeQuery = true)
    BigDecimal postedMovementBefore(
            @Param("accountId") UUID accountId,
            @Param("periodStart") LocalDate periodStart);

    @Query(value = """
            select journal.journal_id as journalId,
                   journal.effective_date as effectiveDate,
                   journal.description as description,
                   sum(case when posting.direction = 'CREDIT'
                       then posting.amount else -posting.amount end) as amount
            from journal_postings posting
            join journal_transactions journal on journal.journal_id = posting.journal_id
            where posting.ledger_account_id = :accountId
              and journal.effective_date >= :periodStart
              and journal.effective_date < :periodEnd
              and (select event.state from journal_state_events event
                   where event.journal_id = journal.journal_id
                   order by event.event_sequence desc limit 1) = 'POSTED'
            group by journal.journal_id, journal.effective_date,
                     journal.created_at, journal.journal_reference, journal.description
            order by journal.effective_date, journal.created_at, journal.journal_reference
            """, nativeQuery = true)
    List<StatementMovementProjection> findPostedStatementMovements(
            @Param("accountId") UUID accountId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
