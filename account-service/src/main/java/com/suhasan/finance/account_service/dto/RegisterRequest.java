// src/main/java/com/suhasan/finance/account_service/dto/RegisterRequest.java
package com.suhasan.finance.account_service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;  // in your AccountRequest/Response
import java.time.LocalDate;   // if used
import java.util.Set;         // in RegisterResponse

@Data
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
