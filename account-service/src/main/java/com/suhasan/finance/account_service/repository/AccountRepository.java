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

public interface AccountRepository extends JpaRepository<Account, Long> {

    // page through accounts by owner
    Page<Account> findByOwnerId(String ownerId, Pageable pageable);

    // page through accounts by type
    Page<Account> findByAccountType(String accountType, Pageable pageable);

    
}
