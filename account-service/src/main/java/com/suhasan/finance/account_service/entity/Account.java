package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;


@Entity
@Table(name = "accounts")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "account_type")
@Getter @Setter @NoArgsConstructor

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "accountType",
  visible = true
)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CheckingAccount.class, name = "CHECKING"),
  @JsonSubTypes.Type(value = SavingsAccount.class,  name = "SAVINGS"),
  @JsonSubTypes.Type(value = CreditCardAccount.class, name = "CREDIT")
})

public abstract class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;


    @NotNull(message = "Owner ID cannot be null")
    @NotBlank(message = "Owner ID cannot be blank")
    @Column(nullable = false)
    private String ownerId;

    @NotNull(message = "Balance cannot be null")
    @PositiveOrZero(message = "Balance must be zero or positive")
    @Column(nullable = false)
    private BigDecimal balance;

    @NotNull(message = "Ledger balance cannot be null")
    @PositiveOrZero(message = "Ledger balance must be zero or positive")
    @Column(name = "ledger_balance", nullable = false)
    private BigDecimal ledgerBalance;

    @NotNull(message = "Available balance cannot be null")
    @PositiveOrZero(message = "Available balance must be zero or positive")
    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance;

    @Column(nullable = false, updatable = false)
    private LocalDate createdAt = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(length = 500)
    private String statusReason;

    private LocalDateTime statusUpdatedAt;

    @Column(length = 100)
    private String statusUpdatedBy;

     /** map the discriminator column so you can query by it */
    @Column(name = "account_type", insertable = false, updatable = false)
    private String accountType;

    @PrePersist
    void ensureDefaults() {
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
        ensureBalanceAliases();
    }

    @PreUpdate
    void ensureBalanceAliases() {
        if (ledgerBalance == null) {
            ledgerBalance = balance;
        }
        if (balance == null) {
            balance = ledgerBalance;
        }
        if (availableBalance == null) {
            availableBalance = ledgerBalance;
        }
        balance = ledgerBalance;
    }

    public BigDecimal getBalance() {
        return ledgerBalance != null ? ledgerBalance : balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
        this.ledgerBalance = balance;
        if (this.availableBalance == null) {
            this.availableBalance = balance;
        }
    }
}
