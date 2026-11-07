package ch.dboeckli.example.otel.tracing;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleSpan;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaggageTaggingFilterTest {

    private Tracer tracer;

    private CurrentTraceContext currentTraceContext;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        currentTraceContext = mock(CurrentTraceContext.class);
        Tracer.SpanInScope spanInScope = mock(Tracer.SpanInScope.class);
        BaggageInScope baggageInScope = mock(BaggageInScope.class);

        when(tracer.currentTraceContext()).thenReturn(currentTraceContext);
        when(currentTraceContext.context()).thenReturn(null);
        when(tracer.withSpan(any())).thenReturn(spanInScope);
        when(tracer.createBaggageInScope(anyString(), anyString())).thenReturn(baggageInScope);
    }

    @Test
    void continuesChainWhenNoSpanAndNoBaggageExist() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        when(tracer.getAllBaggage(any())).thenReturn(null);
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new BaggageTaggingFilter(tracer).doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void continuesChainWhenBaggageIsNull() throws Exception {
        SimpleSpan span = new SimpleSpan();
        when(tracer.currentSpan()).thenReturn(span);
        when(tracer.getAllBaggage(any())).thenReturn(null);
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new BaggageTaggingFilter(tracer).doFilterInternal(request, response, filterChain);

        assertThat(span.getTags()).isEmpty();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void continuesChainWhenNoSpanButBaggageExists() throws Exception {
        Map<String, String> baggage = new HashMap<>();
        baggage.put("present", "value");
        when(tracer.currentSpan()).thenReturn(null);
        when(tracer.getAllBaggage(any())).thenReturn(baggage);
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new BaggageTaggingFilter(tracer).doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void tagsTheCurrentSpanWithNonNullBaggageValues() throws Exception {
        Map<String, String> baggage = new HashMap<>();
        baggage.put("present", "value");
        baggage.put("missing", null);
        SimpleSpan span = new SimpleSpan();
        when(tracer.currentSpan()).thenReturn(span);
        when(tracer.getAllBaggage(any())).thenReturn(baggage);
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new BaggageTaggingFilter(tracer).doFilterInternal(request, response, filterChain);

        assertThat(span.getTags()).containsEntry("present", "value").doesNotContainKey("missing");
        verify(filterChain).doFilter(request, response);
    }

}
