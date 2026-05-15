package ch.dboeckli.example.otel.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "spring.docker.compose.skip.in-tests=false" })
@ActiveProfiles("local")
@Slf4j
@AutoConfigureObservability
public class TracingWithJaegerAndZipkinIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    void jaeger_has_traces_after_hello_call() {
        // 1) Aktion auslösen, die Metriken erzeugt
        String helloUrl = "http://localhost:" + port + "/hello";
        ResponseEntity<String> helloResponse = restTemplate.getForEntity(helloUrl, String.class);
        log.info("Hello response: status={}, body={}", helloResponse.getStatusCode(), helloResponse.getBody());
        assertEquals(HttpStatus.OK, helloResponse.getStatusCode());

        String service = "spring-with-otel";
        final String jaegerSearchUrl = "http://localhost:16686/api/traces?limit=20&lookback=1h&service="
                + URLEncoder.encode(service, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> resp = await().atMost(Duration.ofSeconds(60))
            .pollDelay(Duration.ofSeconds(2))
            .pollInterval(Duration.ofSeconds(2))
            .conditionEvaluationListener(
                    c -> log.info("Polling Jaeger ({}ms): {}", c.getRemainingTimeInMS(), jaegerSearchUrl))
            .until(() -> restTemplate.exchange(jaegerSearchUrl, HttpMethod.GET, request, String.class),
                    r -> r.getStatusCode().is2xxSuccessful() && r.getBody() != null
                            && containsJaegerTraces(r.getBody()));

        log.info("Jaeger response traces: {}", pretty(resp.getBody()));
    }

    @Test
    void zipkin_has_traces_after_hello_call() {
        // 1) Aktion auslösen, die Metriken erzeugt
        String helloUrl = "http://localhost:" + port + "/hello";
        ResponseEntity<String> helloResponse = restTemplate.getForEntity(helloUrl, String.class);
        log.info("Hello response: status={}, body={}", helloResponse.getStatusCode(), helloResponse.getBody());
        assertEquals(HttpStatus.OK, helloResponse.getStatusCode());

        // Zipkin V2 API: Suche der letzten Traces (JSON-Array, jeder Eintrag ist ein
        // Trace mit Spans)
        String zipkinQueryUrl = "http://localhost:9411/api/v2/traces?limit=20&lookback=" + (60 * 60 * 1000);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> resp = await().atMost(Duration.ofSeconds(60))
            .pollDelay(Duration.ofSeconds(2))
            .pollInterval(Duration.ofSeconds(2))
            .conditionEvaluationListener(
                    c -> log.info("Polling Zipkin ({}ms): {}", c.getRemainingTimeInMS(), zipkinQueryUrl))
            .until(() -> restTemplate.exchange(zipkinQueryUrl, HttpMethod.GET, request, String.class),
                    r -> r.getStatusCode().is2xxSuccessful() && r.getBody() != null
                            && containsZipkinTraces(r.getBody()));

        log.info("Zipkin response traces: {}", pretty(resp.getBody()));
    }

    private boolean containsJaegerTraces(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode data = root.get("data");
            return data != null && data.isArray() && !data.isEmpty();
        }
        catch (Exception e) {
            log.warn("Failed to parse Jaeger JSON", e);
            return false;
        }
    }

    private boolean containsZipkinTraces(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            return root.isArray() && !root.isEmpty();
        }
        catch (Exception e) {
            log.warn("Failed to parse Zipkin JSON", e);
            return false;
        }
    }

    private String pretty(String body) {
        try {
            Object json = OBJECT_MAPPER.readValue(body, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        }
        catch (Exception e) {
            return body;
        }
    }

}
