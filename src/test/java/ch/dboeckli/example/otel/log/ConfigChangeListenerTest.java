package ch.dboeckli.example.otel.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConfigChangeListenerTest {

    private static final String LOGGER_NAME = "ch.dboeckli.example.otel.log.ConfigChangeListener";

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    private ConfigChangeListener listener;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        listener = new ConfigChangeListener(mock(ObservationRegistry.class), mock(Tracer.class));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @Test
    void logsPlainPropertyValues() {
        GenericApplicationContext context = contextWithProperties(Map.of("customer.name", "Alice"));

        listener.doHandleContextRefresh(new ContextRefreshedEvent(context));

        assertThat(messages()).anyMatch(msg -> msg.contains("customer.name: Alice"));
    }

    @Test
    void masksPasswordKeyValues() {
        GenericApplicationContext context = contextWithProperties(
                Map.of("app.jwt.key-value", "supersecret", "app.secret", "verysecret", "db.password", "dbpassword"));

        listener.doHandleContextRefresh(new ContextRefreshedEvent(context));

        assertThat(messages()).anyMatch(msg -> msg.contains("app.jwt.key-value: **************************"))
            .anyMatch(msg -> msg.contains("app.secret: **************************"))
            .anyMatch(msg -> msg.contains("db.password: **************************"))
            .noneMatch(msg -> msg.contains("supersecret"))
            .noneMatch(msg -> msg.contains("verysecret"))
            .noneMatch(msg -> msg.contains("dbpassword"));
    }

    @Test
    void masksPropertyValueContainingPasswordKeyword() {
        GenericApplicationContext context = contextWithProperties(Map.of("app.credential.name", "mysecretvalue"));

        listener.doHandleContextRefresh(new ContextRefreshedEvent(context));

        assertThat(messages()).anyMatch(msg -> msg.contains("app.credential.name: **************************"))
            .noneMatch(msg -> msg.contains("mysecretvalue"));
    }

    private static GenericApplicationContext contextWithProperties(Map<String, Object> properties) {
        GenericApplicationContext context = new GenericApplicationContext();
        MutablePropertySources propertySources = context.getEnvironment().getPropertySources();
        propertySources.addFirst(new MapPropertySource("test", properties));
        context.refresh();
        return context;
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

}
