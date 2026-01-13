package com.insurancemegacorp.crash.sink.scdf;

import com.insurancemegacorp.crash.domain.AccidentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for forwarding detected accidents to the crash-orchestrator.
 * Uses blocking HTTP calls which is acceptable for SCDF stream processing.
 */
@Component
public class OrchestratorClient {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorClient.class);

    private final RestTemplate restTemplate;

    @Value("${orchestrator.url:http://localhost:8080}")
    private String orchestratorUrl;

    public OrchestratorClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Submit an accident event to the orchestrator for FNOL processing.
     *
     * @param event the accident event to process
     */
    public void submitAccident(AccidentEvent event) {
        String url = orchestratorUrl + "/api/accidents";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, event, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Forwarded accident to orchestrator: policyId={}, status={}",
                        event.policyId(), response.getStatusCode());
            } else {
                log.warn("Orchestrator returned non-success: policyId={}, status={}",
                        event.policyId(), response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to forward accident to orchestrator: policyId={}, error={}",
                    event.policyId(), e.getMessage());
            // Don't rethrow - let DLQ handle if configured, otherwise log and continue
        }
    }
}
