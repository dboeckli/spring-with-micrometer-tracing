package ch.dboeckli.example.otel.tracing;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleSpan;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceDebugFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String SPAN_ID = "00f067aa0ba902b7";

    private final Tracer tracer = mock(Tracer.class);

    private final TraceDebugFilter filter = new TraceDebugFilter(tracer);

    @Test
    void logsAValidTraceContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(tracer.currentSpan()).thenReturn(span(TRACE_ID, SPAN_ID));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void logsAnInvalidTraceContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(tracer.currentSpan()).thenReturn(span("invalid", "invalid"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void considersTraceContextWithValidIdsAsValid() {
        assertThat(filter.isValidTraceContext(span(TRACE_ID, SPAN_ID).context())).isTrue();
    }

    @Test
    void considersTraceContextWithInvalidTraceIdAsInvalid() {
        assertThat(filter.isValidTraceContext(span("invalid", SPAN_ID).context())).isFalse();
    }

    @Test
    void considersTraceContextWithInvalidSpanIdAsInvalid() {
        assertThat(filter.isValidTraceContext(span(TRACE_ID, "invalid").context())).isFalse();
    }

    private SimpleSpan span(String traceId, String spanId) {
        SimpleSpan span = new SimpleSpan();
        span.context().setTraceId(traceId);
        span.context().setSpanId(spanId);
        return span;
    }

}
