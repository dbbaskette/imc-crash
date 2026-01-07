package com.insurancemegacorp.crashsink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CRASH RabbitMQ Sink Application.
 *
 * Consumes vehicle accident events from RabbitMQ, processes them through
 * the Embabel agent pipeline, and outputs FNOL reports to both a database
 * and an output queue.
 */
@SpringBootApplication
public class CrashSinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrashSinkApplication.class, args);
    }
}
