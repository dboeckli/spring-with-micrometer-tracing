package ch.dboeckli.example.otel.rest;

import ch.dboeckli.example.otel.service.HelloService;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Slf4j
public class HelloController {

    protected final static String HELLO_MESSAGE = "Say Hello...";

    private final HelloService helloService;

    private final Tracer tracer;

    public HelloController(HelloService helloService, Tracer tracer) {
        this.helloService = helloService;
        this.tracer = tracer;
    }

    @Observed // is not really needed. it is observed by default
    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        CurrentTraceContext currentTraceContext = tracer.currentTraceContext();
        Map<String, String> baggageMap = tracer.getAllBaggage(currentTraceContext.context());

        log.info("{} with Baggage: {} ", HELLO_MESSAGE, baggageMap);

        try (BaggageInScope _ = tracer.createBaggageInScope("addedBaggageByController", "echo from controller")) {
            helloService.processHello();
        }

        return new ResponseEntity<>("{\"message\":\"hello\"}", HttpStatus.OK);
    }

}
