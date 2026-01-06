package com.insurancemegacorp.crash.orchestrator;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.insurancemegacorp.crash.domain.AccidentEvent;
import com.insurancemegacorp.crash.domain.FNOLReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * REST API for receiving accident events and triggering FNOL processing.
 */
@RestController
@RequestMapping("/api")
public class AccidentController {

    private static final Logger log = LoggerFactory.getLogger(AccidentController.class);

    private final AgentPlatform agentPlatform;

    public AccidentController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    /**
     * Receive an accident event and process it through the FNOL agent pipeline.
     */
    @PostMapping("/accident")
    public ResponseEntity<FNOLReport> processAccident(@RequestBody AccidentEvent event) {
        log.info("Received accident event for policy {} at ({}, {})",
                 event.policyId(), event.latitude(), event.longitude());

        // Check if this meets accident threshold
        if (event.gForce() < 2.5) {
            log.info("G-force {} below threshold, not processing as accident", event.gForce());
            return ResponseEntity.badRequest().build();
        }

        log.info("Processing accident with g-force {}, severity threshold exceeded", event.gForce());

        // Run the FNOL agent using AgentInvocation
        var invocation = AgentInvocation.create(agentPlatform, FNOLReport.class);
        FNOLReport report = invocation.invoke(event);

        log.info("Generated FNOL report: claim #{}", report.claimNumber());

        return ResponseEntity.ok(report);
    }

    /**
     * Async version for non-blocking processing.
     */
    @PostMapping("/accident/async")
    public CompletableFuture<ResponseEntity<FNOLReport>> processAccidentAsync(
            @RequestBody AccidentEvent event
    ) {
        return CompletableFuture.supplyAsync(() -> processAccident(event));
    }

    /**
     * Simulate an accident for demo purposes.
     */
    @PostMapping("/accident/simulate")
    public ResponseEntity<FNOLReport> simulateAccident(
            @RequestParam(defaultValue = "200018") int policyId,
            @RequestParam(defaultValue = "3.8") double gForce,
            @RequestParam(defaultValue = "34.5") double speedMph
    ) {
        AccidentEvent simulatedEvent = new AccidentEvent(
            policyId,
            300000 + policyId,
            400000 + policyId,
            "1HGBH41JXMN" + String.format("%06d", policyId),
            Instant.now(),
            speedMph,
            35,
            gForce,
            39.1157,  // Leesburg, VA
            -77.5636,
            "Main Street",
            -2.1,
            0.3,
            0.8
        );

        log.info("Simulating accident: policy={}, gForce={}, speed={}",
                 policyId, gForce, speedMph);

        return processAccident(simulatedEvent);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("FNOL Orchestrator is running");
    }
}
