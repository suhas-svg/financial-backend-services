package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.RegisterRequest;
import com.suhasan.finance.account_service.entity.Role;
import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.repository.RoleRepository;
import com.suhasan.finance.account_service.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;

    private SimpleMeterRegistry meterRegistry;
    private AuthService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new AuthService(userRepository, roleRepository, passwordEncoder, meterRegistry);
    }

    @Test
    void registerEncodesPasswordAssignsExistingUserRoleAndReturnsSafeDto() {
        RegisterRequest request = request("alice", "secret123");
        Role role = role("ROLE_USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(request);

        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRoles()).containsExactly("ROLE_USER");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(saved.getValue().getRoles()).containsExactly(role);
        assertThat(meterRegistry.counter("auth_registration_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("auth_registration_failed_total").count()).isZero();
        assertThat(meterRegistry.timer("auth_registration_duration").count()).isEqualTo(1L);
    }

    @Test
    void registerCreatesDefaultRoleWhenItDoesNotExist() {
        RegisterRequest request = request("alice", "secret123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(request);

        ArgumentCaptor<Role> createdRole = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(createdRole.capture());
        assertThat(createdRole.getValue().getName()).isEqualTo("ROLE_USER");
        assertThat(response.getRoles()).containsExactly("ROLE_USER");
    }

    @Test
    void duplicateUsernameFailsOnceWithoutEncodingOrSaving() {
        RegisterRequest request = request("alice", "secret123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already taken");

        assertThat(meterRegistry.counter("auth_registration_failed_total").count()).isEqualTo(1.0);
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void persistenceFailureIsCountedAndPropagated() {
        RegisterRequest request = request("alice", "secret123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role("ROLE_USER")));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(meterRegistry.counter("auth_registration_total").count()).isZero();
        assertThat(meterRegistry.counter("auth_registration_failed_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer("auth_registration_duration").count()).isEqualTo(1L);
    }

    private RegisterRequest request(String username, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
