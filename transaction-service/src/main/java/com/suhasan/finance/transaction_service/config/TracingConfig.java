package com.suhasan.finance.transaction_service.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request correlation on top of Spring Boot's tracing.
 *
 * <p>Tracing itself (sampling, W3C propagation, OTLP export, traceId/spanId in the
 * logging MDC) is configured by Spring Boot from {@code management.tracing.*} and
 * {@code management.otlp.tracing.*}. This adds a correlation ID and returns the
 * trace ID to the caller so a support ticket can point at the exact trace.
 *
 * <p>Client-supplied identity headers are deliberately not copied into logs: any
 * caller could set them, which would make logs claim the wrong user.
 */
@Configuration
public class TracingConfig implements WebMvcConfigurer {

    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String CORRELATION_ID_MDC = "correlationId";
    // Accept caller IDs only in a short, log-safe form; otherwise mint a new one.
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ObjectProvider<Tracer> tracer;

    public TracingConfig(final ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public void addInterceptors(@NonNull final InterceptorRegistry registry) {
        registry.addInterceptor(new CorrelationIdInterceptor(tracer));
    }

    static final class CorrelationIdInterceptor implements HandlerInterceptor {

        private final ObjectProvider<Tracer> tracer;

        CorrelationIdInterceptor(final ObjectProvider<Tracer> tracer) {
            this.tracer = tracer;
        }

        @Override
        public boolean preHandle(@NonNull final HttpServletRequest request,
                                 @NonNull final HttpServletResponse response,
                                 @NonNull final Object handler) {
            final String supplied = request.getHeader(CORRELATION_ID_HEADER);
            final String correlationId = supplied != null && SAFE_ID.matcher(supplied).matches()
                    ? supplied
                    : UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID_MDC, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            final Tracer current = tracer.getIfAvailable();
            final Span span = current == null ? null : current.currentSpan();
            if (span != null) {
                response.setHeader(TRACE_ID_HEADER, span.context().traceId());
            }
            return true;
        }

        @Override
        public void afterCompletion(@NonNull final HttpServletRequest request,
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final Object handler,
                                    @Nullable final Exception ex) {
            MDC.remove(CORRELATION_ID_MDC);
        }
    }
}
