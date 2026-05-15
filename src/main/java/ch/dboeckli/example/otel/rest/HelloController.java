package ch.dboeckli.example.otel.rest;

import ch.dboeckli.example.otel.service.HelloService;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HelloController {

    protected final static String HELLO_MESSAGE = "Say Hello...";

    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        log.info("### HelloController.hello() 1");
        Baggage baggage = Baggage.current().toBuilder().put("addedBaggageByController", "gugus").build();

        try (Scope ignored = baggage.storeInContext(Context.current()).makeCurrent()) {
            baggage.asMap().forEach((key, entry) -> Span.current().setAttribute(key, entry.getValue()));
            log.info(HELLO_MESSAGE);
            log.info("### HelloController.hello() 2");
            helloService.processHello();
        }

        return new ResponseEntity<>("{\"message\":\"hello\"}", HttpStatus.OK);
    }

}
