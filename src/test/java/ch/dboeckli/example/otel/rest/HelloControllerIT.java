package ch.dboeckli.example.otel.rest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none" })
@ActiveProfiles("local")
@Slf4j
class HelloControllerIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void hello_returnsHelloMessage() {
        String url = "http://localhost:" + port + "/hello";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertAll(() -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody()).isEqualTo("{\"message\":\"hello\"}"));
    }

    @Test
    void hello_logsMessage_viaLogbackAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(HelloController.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        String url = "http://localhost:" + port + "/hello";
        restTemplate.getForEntity(url, String.class);
        List<ILoggingEvent> logEvents = listAppender.list;

        assertAll(() -> assertNotNull(logEvents), () -> assertEquals(3, logEvents.size()),
                () -> assertThat(logEvents.get(1).getFormattedMessage()).contains(HelloController.HELLO_MESSAGE),
                () -> assertThat(logEvents.getFirst().getMDCPropertyMap().get("trace_id")).isNotBlank()
                    .matches("[0-9a-f]{32}"),
                () -> assertThat(logEvents.getFirst().getMDCPropertyMap().get("span_id")).as("span_id")
                    .isNotBlank()
                    .matches("[0-9a-f]{16}"),
                () -> assertThat(logEvents.getFirst().getMDCPropertyMap().get("trace_flags")).as("trace_flags")
                    .isNotBlank()
                    .matches("[0-9a-f]{2}"));

        logger.detachAppender(listAppender);
        listAppender.stop();
    }

}