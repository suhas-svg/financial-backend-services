package com.suhasan.finance.account_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AccountBalanceOperationId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "operation_id", nullable = false, length = 100)
    private String operationId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;
}
