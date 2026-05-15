package ch.dboeckli.example.otel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class SpringApplication {

    public static void main(String[] args) {
        log.info("Starting Spring 6 Application...");
        org.springframework.boot.SpringApplication.run(SpringApplication.class, args);
    }

}
