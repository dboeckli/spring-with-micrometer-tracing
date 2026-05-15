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
class LoggingWithElasticsearchIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    void elasticsearch_has_logs_after_hello_call() {
        // 1) Aktion auslösen, die Logs erzeugt
        String helloUrl = "http://localhost:" + port + "/hello";
        ResponseEntity<String> helloResponse = restTemplate.getForEntity(helloUrl, String.class);
        log.info("Hello response: status={}, body={}", helloResponse.getStatusCode(), helloResponse.getBody());
        assertEquals(HttpStatus.OK, helloResponse.getStatusCode());

        // 2) Elasticsearch Query: neueste Dokumente in den letzten Minuten
        final String esSearchUrl = "http://localhost:9200/_search";

        String queryJson = """
                {
                  "size": 5,
                  "sort": [{ "@timestamp": { "order": "desc" } }],
                  "query": {
                    "bool": {
                      "filter": [
                        { "range": { "@timestamp": { "gte": "now-5m" } } }
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
                    c -> log.info("Polling Elasticsearch ({}ms): {}", c.getRemainingTimeInMS(), esSearchUrl))
            .until(() -> restTemplate.exchange(esSearchUrl, HttpMethod.GET, request, String.class),
                    r -> r.getStatusCode().is2xxSuccessful() && r.getBody() != null && containsHits(r.getBody()));

        log.info("Elasticsearch response: {}", pretty(resp.getBody()));
    }

    private boolean containsHits(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode hits = root.path("hits").path("hits");
            return hits.isArray() && !hits.isEmpty();
        }
        catch (Exception e) {
            log.warn("Failed to parse Elasticsearch JSON", e);
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