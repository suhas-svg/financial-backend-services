// src/main/java/com/suhasan/finance/account_service/service/AuthService.java
package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.*;
import com.suhasan.finance.account_service.entity.*;
import com.suhasan.finance.account_service.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    
    // Micrometer metrics for authentication
    private final Counter registrationCounter;
    private final Counter registrationFailedCounter;
    private final Timer registrationTimer;

    public AuthService(UserRepository userRepo,
                      RoleRepository roleRepo,
                      PasswordEncoder passwordEncoder,
                      MeterRegistry registry) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        
        // Initialize custom business metrics
        this.registrationCounter = registry.counter("auth_registration_total");
        this.registrationFailedCounter = registry.counter("auth_registration_failed_total");
        this.registrationTimer = registry.timer("auth_registration_duration");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        return registrationTimer.record(() -> {
            try {
                // 1) Check uniqueness
                if (userRepo.findByUsername(req.getUsername()).isPresent()) {
                    registrationFailedCounter.increment();
                    throw new IllegalArgumentException("Username already taken");
                }
                // 2) Load or create ROLE_USER
                Role userRole = roleRepo.findByName("ROLE_USER")
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setName("ROLE_USER");
                        return roleRepo.save(r);
                    });
                // 3) Build & save User
                User user = new User();
                user.setUsername(req.getUsername());
                user.setPassword(passwordEncoder.encode(req.getPassword()));
                user.setRoles(Set.of(userRole));
                User saved = userRepo.save(user);

                // 4) Return safe DTO
                Set<String> roles = saved.getRoles()
                                         .stream()
                                         .map(Role::getName)
                                         .collect(Collectors.toSet());
                
                registrationCounter.increment();
                return new RegisterResponse(saved.getUsername(), roles);
            } catch (Exception e) {
                registrationFailedCounter.increment();
                throw e;
            }
        });
    }
}
