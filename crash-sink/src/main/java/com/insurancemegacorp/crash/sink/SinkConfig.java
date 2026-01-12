package com.insurancemegacorp.crash.sink;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the crash-sink service.
 */
@Configuration
public class SinkConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
