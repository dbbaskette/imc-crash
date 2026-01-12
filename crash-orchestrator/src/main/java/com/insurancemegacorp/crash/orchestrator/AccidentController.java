package com.insurancemegacorp.crash.orchestrator;

import com.insurancemegacorp.crash.domain.AccidentEvent;
import com.insurancemegacorp.crash.domain.FNOLReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for receiving accident events from the crash-sink service.
 *
 * This replaces the direct RabbitMQ consumption, allowing the orchestrator
 * to focus on FNOL processing while the sink handles message ingestion.
 */
@RestController
@RequestMapping("/api/accidents")
public class AccidentController {
    private static final Logger log = LoggerFactory.getLogger(AccidentController.class);

    private final FnolService fnolService;
    private final FnolPersistenceService persistenceService;

    public AccidentController(FnolService fnolService, FnolPersistenceService persistenceService) {
        this.fnolService = fnolService;
        this.persistenceService = persistenceService;
    }

    /**
     * Process an accident event and generate an FNOL report.
     *
     * @param event the accident event from the sink
     * @return the generated FNOL report
     */
    @PostMapping
    public ResponseEntity<FNOLReport> processAccident(@RequestBody AccidentEvent event) {
        log.info("ACCIDENT RECEIVED - policyId={}, vehicleId={}, driverId={}, vin={}",
                event.policyId(), event.vehicleId(), event.driverId(), event.vin());
        log.info("Impact: gForce={}, speed={} mph (limit: {})",
                event.gForce(), event.speedMph(), event.speedLimitMph());
        log.info("Location: {} ({}, {}) at {}",
                event.currentStreet(), event.latitude(), event.longitude(), event.eventTime());

        try {
            // Process through Embabel agents
            FNOLReport report = fnolService.processAccident(event);

            // Persist to database
            try {
                FnolEntity saved = persistenceService.persist(report);
                log.info("FNOL persisted to database: id={}, claim={}",
                        saved.getId(), saved.getClaimNumber());
            } catch (Exception e) {
                log.error("Failed to persist FNOL to database: {}", e.getMessage(), e);
                // Continue - report was still generated
            }

            log.info("FNOL report generated: claimNumber={}, severity={}",
                    report.claimNumber(), report.impact().severity());

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("Failed to process accident: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check endpoint for accident processing.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
