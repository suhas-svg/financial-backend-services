package com.suhasan.finance.transaction_service.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdInterceptorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsWellFormedCallerIdAndReturnsTraceId() {
        var interceptor = new TracingConfig.CorrelationIdInterceptor(tracerWithTrace("4bf92f3577b34da6a3ce929d0e0e4736"));
        var request = new MockHttpServletRequest();
        request.addHeader(TracingConfig.CORRELATION_ID_HEADER, "order-42.retry_1");
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getHeader(TracingConfig.CORRELATION_ID_HEADER)).isEqualTo("order-42.retry_1");
        assertThat(response.getHeader(TracingConfig.TRACE_ID_HEADER)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(MDC.get("correlationId")).isEqualTo("order-42.retry_1");
    }

    @Test
    void replacesUnsafeCallerIdToPreventLogInjection() {
        var interceptor = new TracingConfig.CorrelationIdInterceptor(tracerWithTrace(null));
        var request = new MockHttpServletRequest();
        request.addHeader(TracingConfig.CORRELATION_ID_HEADER, "abc\n{\"level\":\"ERROR\"}");
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getHeader(TracingConfig.CORRELATION_ID_HEADER)).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(TracingConfig.TRACE_ID_HEADER)).isNull();
    }

    @Test
    void doesNotCopyClientClaimedIdentityIntoLogs() {
        var interceptor = new TracingConfig.CorrelationIdInterceptor(tracerWithTrace(null));
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-ID", "admin");
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Tracer> tracerWithTrace(String traceId) {
        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        Tracer tracer = mock(Tracer.class);
        when(provider.getIfAvailable()).thenReturn(tracer);
        if (traceId != null) {
            Span span = mock(Span.class);
            TraceContext context = mock(TraceContext.class);
            when(context.traceId()).thenReturn(traceId);
            when(span.context()).thenReturn(context);
            when(tracer.currentSpan()).thenReturn(span);
        }
        return provider;
    }
}
