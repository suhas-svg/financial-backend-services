package com.suhasan.finance.account_service.sandbox;

import com.suhasan.finance.account_service.entity.Role;
import com.suhasan.finance.account_service.entity.SyntheticSandboxBootstrap;
import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.repository.RoleRepository;
import com.suhasan.finance.account_service.repository.SyntheticSandboxBootstrapRepository;
import com.suhasan.finance.account_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;

@Service
public class SyntheticSandboxBootstrapService {
    private static final short SINGLETON_ID = 1;
    private final SyntheticSandboxGuard guard;
    private final SyntheticSandboxBootstrapRepository bootstrapRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapToken;

    public SyntheticSandboxBootstrapService(final SyntheticSandboxGuard guard,
            final SyntheticSandboxBootstrapRepository bootstrapRepository, final UserRepository userRepository,
            final RoleRepository roleRepository, final PasswordEncoder passwordEncoder,
            @Value("${sandbox.bootstrap.token:}") final String bootstrapToken) {
        this.guard = guard;
        this.bootstrapRepository = bootstrapRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapToken = bootstrapToken;
    }

    public BootstrapStatus status() {
        guard.requireSynthetic();
        return bootstrapRepository.findById(SINGLETON_ID)
                .map(state -> new BootstrapStatus(false, true, state.getOperatorUsername()))
                .orElseGet(() -> new BootstrapStatus(true, false, null));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BootstrapStatus bootstrap(final String suppliedToken, final String username, final String password) {
        guard.requireSynthetic();
        requireToken(suppliedToken);
        if (bootstrapRepository.existsById(SINGLETON_ID)) {
            throw new IllegalStateException("Synthetic sandbox operator bootstrap has already completed");
        }
        if (username == null || !username.matches("[A-Za-z][A-Za-z0-9._-]{4,63}")) {
            throw new IllegalArgumentException("Operator username must be 5-64 safe characters");
        }
        if (password == null || password.length() < 14 || password.length() > 200) {
            throw new IllegalArgumentException("Operator password must be between 14 and 200 characters");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Requested operator username already exists");
        }
        final Role admin = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> saveRole("ROLE_ADMIN"));
        final Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> saveRole("ROLE_USER"));
        final User operator = new User();
        operator.setUsername(username);
        operator.setPassword(passwordEncoder.encode(password));
        operator.setRoles(Set.of(admin, userRole));
        userRepository.save(operator);

        final SyntheticSandboxBootstrap state = new SyntheticSandboxBootstrap();
        state.setSingletonId(SINGLETON_ID);
        state.setOperatorUsername(username);
        state.setCompletedAt(Instant.now());
        bootstrapRepository.saveAndFlush(state);
        return new BootstrapStatus(false, true, username);
    }

    private Role saveRole(final String name) {
        final Role role = new Role();
        role.setName(name);
        return roleRepository.save(role);
    }

    private void requireToken(final String suppliedToken) {
        if (bootstrapToken.isBlank() || suppliedToken == null || suppliedToken.isBlank()
                || !MessageDigest.isEqual(bootstrapToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid bootstrap authorization");
        }
    }

    public record BootstrapStatus(boolean setupRequired, boolean completed, String operatorUsername) {}
}
