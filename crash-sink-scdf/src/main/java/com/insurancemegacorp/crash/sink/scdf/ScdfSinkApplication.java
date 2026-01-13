package com.insurancemegacorp.crash.sink.scdf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SCDF Stream processor application for CRASH accident detection.
 * Consumes flattened telemetry from RabbitMQ, filters for accidents,
 * and forwards to the crash-orchestrator via HTTP.
 */
@SpringBootApplication
public class ScdfSinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScdfSinkApplication.class, args);
    }
}
