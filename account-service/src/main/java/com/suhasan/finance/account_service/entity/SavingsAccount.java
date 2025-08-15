package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
@Entity
@DiscriminatorValue("SAVINGS")
@Getter @Setter @NoArgsConstructor
public class SavingsAccount extends Account {
    @Column(nullable = true)
    @PositiveOrZero(message = "Interest rate must be zero or positive")
    private double interestRate;
}
