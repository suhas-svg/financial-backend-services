package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, String>,
        JpaSpecificationExecutor<ScheduledTransfer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduledTransfer s where s.status = 'ACTIVE' and s.nextRunAt <= :now order by s.nextRunAt asc")
    List<ScheduledTransfer> findDueActiveForUpdate(@Param("now") Instant now, Pageable pageable);

    Page<ScheduledTransfer> findByUserId(String userId, Pageable pageable);

    Optional<ScheduledTransfer> findByScheduleIdAndUserId(String scheduleId, String userId);
}
