package ch.dboeckli.example.otel.rest;

import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static io.opentelemetry.api.GlobalOpenTelemetry.resetForTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "management.otlp.tracing.export.enabled=false", "management.otlp.logging.export.enabled=false",
                "management.otlp.metrics.export.enabled=false" })
@ActiveProfiles("local")
@Import(HelloControllerTest.TracingTestConfig.class)
@Slf4j
public class HelloControllerTest {

    @TestConfiguration
    static class TracingTestConfig {

        private static final List<String> TAG_FIELDS = List.of("testBaggage", "addedBaggageByFilter",
                "addedBaggageByController", "addedBaggageByService");

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        @Primary
        SdkTracerProvider sdkTracerProvider(InMemorySpanExporter exporter) {
            return SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        }

        @Bean
        @Primary
        OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider) {
            return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)

                .setPropagators(ContextPropagators.create(TextMapPropagator
                    .composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())))
                .build();
        }

        @Bean
        @Primary
        io.micrometer.tracing.Tracer micrometerTracer(OpenTelemetry openTelemetry) {
            return new OtelTracer(openTelemetry.getTracer("test"), new OtelCurrentTraceContext(), event -> {
            }, new OtelBaggageManager(new OtelCurrentTraceContext(),
                    // remote fields
                    TAG_FIELDS,
                    // tag fields
                    TAG_FIELDS));
        }

        @Bean
        @Primary
        io.micrometer.tracing.propagation.Propagator micrometerPropagator(OpenTelemetry openTelemetry) {
            return new io.micrometer.tracing.otel.bridge.OtelPropagator(openTelemetry.getPropagators(),
                    openTelemetry.getTracer("test"));
        }

    }

    @Autowired
    InMemorySpanExporter spanExporter;

    @Autowired
    SdkTracerProvider sdkTracerProvider;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        spanExporter.reset();
    }

    @AfterEach
    void tearDown() {
        sdkTracerProvider.close();
        spanExporter.reset();
        resetForTest();
    }

    @Test
    void hello_returnsHelloMessage() throws IOException, InterruptedException {
        String traceParentTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String traceParentSpanId = "00f067aa0ba902b7";

        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/hello"))
                .GET()
                .header("Accept", "application/json")
                .header("traceparent", "00-" + traceParentTraceId + "-" + traceParentSpanId + "-01")
                .header("baggage", "testBaggage=hallo")
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertAll(() -> assertThat(response.statusCode()).isEqualTo(200),
                    () -> assertThat(response.body()).isEqualTo("{\"message\":\"hello\"}"));
        }

        // Spans prüfen
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        log.info("Spans: {}", spans);

        assertThat(spans).isNotEmpty();
        assertThat(spans).hasSize(2);
        assertThat(spans).anyMatch(span -> span.getName().contains("hello"));

        SpanData span = spans.get(1); // this is the parent span

        // Span Name
        assertThat(span.getName()).isEqualTo("http get /hello");

        // Trace/Span IDs vorhanden
        assertThat(span.getTraceId()).isNotBlank();
        assertThat(span.getSpanId()).isNotBlank();
        assertThat(span.getTraceId()).isEqualTo(traceParentTraceId);
        assertThat(span.getParentSpanContext().getSpanId()).isEqualTo(traceParentSpanId);

        // Status OK
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);

        // Attributes
        Attributes attributes = span.getAttributes();
        log.info("Attributes: {}", attributes);
        assertAll(() -> assertThat(attributes.get(AttributeKey.stringKey("method"))).isEqualTo("GET"),
                () -> assertThat(attributes.get(AttributeKey.stringKey("status"))).isEqualTo("200"),
                () -> assertThat(attributes.get(AttributeKey.stringKey("uri"))).isEqualTo("/hello"),
                () -> assertThat(attributes.get(AttributeKey.stringKey("outcome"))).isEqualTo("SUCCESS"),
                () -> assertThat(attributes.get(AttributeKey.stringKey("application")))
                    .isEqualTo("spring-with-micrometer-tracing"),
                () -> assertThat(attributes.get(AttributeKey.stringKey("env"))).isEqualTo("local"),

                // Baggage-Felder
                () -> assertThat(attributes.get(AttributeKey.stringKey("testBaggage"))).isEqualTo("hallo"),
                () -> assertThat(attributes.get(AttributeKey.stringKey("addedBaggageByFilter")))
                    .isEqualTo("echo from filter"),
                () -> assertThat(attributes.get(AttributeKey.stringKey("addedBaggageByController")))
                    .isEqualTo("echo from controller"));

        SpanData serviceSpan = spans.getFirst();
        Attributes serviceSpanAttributes = serviceSpan.getAttributes();
        log.info("Service Span Attributes: {}", serviceSpanAttributes);

        assertAll(
                () -> assertThat(serviceSpanAttributes.get(AttributeKey.stringKey("addedBaggageByService")))
                    .isEqualTo("echo from service"),
                () -> assertThat(serviceSpanAttributes.get(AttributeKey.stringKey("processHello")))
                    .isEqualTo("spanValue"));

        // Parent-Beziehung verifizieren
        assertThat(serviceSpan.getParentSpanContext().getSpanId()).isEqualTo(span.getSpanId());
        assertThat(serviceSpan.getTraceId()).isEqualTo(span.getTraceId());

    }

}
