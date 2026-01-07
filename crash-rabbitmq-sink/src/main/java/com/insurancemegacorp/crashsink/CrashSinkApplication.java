package com.insurancemegacorp.crashsink;

import com.embabel.agent.config.annotation.EnableAgents;
import com.insurancemegacorp.crashsink.config.McpToolGroupsConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * CRASH RabbitMQ Sink Application.
 *
 * Consumes vehicle accident events from RabbitMQ, processes them through
 * the Embabel agent pipeline, and outputs FNOL reports to both a database
 * and an output queue.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.insurancemegacorp.crashsink",  // Our project
    "com.embabel.agent"                 // Force Embabel overlap
})
@EnableAgents
@Import(McpToolGroupsConfiguration.class)
public class CrashSinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrashSinkApplication.class, args);
    }
}
