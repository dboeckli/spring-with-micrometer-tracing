package ch.dboeckli.example.otel.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class TraceParentFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public TraceParentFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Span currentSpan = tracer.currentSpan();

        log.info("### currentSpan: {}", currentSpan);

        if (currentSpan == null) {
            log.warn("No active span found, skipping traceparent header.");
            filterChain.doFilter(request, response);
            return;
        }

        String traceParent = request.getHeader("traceparent");

        if (traceParent == null || traceParent.isBlank()) {
            log.info("Traceparent was null or empty, setting traceparent header.");

            String traceId = currentSpan.context().traceId();
            String spanId = currentSpan.context().spanId();

            String traceParentValue = String.format("00-%s-%s-01", traceId, spanId);
            log.info("Setting traceparent header: {}", traceParentValue);

            traceParent = traceParentValue;

            response.setHeader("traceparent", traceParentValue);
        }
        else {
            log.info("Traceparent already present: {}", traceParent);
        }
        TraceparentValidator.isValidTraceparent(traceParent);

        filterChain.doFilter(request, response);
    }

}
