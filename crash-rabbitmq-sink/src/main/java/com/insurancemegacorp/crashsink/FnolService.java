package com.insurancemegacorp.crashsink;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.insurancemegacorp.crash.domain.AccidentEvent;
import com.insurancemegacorp.crash.domain.FNOLReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that orchestrates FNOL generation using the Embabel CrashAgent.
 *
 * This service wraps the AgentPlatform invocation and provides a clean interface
 * for the CrashSink to generate FNOL reports from AccidentEvents.
 */
@Service
public class FnolService {

    private static final Logger log = LoggerFactory.getLogger(FnolService.class);

    private final AgentPlatform agentPlatform;

    public FnolService(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
        log.info("FnolService initialized with AgentPlatform");
    }

    /**
     * Process an accident event through the FNOL agent pipeline.
     *
     * The CrashAgent will:
     * 1. Analyze impact severity (Impact Analyst MCP)
     * 2. Gather environmental context (Environment MCP - Open-Meteo weather)
     * 3. Look up policy information (Policy MCP)
     * 4. Find nearby services (Services MCP)
     * 5. Initiate communications (Communications MCP)
     * 6. Compile the final FNOL report
     *
     * @param event The accident event from telemetry
     * @return The generated FNOL report
     */
    public FNOLReport processAccident(AccidentEvent event) {
        log.info("========================================");
        log.info("FNOL PROCESSING STARTED");
        log.info("========================================");
        log.info("Policy ID:    {}", event.policyId());
        log.info("G-Force:      {}", event.gForce());
        log.info("Speed:        {} mph", event.speedMph());
        log.info("Location:     ({}, {})", event.latitude(), event.longitude());
        log.info("----------------------------------------");

        long startTime = System.currentTimeMillis();

        try {
            // Create agent invocation targeting FNOLReport as the goal
            var invocation = AgentInvocation.create(agentPlatform, FNOLReport.class);

            // Invoke the agent with the AccidentEvent as input
            // Embabel will:
            // - Find CrashAgent which can achieve FNOLReport
            // - Build execution plan based on action dependencies
            // - Execute actions (parallel where possible)
            // - Return the completed FNOL report
            FNOLReport report = invocation.invoke(event);

            long duration = System.currentTimeMillis() - startTime;

            log.info("========================================");
            log.info("FNOL PROCESSING COMPLETE");
            log.info("========================================");
            log.info("Claim Number: {}", report.claimNumber());
            log.info("Status:       {}", report.status());
            log.info("Severity:     {}", report.impact().severity());
            log.info("Duration:     {} ms", duration);
            log.info("Alerts:       {}", report.alerts().size());
            log.info("Actions:      {}", report.recommendedActions().size());
            log.info("========================================");

            return report;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("========================================");
            log.error("FNOL PROCESSING FAILED");
            log.error("========================================");
            log.error("Policy ID:  {}", event.policyId());
            log.error("Duration:   {} ms", duration);
            log.error("Error:      {}", e.getMessage());
            log.error("========================================", e);
            throw new RuntimeException("FNOL processing failed for policy " + event.policyId(), e);
        }
    }

    /**
     * Check if an accident event should be processed.
     *
     * @param gForce The g-force reading from the accident
     * @return true if the event should be processed as an accident
     */
    public boolean shouldProcess(double gForce) {
        // Threshold for processing as potential accident
        // Minor: 2.5-3.0g, Moderate: 3.0-5.0g, Severe: 5.0g+
        boolean shouldProcess = gForce >= 2.5;
        if (!shouldProcess) {
            log.debug("G-force {} below threshold 2.5, skipping FNOL processing", gForce);
        }
        return shouldProcess;
    }
}
