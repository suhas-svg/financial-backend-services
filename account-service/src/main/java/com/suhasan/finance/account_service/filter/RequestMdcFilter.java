package com.suhasan.finance.account_service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestMdcFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(@NonNull final HttpServletRequest req,
                                  @NonNull final HttpServletResponse res,
                                  @NonNull final FilterChain chain)
      throws ServletException, IOException {
    final String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    MDC.put("method", req.getMethod());
    MDC.put("path", req.getRequestURI());
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.clear();
    }
  }
}
