package com.suhasan.finance.account_service.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final CustomUserDetailsService users = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, users);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesMissingMalformedAndBlankBearerHeadersWithoutAuthentication() throws Exception {
        for (String header : new String[]{null, "Basic abc", "Bearer "}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            if (header != null) request.addHeader("Authorization", header);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        verify(tokens, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void authenticatesInternalServiceTokenWithBoundRoles() throws Exception {
        when(tokens.validateInternalServiceToken("internal")).thenReturn(true);
        when(tokens.getInternalSubject("internal")).thenReturn("transaction-service");
        when(tokens.getInternalRoles("internal")).thenReturn(List.of("ROLE_INTERNAL_SERVICE"));

        execute("internal");

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("transaction-service");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_INTERNAL_SERVICE");
        verify(tokens, never()).validateToken("internal");
    }

    @Test
    void authenticatesCustomerTokenAndLeavesInvalidOrExistingContextUntouched() throws Exception {
        when(tokens.validateInternalServiceToken("customer-token")).thenReturn(false);
        when(tokens.validateToken("customer-token")).thenReturn(true);
        when(tokens.getUsernameFromJWT("customer-token")).thenReturn("customer");
        when(users.loadUserByUsername("customer")).thenReturn(
                User.withUsername("customer").password("n/a").roles("USER").build());
        execute("customer-token");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("customer");

        SecurityContextHolder.clearContext();
        when(tokens.validateInternalServiceToken("bad")).thenReturn(false);
        when(tokens.validateToken("bad")).thenReturn(false);
        execute("bad");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        var existing = new org.springframework.security.authentication.TestingAuthenticationToken(
                "existing", "n/a", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(existing);
        execute("bad");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    private void execute(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
