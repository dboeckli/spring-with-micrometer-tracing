package ch.dboeckli.example.otel.tracing;

import io.micrometer.tracing.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class BaggageTaggingFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public BaggageTaggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        CurrentTraceContext currentTraceContext = tracer.currentTraceContext();
        Map<String, String> baggageMap = tracer.getAllBaggage(currentTraceContext.context());

        Map<String, String> headers = Collections.list(request.getHeaderNames())
            .stream()
            .collect(Collectors.toMap(name -> name, request::getHeader));
        log.info("### Hello from Baggage: {}. Headers: {}", baggageMap, headers);

        Span currentSpan = tracer.currentSpan();

        try (Tracer.SpanInScope _ = tracer.withSpan(currentSpan);
                BaggageInScope _ = tracer.createBaggageInScope("addedBaggageByFilter", "echo from filter")) {

            addCurrentBaggageFromRequest(currentSpan, baggageMap);

            filterChain.doFilter(request, response);
        }

    }

    /*
     * here we add all baggage sent with the request. we need to set manually because it
     * is not set like the addedBaggageByFilter
     */
    private void addCurrentBaggageFromRequest(Span currentSpan, Map<String, String> baggageMap) {
        if (currentSpan != null && baggageMap != null) {
            baggageMap.forEach((key, value) -> {
                if (value != null) {
                    currentSpan.tag(key, value);
                }
            });
        }
    }

}
