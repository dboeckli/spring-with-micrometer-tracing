package ch.dboeckli.example.otel.service;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class HelloService {

    public static final String HELLO_MESSAGE_FROM_SERVICE = "Service Sais Hello...";

    private static final String SERVICE_STARTED = "service-started";

    private final Tracer tracer;

    public HelloService(Tracer tracer) {
        this.tracer = tracer;
    }

    /*
     * Baggage sollte nach dem Span-Scope kommen, damit das Baggage am neuen Span hängt –
     * nicht am alten
     */
    public String processHello() {
        CurrentTraceContext currentTraceContext = tracer.currentTraceContext();
        Map<String, String> baggageMap = tracer.getAllBaggage(currentTraceContext.context());

        Span newSpan = tracer.nextSpan().name("processHello").start();

        try (var _ = tracer.withSpan(newSpan);
                var _ = tracer.createBaggageInScope("addedBaggageByService", "echo from service")) {
            log.info("### Hello from Baggage! " + baggageMap);

            newSpan.tag("processHello", "spanValue");

            // events are visible only in jaeger and zipkin, in kibana not
            newSpan.event(SERVICE_STARTED);
            log.info(SERVICE_STARTED); // landet in Kibana
            newSpan.event(SERVICE_STARTED);
            log.info("service-stopped"); // landet in Kibana

            return HELLO_MESSAGE_FROM_SERVICE;
        }
        catch (Exception e) {
            newSpan.error(e);
            throw e;
        }
        finally {
            newSpan.end();
        }

    }

}
