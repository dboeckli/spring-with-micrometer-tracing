package ch.dboeckli.example.otel.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

@Service
@Slf4j
public class HelloService {

    public final static String HELLO_MESSAGE_FROM_SERVICE = "Service Sais Hello...";

    private final Tracer tracer;

    public HelloService(OpenTelemetry openTelemetry, @Value("${spring.application.name}") String appName,
            BuildProperties buildProperties) {
        this.tracer = openTelemetry.getTracer(appName, buildProperties.getVersion());
    }

    public String processHello() {
        // Create a span for the entire service processing operation
        Span serviceSpan = tracer.spanBuilder("process-hello")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("app.custom.service.flag", "helloFromService")
            .startSpan();

        String userRole = Baggage.current().getEntryValue("app.user.role");
        String requestSource = Baggage.current().getEntryValue("app.request.source");
        log.info("{} [baggage: app.user.role={}, app.request.source={}]", HELLO_MESSAGE_FROM_SERVICE, userRole,
                requestSource);

        // Make the span current for this execution context
        try (Scope scope = serviceSpan.makeCurrent()) {
            // Log events within the span
            serviceSpan.addEvent("service-started");

            try {
                serviceSpan.addEvent("service-completed");
                // Set span status to success
                serviceSpan.setStatus(StatusCode.OK);
                return "Hello from the service";
            }
            catch (Exception e) {
                // Record error information
                serviceSpan.setStatus(StatusCode.ERROR, e.getMessage());
                serviceSpan.recordException(e,
                        Attributes.of(AttributeKey.stringKey("exception.type"), e.getClass().getName(),
                                AttributeKey.stringKey("exception.stacktrace"), getStackTraceAsString(e)));
                throw e;
            }
        }
        finally {
            // Always end the span
            serviceSpan.end();
        }
    }

    private String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter(1024);
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        }
    }

}
