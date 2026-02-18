// // src/main/java/com/suhasan/finance/account_service/mapper/AccountMapper.java
// package com.suhasan.finance.account_service.mapper;

// import org.mapstruct.Mapper;
// import org.mapstruct.Mapping;

// import com.suhasan.finance.account_service.entity.Account;
// import com.suhasan.finance.account_service.entity.SavingsAccount;
// import com.suhasan.finance.account_service.entity.CreditCardAccount;
// import com.suhasan.finance.account_service.dto.AccountRequest;
// import com.suhasan.finance.account_service.dto.AccountResponse;

// @Mapper(componentModel = "spring",
//         imports = {SavingsAccount.class, CreditCardAccount.class})
// public interface AccountMapper {

//     // 1) Default factory method for abstract base
//     default Account toEntity(AccountRequest dto) {
//         switch (dto.getAccountType()) {
//             case "SAVINGS":
//                 SavingsAccount sa = new SavingsAccount();
//                 sa.setOwnerId(dto.getOwnerId());
//                 sa.setBalance(dto.getBalance());
//                 sa.setInterestRate(dto.getInterestRate() != null
//                     ? dto.getInterestRate() : 0.0);
//                 return sa;
//             case "CREDIT":
//                 CreditCardAccount ca = new CreditCardAccount();
//                 ca.setOwnerId(dto.getOwnerId());
//                 ca.setBalance(dto.getBalance());
//                 ca.setCreditLimit(dto.getCreditLimit());
//                 ca.setDueDate(dto.getDueDate());
//                 return ca;
//             default:
//                 throw new IllegalArgumentException(
//                   "Unknown accountType: " + dto.getAccountType());
//         }
//     }

//     // 2) MapStruct-generated mapping from entity to DTO
//     @Mapping(target = "accountType",
//              expression = "java(entity instanceof SavingsAccount ? \"SAVINGS\" : \"CREDIT\")")
//     AccountResponse toDto(Account entity);
// }
// src/main/java/com/suhasan/finance/account_service/mapper/AccountMapper.java
package com.suhasan.finance.account_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.SavingsAccount;
import com.suhasan.finance.account_service.entity.CreditCardAccount;
import com.suhasan.finance.account_service.dto.AccountRequest;
import com.suhasan.finance.account_service.dto.AccountResponse;

@Mapper(
    componentModel = "spring",
    imports = { SavingsAccount.class, CreditCardAccount.class }
)
public interface AccountMapper {

    // 1) Default factory method for abstract base
    default Account toEntity(AccountRequest dto) {
        switch (dto.getAccountType()) {
            case "SAVINGS":
                SavingsAccount sa = new SavingsAccount();
                sa.setOwnerId(dto.getOwnerId());
                sa.setBalance(dto.getBalance());
                sa.setInterestRate(dto.getInterestRate() != null
                    ? dto.getInterestRate() : 0.0);
                return sa;
            case "CREDIT":
                CreditCardAccount ca = new CreditCardAccount();
                ca.setOwnerId(dto.getOwnerId());
                ca.setBalance(dto.getBalance());
                ca.setCreditLimit(dto.getCreditLimit());
                ca.setDueDate(dto.getDueDate());
                return ca;
            default:
                throw new IllegalArgumentException(
                  "Unknown accountType: " + dto.getAccountType());
        }
    }

    // 2) MapStruct mapping from entity to DTO, including all fields
    @Mappings({
      @Mapping(
        target = "accountType",
        expression = "java(entity.getAccountType())"
      ),
      @Mapping(
        target = "interestRate",
        expression = "java(entity instanceof SavingsAccount ? ((SavingsAccount)entity).getInterestRate() : null)"
      ),
      @Mapping(
        target = "creditLimit",
        expression = "java(entity instanceof CreditCardAccount ? ((CreditCardAccount)entity).getCreditLimit() : null)"
      ),
      @Mapping(
        target = "dueDate",
        expression = "java(entity instanceof CreditCardAccount ? ((CreditCardAccount)entity).getDueDate() : null)"
      )
    })
    AccountResponse toDto(Account entity);
}
