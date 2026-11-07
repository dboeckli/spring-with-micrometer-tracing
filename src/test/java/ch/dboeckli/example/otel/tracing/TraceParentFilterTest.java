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

class TraceParentFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String SPAN_ID = "00f067aa0ba902b7";

    private final Tracer tracer = mock(Tracer.class);

    private final TraceParentFilter filter = new TraceParentFilter(tracer);

    @Test
    void skipsTraceparentHeaderWhenNoActiveSpanExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("traceparent")).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void setsTraceparentHeaderWhenNoneIsPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(tracer.currentSpan()).thenReturn(span());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("traceparent")).isEqualTo("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void setsTraceparentHeaderWhenIncomingHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        request.addHeader("traceparent", " ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(tracer.currentSpan()).thenReturn(span());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("traceparent")).isEqualTo("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void keepsAnExistingTraceparentHeader() throws Exception {
        String traceparent = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        request.addHeader("traceparent", traceparent);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(tracer.currentSpan()).thenReturn(span());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("traceparent")).isNull();
        verify(filterChain).doFilter(request, response);
    }

    private SimpleSpan span() {
        SimpleSpan span = new SimpleSpan();
        span.context().setTraceId(TRACE_ID);
        span.context().setSpanId(SPAN_ID);
        return span;
    }

}
