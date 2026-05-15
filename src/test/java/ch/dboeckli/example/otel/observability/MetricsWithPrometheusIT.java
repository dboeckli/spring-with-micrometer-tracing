package ch.dboeckli.example.otel.observability;

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

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "spring.docker.compose.skip.in-tests=false",
                // Micrometer-OTLP (für Spring) – kann bleiben, wird hier aber praktisch
                // nicht verwendet
                "management.otlp.metrics.export.step=5s",
                // WICHTIG: OpenTelemetry-Metrics-Export-Intervall (Standard: 60000 ms)
                "otel.metric.export.interval=5000" })
@ActiveProfiles("local")
@Slf4j
@AutoConfigureObservability
class MetricsWithPrometheusIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    void prometheus_server_has_metrics_after_hello_call() {
        String helloUrl = "http://localhost:" + port + "/hello";
        ResponseEntity<String> helloResponse = restTemplate.getForEntity(helloUrl, String.class);
        log.info("Hello response: status={}, body={}", helloResponse.getStatusCode(), helloResponse.getBody());
        assertEquals(HttpStatus.OK, helloResponse.getStatusCode());

        String prometheus = "http://localhost:9090/api/v1/query";
        String httpQuery = prometheus + "?query=http_server_request_duration_seconds_count";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> httpMetric = await().atMost(Duration.ofSeconds(120))
                .pollDelay(Duration.ofSeconds(2))
                .pollInterval(Duration.ofSeconds(2))
                .conditionEvaluationListener(condition -> log.info("Polling Prometheus ({}): {}",
                        condition.getRemainingTimeInMS(), httpQuery))
                .until(() -> restTemplate.exchange(httpQuery, HttpMethod.GET, request, String.class), resp -> {
                    log.info("Prometheus poll response: status={}, body={}", resp.getStatusCode(),
                            this.pretty(resp.getBody()));

                    return resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                            && resp.getBody().contains("\"status\":\"success\"")
                            && resp.getBody().contains("\"resultType\":\"vector\"")
                            && resp.getBody().contains("\"metric\"");
                });

            log.info("Prometheus response for http_server_request_duration_seconds_count: {}",
                    this.pretty(httpMetric.getBody()));

            // show all possible values for the metric (Erfolgsfall)
            String allValuesQuery = "http://localhost:9090/api/v1/label/__name__/values";
            ResponseEntity<String> allValuesResponse = restTemplate.exchange(allValuesQuery, HttpMethod.GET, request,
                    String.class);
            log.info("Prometheus all values: status={}, body={}", allValuesResponse.getStatusCode(),
                    this.pretty(allValuesResponse.getBody()));
        }
        catch (Exception e) {
            // Wenn das Awaitility-Timeout erreicht wird (oder ein anderer Fehler
            // auftritt),
            // trotzdem alle verfügbaren Metric-Namen loggen
            String allValuesQuery = "http://localhost:9090/api/v1/label/__name__/values";
            try {
                ResponseEntity<String> allValuesResponse = restTemplate.exchange(allValuesQuery, HttpMethod.GET,
                        request, String.class);
                log.info("Prometheus all values (on failure): status={}, body={}", allValuesResponse.getStatusCode(),
                        this.pretty(allValuesResponse.getBody()));
            }
            catch (Exception ex) {
                log.warn("Failed to fetch Prometheus all values after error", ex);
            }
            // Test scheitern lassen, damit der Fehler sichtbar bleibt
            throw e;
        }
    }

    private String pretty(String body) {
        try {
            Object json = OBJECT_MAPPER.readValue(body, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        }
        catch (Exception e) {
            // Falls kein valides JSON: unverändert zurückgeben
            return body;
        }
    }

}