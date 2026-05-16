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

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "spring.docker.compose.skip.in-tests=false", "management.otlp.metrics.export.step=5s" })
@ActiveProfiles("local")
@Slf4j
@AutoConfigureObservability
class MetricsWithElasticsearchIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    void elasticsearch_has_metrics_after_hello_call() {
        // 1) Aktion auslösen, die Metriken erzeugt
        String helloUrl = "http://localhost:" + port + "/hello";
        ResponseEntity<String> helloResponse = restTemplate.getForEntity(helloUrl, String.class);
        log.info("Hello response: status={}, body={}", helloResponse.getStatusCode(), helloResponse.getBody());
        assertEquals(HttpStatus.OK, helloResponse.getStatusCode());

        // 2) Elasticsearch Query: Metrik-Dokumente der letzten Minuten mit
        // http.server.request.duration
        final String esSearchUrl = "http://localhost:9200/metrics-*/_search";

        String queryJson = """
                {
                  "size": 10,
                  "sort": [{ "@timestamp": { "order": "desc" } }],
                  "query": {
                    "bool": {
                      "filter": [
                        { "range": { "@timestamp": { "gte": "now-5m" } } },
                        { "term": { "service.name": "spring-with-micrometer-tracing" } },
                        { "term": { "metricset.name": "app" } }
                      ]
                    }
                  }
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(queryJson, headers);

        ResponseEntity<String> resp = await().atMost(Duration.ofSeconds(120))
            .pollDelay(Duration.ofSeconds(5))
            .pollInterval(Duration.ofSeconds(5))
            .conditionEvaluationListener(
                    c -> log.info("Polling Elasticsearch metrics ({}ms): {}", c.getRemainingTimeInMS(), esSearchUrl))
            .until(() -> restTemplate.exchange(esSearchUrl, HttpMethod.GET, request, String.class), r -> {
                log.info("Elasticsearch metrics poll response: status={}, body={}", r.getStatusCode(),
                        pretty(r.getBody()));

                return r.getStatusCode().is2xxSuccessful() && r.getBody() != null && containsMetrics(r.getBody());
            });

        log.info("Elasticsearch metrics final response: {}", pretty(resp.getBody()));
    }

    private boolean containsMetrics(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty())
                return false;

            // Log first hit to see real field structure
            log.info("First metric doc: {}", pretty(hits.get(0).toString()));
            return true;
        }
        catch (Exception e) {
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
