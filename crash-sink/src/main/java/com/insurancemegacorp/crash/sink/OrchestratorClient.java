package com.insurancemegacorp.crash.sink;

import com.insurancemegacorp.crash.domain.AccidentEvent;
import com.insurancemegacorp.crash.domain.FNOLReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for forwarding accident events to the orchestrator service.
 */
@Component
public class OrchestratorClient {
    private static final Logger log = LoggerFactory.getLogger(OrchestratorClient.class);

    private final RestTemplate restTemplate;
    private final String orchestratorUrl;

    public OrchestratorClient(
            RestTemplate restTemplate,
            @Value("${orchestrator.url}") String orchestratorUrl
    ) {
        this.restTemplate = restTemplate;
        this.orchestratorUrl = orchestratorUrl;
    }

    /**
     * Submit an accident event to the orchestrator for FNOL processing.
     *
     * @param event the accident event to process
     * @return the generated FNOL report, or null if processing failed
     */
    public FNOLReport submitAccident(AccidentEvent event) {
        String url = orchestratorUrl + "/api/accidents";
        log.info("Forwarding accident to orchestrator: {} - policyId={}, gForce={}",
                url, event.policyId(), event.gForce());

        try {
            ResponseEntity<FNOLReport> response = restTemplate.postForEntity(
                url,
                event,
                FNOLReport.class
            );

            FNOLReport report = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && report != null) {
                log.info("Orchestrator processed accident successfully: claimNumber={}", report.claimNumber());
                return report;
            }
            log.error("Orchestrator returned non-success status: {}", response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed to forward accident to orchestrator: {}", e.getMessage(), e);
            return null;
        }
    }
}
