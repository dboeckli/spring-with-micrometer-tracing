package ch.dboeckli.example.otel.service;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
class HelloServiceTest {

    private SimpleTracer tracer;

    private HelloService helloService;

    @BeforeEach
    void setUp() {
        tracer = new SimpleTracer();
        helloService = new HelloService(tracer);
    }

    @Test
    void shouldCreateSpan() {
        Span parentSpan = tracer.nextSpan().name("test-parent").start();
        try (var _ = tracer.withSpan(parentSpan)) {
            helloService.processHello();
        }
        finally {
            parentSpan.end();
        }

        // Alle Spans, die der Tracer erstellt hat
        Deque<SimpleSpan> spans = tracer.getSpans();
        assertThat(spans).hasSize(2);

        SimpleSpan processHelloSpan = spans.stream()
            .filter(s -> "processHello".equals(s.getName()))
            .findFirst()
            .orElseThrow();

        assertThat(processHelloSpan.getName()).isEqualTo("processHello");
        assertThat(processHelloSpan.getTags()).containsEntry("processHello", "spanValue");
        assertThat(processHelloSpan.getEvents()).extracting("value").contains("service-started");
    }

    @Test
    void marksSpanAsErroredAndRethrowsWhenBaggageScopeFails() {
        Tracer mockingTracer = mock(Tracer.class);
        CurrentTraceContext currentTraceContext = mock(CurrentTraceContext.class);
        Span span = mock(Span.class);

        when(mockingTracer.currentTraceContext()).thenReturn(currentTraceContext);
        when(mockingTracer.getAllBaggage(any())).thenReturn(Map.of());
        when(mockingTracer.nextSpan()).thenReturn(span);
        when(span.name("processHello")).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(mockingTracer.withSpan(span)).thenReturn(mock(Tracer.SpanInScope.class));
        when(mockingTracer.createBaggageInScope(anyString(), anyString()))
            .thenThrow(new IllegalStateException("baggage failure"));

        HelloService service = new HelloService(mockingTracer);

        assertThatThrownBy(service::processHello).isInstanceOf(IllegalStateException.class)
            .hasMessage("baggage failure");
        verify(span).error(any(IllegalStateException.class));
        verify(span).end();
    }

}