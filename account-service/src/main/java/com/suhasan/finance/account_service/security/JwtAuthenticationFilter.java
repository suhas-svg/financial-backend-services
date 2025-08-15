package com.suhasan.finance.account_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import java.io.IOException;
import org.springframework.security.core.userdetails.UserDetails;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenProvider tokenProvider;
  private final CustomUserDetailsService userDetailsService;

  public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                 CustomUserDetailsService uds) {
    this.tokenProvider = tokenProvider;
    this.userDetailsService = uds;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req,
                                  HttpServletResponse res,
                                  FilterChain chain)
      throws ServletException, IOException {
    // TEMPORARY: Skip authentication for all endpoints
    chain.doFilter(req, res);
  }
}