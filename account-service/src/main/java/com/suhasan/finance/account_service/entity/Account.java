package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    

    @NotNull(message = "Owner ID cannot be null")
    @NotBlank(message = "Owner ID cannot be blank")
    @Column(nullable = false)
    private String ownerId;

    @NotNull(message = "Balance cannot be null")
    @PositiveOrZero(message = "Balance must be zero or positive")
    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false, updatable = false)
    private LocalDate createdAt = LocalDate.now();

     /** map the discriminator column so you can query by it */
    @Column(name = "account_type", insertable = false, updatable = false)
    private String accountType;
}
