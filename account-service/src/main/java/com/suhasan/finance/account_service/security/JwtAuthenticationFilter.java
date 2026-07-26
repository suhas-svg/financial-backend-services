package com.suhasan.finance.account_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider tokenProvider;
  private final CustomUserDetailsService userDetailsService;

  @Override
  protected void doFilterInternal(@NonNull final HttpServletRequest req,
                                  @NonNull final HttpServletResponse res,
                                  @NonNull final FilterChain chain)
      throws ServletException, IOException {
    final String token = resolveToken(req);

    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      if (tokenProvider.validateInternalServiceToken(token)) {
        final String subject = tokenProvider.getInternalSubject(token);
        final var authorities = tokenProvider.getInternalRoles(token).stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
        final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(subject, token, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } else if (tokenProvider.validateToken(token)) {
        final String username = tokenProvider.getUsernameFromJWT(token);
        final UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, token, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }

    chain.doFilter(req, res);
  }

  private String resolveToken(final HttpServletRequest req) {
    final String authHeader = req.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }

    final String token = authHeader.substring(7);
    return token.isBlank() ? null : token;
  }
}
