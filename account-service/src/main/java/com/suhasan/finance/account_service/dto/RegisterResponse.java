package com.suhasan.finance.account_service.dto;

import java.util.HashSet;
import java.util.Set;

public class RegisterResponse {
    private String username;
    private Set<String> roles;

    public RegisterResponse(final String username, final Set<String> roles) {
        this.username = username;
        this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public Set<String> getRoles() {
        return new HashSet<>(roles);
    }

    public void setRoles(final Set<String> roles) {
        this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
    }
}
