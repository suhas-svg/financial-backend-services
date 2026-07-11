package com.suhasan.finance.account_service.repository;
import com.suhasan.finance.account_service.entity.AccountSpendingLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface AccountSpendingLimitRepository extends JpaRepository<AccountSpendingLimit,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from AccountSpendingLimit l where l.accountId=:id") Optional<AccountSpendingLimit> lock(@Param("id") Long id);
}
