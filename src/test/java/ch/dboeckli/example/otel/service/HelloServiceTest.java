package ch.dboeckli.example.otel.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

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
        try (Tracer.SpanInScope ws = tracer.withSpan(parentSpan)) {
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
        assertThat(processHelloSpan.getEvents()).extracting("value").contains("service-started"); // passe
                                                                                                  // an,
                                                                                                  // was
                                                                                                  // du
                                                                                                  // wirklich
                                                                                                  // loggst
    }

}