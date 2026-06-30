package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.ScheduledTransferRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ScheduledTransferRunRepository extends JpaRepository<ScheduledTransferRun, String> {

    boolean existsByScheduleScheduleIdAndScheduledFor(String scheduleId, Instant scheduledFor);

    Page<ScheduledTransferRun> findByScheduleScheduleIdOrderByScheduledForDesc(String scheduleId, Pageable pageable);
}
