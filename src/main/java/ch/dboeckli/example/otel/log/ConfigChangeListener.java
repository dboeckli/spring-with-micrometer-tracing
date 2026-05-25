package ch.dboeckli.example.otel.log;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
@Slf4j
public class ConfigChangeListener {

    private static final List<String> PASSWORD_KEY_LIST = Arrays.asList("jwt.key-value", "password", "credentials",
            "secret");

    private final ObservationRegistry observationRegistry;

    private final Tracer tracer;

    public ConfigChangeListener(ObservationRegistry observationRegistry, Tracer tracer) {
        this.observationRegistry = observationRegistry;
        this.tracer = tracer;
    }

    @EventListener
    @Observed(name = "config.change.listener", contextualName = "handle-context-refresh")
    public void doHandleContextRefresh(ContextRefreshedEvent event) {
        final Environment env = event.getApplicationContext().getEnvironment();
        log.debug(LogMessage.RECEIVED_CONTEXT_REFRESH_EVENT.getMessage());
        log.info("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
        final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
        StreamSupport.stream(sources.spliterator(), false)
            .filter(EnumerablePropertySource.class::isInstance)
            .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
            .flatMap(Arrays::stream)
            .distinct()
            .forEach(prop -> {
                String propertyValue = env.getProperty(prop);
                if (propertyValue != null) {

                    if (PASSWORD_KEY_LIST.stream().anyMatch(prop.toLowerCase()::contains)
                            || PASSWORD_KEY_LIST.stream().anyMatch(propertyValue.toLowerCase()::contains)) {

                        log.info("{}: {}", prop, "**************************"); // hide
                                                                                // password
                    }
                    else {
                        log.info("{}: {}", prop, propertyValue);
                    }

                }
                else {
                    log.warn("null propertyValue encountered in {}: {}", prop, propertyValue);
                }
            });
        showTracerProvider();
    }

    private void showTracerProvider() {
        log.info("### Micrometer Tracer impl: {}", tracer.getClass().getName());
        log.info("### Tracer details: {}", ReflectionToStringBuilder.toString(tracer, ToStringStyle.MULTI_LINE_STYLE));

        log.info("### ObservationRegistry impl: {}", observationRegistry.getClass().getName());

        ObservationRegistry.ObservationConfig config = observationRegistry.observationConfig();
        log.info("### ObservationConfig: {}",
                ReflectionToStringBuilder.toString(config, ToStringStyle.MULTI_LINE_STYLE));

        // Alle registrierten ObservationHandler auflisten (zeigt u.a.
        // Tracing-/Metrics-Handler)
        try {
            java.lang.reflect.Field handlersField = ObservationRegistry.ObservationConfig.class
                .getDeclaredField("observationHandlers");
            handlersField.setAccessible(true);
            Object handlers = handlersField.get(config);
            if (handlers instanceof Iterable<?> iterable) {
                for (Object handler : iterable) {
                    log.info("### ObservationHandler: {}", handler.getClass().getName());
                }
            }
            else {
                log.info("### ObservationHandlers: {}", handlers);
            }
        }
        catch (Exception e) {
            log.warn("### Could not inspect ObservationHandlers: {}", e.getMessage());
        }
    }

}
