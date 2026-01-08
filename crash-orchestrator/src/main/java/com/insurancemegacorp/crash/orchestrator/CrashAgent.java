package com.insurancemegacorp.crash.orchestrator;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.insurancemegacorp.crash.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CRASH Agent - Orchestrates multiple specialist agents to generate
 * a complete First Notice of Loss report from accident telemetry.
 *
 * Uses Embabel's goal-based planning to dynamically determine execution order
 * based on dependencies between actions.
 */
@Agent(description = "First Notice of Loss Agent - Processes accident events and coordinates " +
                     "specialist agents to generate complete FNOL claim reports")
@Component
public class CrashAgent {

    private static final Logger log = LoggerFactory.getLogger(CrashAgent.class);

    /**
     * Action 1: Analyze the impact using the Impact Analyst MCP server.
     * This is typically executed first as severity classification affects other actions.
     */
    @Action(
        description = "Analyze accident telemetry to determine severity and impact type",
        toolGroups = {"impact-analyst-tools"}
    )
    public ImpactAnalysis analyzeImpact(AccidentEvent event, Ai ai) {
        log.info("Analyzing impact for accident: policyId={}, gForce={}", event.policyId(), event.gForce());
        return ai.withAutoLlm().createObject(
            """
            Use the Impact Analyst tools to analyze this accident.

            Call the analyze_impact tool with:
            - gForce: %f
            - speedMph: %f
            - speedLimitMph: %d
            - accelerometerX: %f
            - accelerometerY: %f
            - accelerometerZ: %f

            Return the complete ImpactAnalysis result.
            """.formatted(
                event.gForce(),
                event.speedMph(),
                event.speedLimitMph(),
                event.accelerometerX(),
                event.accelerometerY(),
                event.accelerometerZ()
            ),
            ImpactAnalysis.class
        );
    }

    /**
     * Action 2: Gather environmental context using the Environment MCP server.
     * Can run in parallel with Policy lookup (no dependency).
     */
    @Action(
        description = "Gather weather, location, and road conditions at accident site",
        toolGroups = {"environment-tools"}
    )
    public EnvironmentContext gatherEnvironment(AccidentEvent event, Ai ai) {
        log.info("Gathering environment for accident: lat={}, lon={}", event.latitude(), event.longitude());
        return ai.withAutoLlm().createObject(
            """
            Use the Environment Agent tools to gather context for this accident.

            Call get_full_environment_context with:
            - latitude: %f
            - longitude: %f
            - timestamp: "%s"

            Return the complete EnvironmentContext result.
            """.formatted(
                event.latitude(),
                event.longitude(),
                event.eventTime().toString()
            ),
            EnvironmentContext.class
        );
    }

    /**
     * Action 3: Look up policy information using the Policy MCP server.
     * Can run in parallel with Environment gathering (no dependency).
     */
    @Action(
        description = "Retrieve policy, driver, and vehicle information",
        toolGroups = {"policy-tools"}
    )
    public PolicyInfo lookupPolicy(AccidentEvent event, Ai ai) {
        log.info("Looking up policy: policyId={}, driverId={}", event.policyId(), event.driverId());
        return ai.withAutoLlm().createObject(
            """
            Use the Policy Agent tools to look up insurance information.

            Call get_full_policy_info with:
            - policyId: %d
            - driverId: %d
            - vehicleId: %d
            - vin: "%s"

            Return the complete PolicyInfo result.
            """.formatted(
                event.policyId(),
                event.driverId(),
                event.vehicleId(),
                event.vin()
            ),
            PolicyInfo.class
        );
    }

    /**
     * Action 4: Find nearby services using the Services MCP server.
     * Depends on ImpactAnalysis for severity-based recommendations.
     */
    @Action(
        description = "Find nearby body shops, tow services, and hospitals based on severity",
        toolGroups = {"services-tools"}
    )
    public NearbyServices findServices(AccidentEvent event, ImpactAnalysis impact, Ai ai) {
        log.info("Finding services for accident: severity={}, location=({}, {})",
                impact.severity(), event.latitude(), event.longitude());
        return ai.withAutoLlm().createObject(
            """
            Use the Services Agent tools to find nearby services.

            Call get_all_nearby_services with:
            - latitude: %f
            - longitude: %f
            - severity: "%s"
            - radiusMiles: 5.0

            Return the complete NearbyServices result.
            """.formatted(
                event.latitude(),
                event.longitude(),
                impact.severity().name()
            ),
            NearbyServices.class
        );
    }

    /**
     * Action 5: Initiate communications using the Communications MCP server.
     * Depends on PolicyInfo for driver contact information and ImpactAnalysis for severity.
     */
    @Action(
        description = "Send driver wellness check and notify adjuster if needed",
        toolGroups = {"communications-tools"}
    )
    public CommunicationsStatus initiateComms(
            AccidentEvent event,
            PolicyInfo policy,
            ImpactAnalysis impact,
            Ai ai
    ) {
        String claimReference = "CLM-" + java.time.Year.now().getValue() + "-" + event.policyId();
        log.info("Initiating communications: claimRef={}, driver={}, severity={}",
                claimReference, policy.driver().name(), impact.severity());

        return ai.withAutoLlm().createObject(
            """
            Use the Communications Agent tools to initiate driver outreach.

            Call get_full_communications_status with:
            - claimReference: "%s"
            - driverName: "%s"
            - driverPhone: "%s"
            - severity: "%s"

            Return the complete CommunicationsStatus result.
            """.formatted(
                claimReference,
                policy.driver().name(),
                policy.driver().phone(),
                impact.severity().name()
            ),
            CommunicationsStatus.class
        );
    }

    /**
     * Final Action: Compile the complete FNOL report.
     * Achieves the goal when all specialist agent results are available.
     */
    @AchievesGoal(description = "Generate complete First Notice of Loss report from accident telemetry")
    @Action(description = "Compile all agent results into final FNOL report")
    public FNOLReport compileReport(
            AccidentEvent event,
            ImpactAnalysis impact,
            EnvironmentContext environment,
            PolicyInfo policy,
            NearbyServices services,
            CommunicationsStatus communications
    ) {
        log.info("Compiling FNOL report for policyId={}, severity={}",
                event.policyId(), impact.severity());

        // Build recommended actions based on severity
        List<String> recommendedActions = new ArrayList<>();
        recommendedActions.add("Review claim within 24 hours");

        if (impact.severity() == ImpactAnalysis.Severity.SEVERE) {
            recommendedActions.add("PRIORITY: Contact driver immediately to verify welfare");
            recommendedActions.add("Assign senior adjuster for complex claim handling");
            recommendedActions.add("Request police report");
        } else if (impact.severity() == ImpactAnalysis.Severity.MODERATE) {
            recommendedActions.add("Schedule vehicle inspection within 48 hours");
            recommendedActions.add("Follow up with driver for photos");
        } else {
            recommendedActions.add("Request photos from driver via mobile app");
        }

        if (services.vehicleLikelyDrivable()) {
            recommendedActions.add("Provide body shop referrals to driver");
        } else {
            recommendedActions.add("Confirm tow service dispatch");
            recommendedActions.add("Arrange rental car if covered");
        }

        // Build alerts
        List<String> alerts = new ArrayList<>();

        if (impact.wasSpeeding()) {
            alerts.add("Driver was exceeding speed limit at time of incident");
        }

        if (impact.airbagLikely()) {
            alerts.add("Airbag deployment likely - verify driver welfare");
        }

        if (environment.weather().precipitation() != null) {
            alerts.add("Adverse weather conditions: " + environment.weather().precipitation());
        }

        if (!environment.contributingFactors().isEmpty()) {
            alerts.add("Contributing factors identified: " +
                      String.join(", ", environment.contributingFactors()));
        }

        if (!communications.driverOutreach().responseStatus().equals("CONFIRMED_OK")) {
            alerts.add("Awaiting driver response to wellness check");
        }

        String claimNumber = "CLM-" + java.time.Year.now().getValue() + "-" +
                            String.format("%06d", (int)(Math.random() * 999999));

        log.info("FNOL report compiled: claimNumber={}, alerts={}", claimNumber, alerts.size());

        return new FNOLReport(
            claimNumber,
            "INITIATED",
            event,
            impact,
            environment,
            policy,
            services,
            communications,
            Instant.now(),
            recommendedActions,
            alerts
        );
    }
}
