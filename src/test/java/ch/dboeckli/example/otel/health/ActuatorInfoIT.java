package ch.dboeckli.example.otel.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext
@SpringBootTest(properties = { "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none" })
@AutoConfigureMockMvc
@AutoConfigureObservability
@Slf4j
@ActiveProfiles("local")
class ActuatorInfoIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    BuildProperties buildProperties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorInfoTest() throws Exception {
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isOk())
            .andDo(result -> log.info("Response (pretty):\n{}", pretty(result.getResponse().getContentAsString())))

            .andExpect(jsonPath("$.git.commit.id.abbrev").isString())

            .andExpect(jsonPath("$.build.artifact").value(buildProperties.getArtifact()))
            .andExpect(jsonPath("$.build.group").value(buildProperties.getGroup()));
    }

    @Test
    void actuatorHealthTest() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andDo(result -> log.info("Response (pretty):\n{}", pretty(result.getResponse().getContentAsString())))
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorPrometheusTest() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andDo(result -> log.info("Response:\n{}", result.getResponse().getContentAsString()));
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
