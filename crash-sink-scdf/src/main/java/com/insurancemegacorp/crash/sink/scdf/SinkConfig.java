package com.insurancemegacorp.crash.sink.scdf;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for crash-sink-scdf beans.
 */
@Configuration
public class SinkConfig {

    /**
     * RestTemplate for HTTP calls to the orchestrator.
     * Configured with reasonable timeouts for SCDF stream processing.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60)) // FNOL processing can take time
                .build();
    }
}
