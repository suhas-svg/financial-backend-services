package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@DiscriminatorValue("CREDIT")
@Getter @Setter @NoArgsConstructor 
public class CreditCardAccount extends Account {
    @NotNull @PositiveOrZero(message = "Credit limit must be zero or positive")
    @Column(nullable = true)
    private BigDecimal creditLimit;

    @Future(message = "Due date must be in the future")
    @Column(nullable = true)
    private LocalDate dueDate;
}
