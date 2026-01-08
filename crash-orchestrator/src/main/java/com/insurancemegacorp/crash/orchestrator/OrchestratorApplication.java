package com.insurancemegacorp.crash.orchestrator;

import com.embabel.agent.config.annotation.EnableAgents;
import com.insurancemegacorp.crash.orchestrator.config.McpToolGroupsConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * CRASH Orchestrator Application.
 *
 * Consumes vehicle accident events from RabbitMQ, processes them through
 * the Embabel agent pipeline, and outputs FNOL reports to both a database
 * and an output queue.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.insurancemegacorp.crash.orchestrator",  // Our project
    "com.embabel.agent"                          // Force Embabel overlap
})
@EnableAgents
@Import(McpToolGroupsConfiguration.class)
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
