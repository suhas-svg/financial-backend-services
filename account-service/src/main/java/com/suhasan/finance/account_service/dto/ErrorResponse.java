// src/main/java/com/suhasan/finance/account_service/dto/ErrorResponse.java
package com.suhasan.finance.account_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String error;      // e.g. "Validation Failed"
    private String message;    // human-readable details
    private String path;       // request URI
    private int status;        // HTTP status code
}
