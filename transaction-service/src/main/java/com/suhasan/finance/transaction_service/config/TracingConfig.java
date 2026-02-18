package com.suhasan.finance.transaction_service.config;

import brave.sampler.Sampler;
import brave.Tracing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enhanced configuration for distributed tracing with correlation headers
 */
@Configuration
@Slf4j
public class TracingConfig implements WebMvcConfigurer {
    
    @Value("${management.tracing.sampling.probability:1.0}")
    private float samplingProbability;
    
    @Value("${spring.application.name:transaction-service}")
    private String serviceName;
    
    /**
     * Configure tracing sampler with custom logic
     */
    @Bean
    public Sampler customSampler() {
        log.info("Configuring tracing sampler with probability: {} for service: {}", 
                samplingProbability, serviceName);
        
        // Custom sampler that always samples critical operations
        return Sampler.create(samplingProbability);
    }
    
    /**
     * Configure tracing with service name
     */
    @Bean
    public Tracing tracing() {
        return Tracing.newBuilder()
                .localServiceName(serviceName)
                .sampler(customSampler())
                .build();
    }
    
    /**
     * Add correlation ID interceptor for request tracking
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new CorrelationIdInterceptor());
    }
    
    /**
     * Interceptor to add correlation headers for distributed tracing
     */
    public static class CorrelationIdInterceptor implements HandlerInterceptor {
        
        private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
        private static final String TRACE_ID_HEADER = "X-Trace-ID";
        private static final String SPAN_ID_HEADER = "X-Span-ID";
        private static final String REQUEST_ID_HEADER = "X-Request-ID";
        private static final String USER_ID_HEADER = "X-User-ID";
        private static final String SESSION_ID_HEADER = "X-Session-ID";
        private static final String CLIENT_ID_HEADER = "X-Client-ID";
        private static final String REQUEST_SOURCE_HEADER = "X-Request-Source";
        private static final String BUSINESS_CONTEXT_HEADER = "X-Business-Context";
        
        @Override
        public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                               @NonNull Object handler) throws Exception {
            
            // Get or generate correlation ID
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = java.util.UUID.randomUUID().toString();
            }
            
            // Get trace context if available from MDC or Brave context
            String traceId = org.slf4j.MDC.get("traceId");
            String spanId = org.slf4j.MDC.get("spanId");
            
            // Try to get trace context from Brave if not in MDC
            if (traceId == null) {
                Tracing currentTracing = Tracing.current();
                brave.propagation.TraceContext traceContext = (currentTracing != null && currentTracing.tracer().currentSpan() != null)
                        ? currentTracing.tracer().currentSpan().context()
                        : null;
                if (traceContext != null) {
                    traceId = traceContext.traceIdString();
                    spanId = traceContext.spanIdString();
                }
            }
            
            // Get additional context headers
            String userId = request.getHeader(USER_ID_HEADER);
            String sessionId = request.getHeader(SESSION_ID_HEADER);
            String clientId = request.getHeader(CLIENT_ID_HEADER);
            String requestSource = request.getHeader(REQUEST_SOURCE_HEADER);
            String businessContext = request.getHeader(BUSINESS_CONTEXT_HEADER);
            
            // Add headers to response for downstream services
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            if (traceId != null) {
                response.setHeader(TRACE_ID_HEADER, traceId);
            }
            if (spanId != null) {
                response.setHeader(SPAN_ID_HEADER, spanId);
            }
            
            // Propagate context headers
            if (userId != null) {
                response.setHeader(USER_ID_HEADER, userId);
            }
            if (sessionId != null) {
                response.setHeader(SESSION_ID_HEADER, sessionId);
            }
            if (clientId != null) {
                response.setHeader(CLIENT_ID_HEADER, clientId);
            }
            if (requestSource != null) {
                response.setHeader(REQUEST_SOURCE_HEADER, requestSource);
            }
            if (businessContext != null) {
                response.setHeader(BUSINESS_CONTEXT_HEADER, businessContext);
            }
            
            // Add to MDC for logging
            org.slf4j.MDC.put("correlationId", correlationId);
            if (traceId != null) {
                org.slf4j.MDC.put("traceId", traceId);
            }
            if (spanId != null) {
                org.slf4j.MDC.put("spanId", spanId);
            }
            if (userId != null) {
                org.slf4j.MDC.put("userId", userId);
            }
            if (sessionId != null) {
                org.slf4j.MDC.put("sessionId", sessionId);
            }
            if (clientId != null) {
                org.slf4j.MDC.put("clientId", clientId);
            }
            if (requestSource != null) {
                org.slf4j.MDC.put("requestSource", requestSource);
            }
            if (businessContext != null) {
                org.slf4j.MDC.put("businessContext", businessContext);
            }
            
            // Add request ID for this specific request
            String requestId = java.util.UUID.randomUUID().toString();
            org.slf4j.MDC.put("requestId", requestId);
            response.setHeader(REQUEST_ID_HEADER, requestId);
            
            // Add request metadata
            org.slf4j.MDC.put("requestMethod", request.getMethod());
            org.slf4j.MDC.put("requestUri", request.getRequestURI());
            org.slf4j.MDC.put("userAgent", request.getHeader("User-Agent"));
            org.slf4j.MDC.put("remoteAddr", getClientIpAddress(request));
            org.slf4j.MDC.put("requestStartTime", String.valueOf(System.currentTimeMillis()));
            
            log.debug("Request correlation setup - correlationId: {}, traceId: {}, spanId: {}, requestId: {}, userId: {}", 
                    correlationId, traceId, spanId, requestId, userId);
            
            return true;
        }
        
        /**
         * Get the real client IP address, considering proxy headers
         */
        private String getClientIpAddress(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        }
        
        @Override
        public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                  @NonNull Object handler, @Nullable Exception ex) throws Exception {
            // Log request completion with timing
            long requestDuration = System.currentTimeMillis() - 
                    Long.parseLong(org.slf4j.MDC.get("requestStartTime") != null ? 
                            org.slf4j.MDC.get("requestStartTime") : "0");
            
            log.debug("Request completed - method: {}, uri: {}, status: {}, duration: {}ms", 
                    request.getMethod(), request.getRequestURI(), response.getStatus(), requestDuration);
            
            // Clean up MDC
            org.slf4j.MDC.remove("correlationId");
            org.slf4j.MDC.remove("traceId");
            org.slf4j.MDC.remove("spanId");
            org.slf4j.MDC.remove("requestId");
            org.slf4j.MDC.remove("userId");
            org.slf4j.MDC.remove("sessionId");
            org.slf4j.MDC.remove("clientId");
            org.slf4j.MDC.remove("requestSource");
            org.slf4j.MDC.remove("businessContext");
            org.slf4j.MDC.remove("requestMethod");
            org.slf4j.MDC.remove("requestUri");
            org.slf4j.MDC.remove("userAgent");
            org.slf4j.MDC.remove("remoteAddr");
            org.slf4j.MDC.remove("requestStartTime");
        }
    }
}
