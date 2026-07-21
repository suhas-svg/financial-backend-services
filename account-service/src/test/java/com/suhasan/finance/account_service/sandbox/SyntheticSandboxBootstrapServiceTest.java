package com.suhasan.finance.account_service.sandbox;

import com.suhasan.finance.account_service.entity.Role;
import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.repository.RoleRepository;
import com.suhasan.finance.account_service.repository.SyntheticSandboxBootstrapRepository;
import com.suhasan.finance.account_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyntheticSandboxBootstrapServiceTest {
    private SyntheticSandboxGuard guard;
    private SyntheticSandboxBootstrapRepository bootstrapRepository;
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private SyntheticSandboxBootstrapService service;

    @BeforeEach
    void setUp() {
        guard = mock(SyntheticSandboxGuard.class);
        bootstrapRepository = mock(SyntheticSandboxBootstrapRepository.class);
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        Role admin = new Role(); admin.setName("ROLE_ADMIN");
        Role user = new Role(); user.setName("ROLE_USER");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(admin));
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        service = new SyntheticSandboxBootstrapService(guard, bootstrapRepository, userRepository,
                roleRepository, passwordEncoder, "runtime-bootstrap-token");
    }

    @Test
    void createsExactlyOneAdminWithoutDefaultCredentials() {
        var result = service.bootstrap("runtime-bootstrap-token", "first.operator", "unique-password-123");
        assertThat(result.completed()).isTrue();
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getRoles()).extracting(Role::getName)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        assertThat(user.getValue().getPassword()).isEqualTo("bcrypt-hash");
        verify(bootstrapRepository).saveAndFlush(any());
    }

    @Test
    void rejectsMissingRuntimeBootstrapAuthorization() {
        assertThatThrownBy(() -> service.bootstrap("", "first.operator", "unique-password-123"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsReplayAfterSetup() {
        when(bootstrapRepository.existsById((short) 1)).thenReturn(true);
        assertThatThrownBy(() -> service.bootstrap("runtime-bootstrap-token", "first.operator", "unique-password-123"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already completed");
        verifyNoInteractions(userRepository);
    }
}
