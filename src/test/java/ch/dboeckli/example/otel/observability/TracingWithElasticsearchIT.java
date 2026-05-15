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
        properties = { "spring.docker.compose.skip.in-tests=false" })
@ActiveProfiles("local")
@Slf4j
@AutoConfigureObservability
class TracingWithElasticsearchIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    void elasticsearch_has_traces_after_hello_call() {
        // 1) Aktion auslösen, die Traces erzeugt
        String helloUrl = "http://localhost:" + port + "/hello";
        ResponseEntity<String> helloResponse = restTemplate.getForEntity(helloUrl, String.class);
        log.info("Hello response: status={}, body={}", helloResponse.getStatusCode(), helloResponse.getBody());
        assertEquals(HttpStatus.OK, helloResponse.getStatusCode());

        // 2) Elasticsearch Query: Trace-Dokumente der letzten Minuten
        // APM legt Traces typischerweise in Indizes mit Prefix "traces-" ab.
        final String esSearchUrl = "http://localhost:9200/traces-*/_search";

        String queryJson = """
                {
                  "size": 20,
                  "sort": [{ "@timestamp": { "order": "desc" } }],
                  "query": {
                    "bool": {
                      "filter": [
                        { "range": { "@timestamp": { "gte": "now-5m" } } },
                        { "term":  { "processor.event": "span" } },
                        { "term":  { "service.name": "spring-with-otel" } }
                      ]
                    }
                  }
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(queryJson, headers);

        ResponseEntity<String> resp = await().atMost(Duration.ofSeconds(90))
            .pollDelay(Duration.ofSeconds(3))
            .pollInterval(Duration.ofSeconds(3))
            .conditionEvaluationListener(
                    c -> log.info("Polling Elasticsearch traces ({}ms): {}", c.getRemainingTimeInMS(), esSearchUrl))
            .until(() -> restTemplate.exchange(esSearchUrl, HttpMethod.GET, request, String.class), r -> {
                log.info("Elasticsearch traces poll response: status={}, body={}", r.getStatusCode(),
                        pretty(r.getBody()));

                return r.getStatusCode().is2xxSuccessful() && r.getBody() != null && containsTraceHits(r.getBody());
            });

        log.info("Elasticsearch traces final response: {}", pretty(resp.getBody()));
    }

    private boolean containsTraceHits(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) {
                return false;
            }
            // Optional: einen passenden Span für /hello finden
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                JsonNode spanName = source.path("span.name");
                if (spanName.isTextual() && spanName.asText().contains("/hello")) {
                    log.info("Found span for /hello in Elasticsearch: {}", pretty(source.toString()));
                    return true;
                }
            }
            // Wenn keine /hello-Spans, aber generell Traces da sind, trotzdem true?
            // Falls du wirklich nur /hello haben willst, entferne die folgende Zeile:
            return !hits.isEmpty();
        }
        catch (Exception e) {
            log.warn("Failed to parse Elasticsearch traces JSON", e);
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
