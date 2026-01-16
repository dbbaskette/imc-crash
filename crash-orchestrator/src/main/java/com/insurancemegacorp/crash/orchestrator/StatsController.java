package com.insurancemegacorp.crash.orchestrator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for crash processing statistics.
 * Provides endpoints for the dashboard to display metrics.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * Get the count of accidents reported to the orchestrator.
     */
    @GetMapping("/accidents-reported")
    public ResponseEntity<Map<String, Long>> getAccidentsReported() {
        return ResponseEntity.ok(Map.of("count", statsService.getAccidentsReported()));
    }

    /**
     * Get the count of FNOL reports successfully processed.
     */
    @GetMapping("/fnol-processed")
    public ResponseEntity<Map<String, Long>> getFnolProcessed() {
        return ResponseEntity.ok(Map.of("processed", statsService.getFnolProcessed()));
    }

    /**
     * Get the count of SMS messages sent.
     */
    @GetMapping("/sms")
    public ResponseEntity<Map<String, Long>> getSmsSent() {
        return ResponseEntity.ok(Map.of("sent", statsService.getSmsSent()));
    }

    /**
     * Get the count of emails sent.
     */
    @GetMapping("/email")
    public ResponseEntity<Map<String, Long>> getEmailsSent() {
        return ResponseEntity.ok(Map.of("sent", statsService.getEmailsSent()));
    }
}
