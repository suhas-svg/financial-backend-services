// src/main/java/com/suhasan/finance/account_service/dto/RegisterResponse.java
package com.suhasan.finance.account_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class RegisterResponse {
    private String username;
    private Set<String> roles;
}
