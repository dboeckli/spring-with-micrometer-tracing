package ch.dboeckli.example.otel.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import java.util.List;

import static io.opentelemetry.api.GlobalOpenTelemetry.resetForTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
class HelloServiceTest {

    private InMemorySpanExporter spanExporter;

    private SdkTracerProvider tracerProvider;

    private HelloService helloService;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

        resetForTest();
        GlobalOpenTelemetry.set(openTelemetry);

        // BuildProperties mocken
        BuildProperties mockedBuildProperties = mock(BuildProperties.class);
        when(mockedBuildProperties.getVersion()).thenReturn("test-version");

        Tracer tracer = openTelemetry.getTracer("test-tracer", "test-version");
        log.info("Tracer: {}", tracer);

        // HelloService so anpassen, dass er entweder Tracer injiziert bekommt
        helloService = new HelloService(openTelemetry, "test-service", mockedBuildProperties);
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
        spanExporter.reset();
        resetForTest();
    }

    @Test
    void processHello_createsSpanWithAttributesAndEvents() {
        String result = helloService.processHello();

        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        assertAll(() -> assertThat(result).isEqualTo("Hello from the service"), () -> assertThat(spans).hasSize(1),

                () -> {
                    Assertions.assertNotNull(spans);
                    SpanData span = spans.getFirst();
                    List<EventData> events = span.getEvents();

                    assertAll(
                            // Span‑Basics
                            () -> assertThat(span.getName()).isEqualTo("process-hello"),
                            () -> assertThat(span.getKind().name()).isEqualTo("INTERNAL"),
                            () -> assertThat(span.getStatus().getStatusCode().name()).isEqualTo("OK"),

                            // Attribute
                            () -> assertThat(span.getAttributes()
                                .get(io.opentelemetry.api.common.AttributeKey.stringKey("app.custom.service.flag")))
                                .isEqualTo("helloFromService"),

                            // Events allgemein
                            () -> assertThat(events).hasSize(2),

                            // Event‑Namen / Reihenfolge
                            () -> {
                                Assertions.assertNotNull(events);
                                assertThat(events.getFirst().getName()).isEqualTo("service-started");
                            }, () -> {
                                Assertions.assertNotNull(events);
                                assertThat(events.get(1).getName()).isEqualTo("service-completed");
                            },

                            // Optional: zeitliche Reihenfolge
                            () -> {
                                Assertions.assertNotNull(events);
                                assertThat(events.get(1).getEpochNanos())
                                    .isGreaterThanOrEqualTo(events.get(0).getEpochNanos());
                            });
                });
    }

}