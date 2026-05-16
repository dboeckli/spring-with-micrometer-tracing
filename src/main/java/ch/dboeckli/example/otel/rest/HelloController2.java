package ch.dboeckli.example.otel.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HelloController2 {

    @GetMapping("/hello2")
    public ResponseEntity<String> hello() {
        return new ResponseEntity<>("{\"message\":\"hello\"}", HttpStatus.OK);
    }

}
