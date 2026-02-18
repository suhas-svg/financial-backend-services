// package com.suhasan.finance.account_service.repository;

// import com.suhasan.finance.account_service.entity.Account;
// import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.Pageable;

// public interface AccountRepository extends JpaRepository<Account, Long> {
//      // page through accounts by owner
//   Page<Account> findByOwnerId(String ownerId, Pageable pageable);

//   // page through accounts by type
//   Page<Account> findByAccountType(String accountType, Pageable pageable);
// }

package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // page through accounts by owner
    Page<Account> findByOwnerId(String ownerId, Pageable pageable);

    // page through accounts by type
    Page<Account> findByAccountType(String accountType, Pageable pageable);

    Page<Account> findByOwnerIdAndAccountType(String ownerId, String accountType, Pageable pageable);

    Optional<Account> findByIdAndOwnerId(Long id, String ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
