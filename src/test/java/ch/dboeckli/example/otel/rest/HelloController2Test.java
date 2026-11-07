package ch.dboeckli.example.otel.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class HelloController2Test {

    private final HelloController2 controller = new HelloController2();

    @Test
    void helloReturnsHelloMessage() {
        ResponseEntity<String> response = controller.hello();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"hello\"}");
    }

}
