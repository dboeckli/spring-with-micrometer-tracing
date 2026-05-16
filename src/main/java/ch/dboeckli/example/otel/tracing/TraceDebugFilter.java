package ch.dboeckli.example.otel.tracing;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

@Component
@Slf4j
public class TraceDebugFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public TraceDebugFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Log incoming traceparent header
        String incomingTraceparent = request.getHeader("traceparent");
        log.info("### Incoming traceparent: {}", incomingTraceparent);

        // Log current span context
        TraceContext spanContext = Objects.requireNonNull(tracer.currentSpan()).context();
        if (Boolean.TRUE.equals(spanContext.sampled())) {
            log.info("### Current trace context - TraceId: {}, SpanId: {}", spanContext.traceId(),
                    spanContext.spanId());
        }
        else {
            log.warn("### No valid span context found");
        }

        filterChain.doFilter(request, response);
    }

}
