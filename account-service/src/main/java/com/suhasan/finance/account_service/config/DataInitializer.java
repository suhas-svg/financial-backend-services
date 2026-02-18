package com.suhasan.finance.account_service.config;

import com.suhasan.finance.account_service.entity.Role;
import com.suhasan.finance.account_service.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final RoleRepository roleRepository;
    
    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
    }
    
    private void initializeRoles() {
        if (roleRepository.findByName("ROLE_USER").isEmpty()) {
            Role userRole = new Role();
            userRole.setName("ROLE_USER");
            roleRepository.save(userRole);
            log.info("Created default ROLE_USER");
        } else {
            log.info("ROLE_USER already exists");
        }
        
        if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
            Role adminRole = new Role();
            adminRole.setName("ROLE_ADMIN");
            roleRepository.save(adminRole);
            log.info("Created default ROLE_ADMIN");
        } else {
            log.info("ROLE_ADMIN already exists");
        }

        if (roleRepository.findByName("ROLE_INTERNAL_SERVICE").isEmpty()) {
            Role internalRole = new Role();
            internalRole.setName("ROLE_INTERNAL_SERVICE");
            roleRepository.save(internalRole);
            log.info("Created default ROLE_INTERNAL_SERVICE");
        } else {
            log.info("ROLE_INTERNAL_SERVICE already exists");
        }
    }
}
