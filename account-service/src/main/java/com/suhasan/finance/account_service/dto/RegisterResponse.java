package com.suhasan.finance.account_service.dto;

import java.util.HashSet;
import java.util.Set;

public class RegisterResponse {
    private String username;
    private Set<String> roles;

    public RegisterResponse(String username, Set<String> roles) {
        this.username = username;
        setRoles(roles);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Set<String> getRoles() {
        return new HashSet<>(roles);
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
    }
}
