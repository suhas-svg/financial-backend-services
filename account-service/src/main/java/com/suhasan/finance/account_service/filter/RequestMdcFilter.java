// package com.suhasan.finance.account_service.filter;

// import jakarta.servlet.FilterChain;
// import jakarta.servlet.ServletException;
// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.servlet.http.HttpServletResponse;
// import org.slf4j.MDC;
// import org.springframework.stereotype.Component;
// import org.springframework.web.filter.OncePerRequestFilter;

// import java.io.IOException;
// import java.util.UUID;


// @Component
// public class RequestMdcFilter extends OncePerRequestFilter {

//   @Override
//   protected void doFilterInternal(HttpServletRequest req, 
//                                   javax.servlet.http.HttpServletResponse res,
//                                   FilterChain chain)
//       throws ServletException, IOException {
//     String requestId = UUID.randomUUID().toString();
//     MDC.put("requestId", requestId);
//     MDC.put("method", req.getMethod());
//     MDC.put("path", req.getRequestURI());
//     // you could also MDC.put("userId", extractFromToken(req));
//     try {
//       chain.doFilter(req, res);
//     } finally {
//       MDC.clear();
//     }
//   }
// }

package com.suhasan.finance.account_service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;  // Jakarta import
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestMdcFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest req,
                                  HttpServletResponse res,
                                  FilterChain chain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
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
