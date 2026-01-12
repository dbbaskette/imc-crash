package com.insurancemegacorp.crash.sink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CRASH Sink Application - Lightweight RabbitMQ consumer.
 *
 * This service:
 * 1. Consumes telemetry messages from RabbitMQ
 * 2. Filters for accident events (g-force >= 2.5)
 * 3. Forwards accidents to the orchestrator via HTTP
 *
 * Can be horizontally scaled for high-traffic scenarios.
 */
@SpringBootApplication
public class SinkApplication {
    public static void main(String[] args) {
        SpringApplication.run(SinkApplication.class, args);
    }
}
