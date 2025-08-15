package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("CHECKING")
public class CheckingAccount extends Account { }
