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
     * Action 6: Compile the FNOL report (not final - email follows).
     */
    @Action(description = "Compile all agent results into FNOL report")
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

    /**
     * Final Action: Send FNOL email to adjuster and return the report.
     * This is the goal - achieves the workflow by sending the compiled report via email.
     */
    @AchievesGoal(description = "Generate complete First Notice of Loss report and email to adjuster")
    @Action(
        description = "Email the compiled FNOL report to the claims adjuster and return the report",
        toolGroups = {"communications-tools"}
    )
    public FNOLReport sendFnolToAdjuster(
            FNOLReport report,
            Ai ai
    ) {
        log.info("Sending FNOL email for claim: {}, severity: {}",
                report.claimNumber(), report.impact().severity());

        // Build the report content for the email
        String reportContent = buildEmailReportContent(report);

        // Call notifyAdjuster with the full FNOL report
        try {
            ai.withAutoLlm().createObject(
                """
                Call the notifyAdjuster tool to send the FNOL report email to the claims adjuster.

                Use these parameters:
                - claimNumber: "%s"
                - severity: "%s"
                - summary: "%s"

                This will email the full FNOL report to the assigned adjuster.
                Return confirmation of the notification.
                """.formatted(
                    report.claimNumber(),
                    report.impact().severity().name(),
                    reportContent.replace("\"", "\\\"").replace("\n", "\\n")
                ),
                EmailConfirmation.class
            );
            log.info("FNOL email sent for claim: {}", report.claimNumber());
        } catch (Exception e) {
            log.error("Failed to send FNOL email for claim {}: {}", report.claimNumber(), e.getMessage());
        }

        // Return the original report
        return report;
    }

    private String buildEmailReportContent(FNOLReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append("ACCIDENT DETAILS\n");
        sb.append("================\n");
        sb.append("Date/Time: ").append(report.event().eventTime()).append("\n");
        sb.append("Location: ").append(report.event().currentStreet()).append("\n");
        sb.append("Coordinates: ").append(report.event().latitude()).append(", ").append(report.event().longitude()).append("\n\n");

        sb.append("IMPACT ANALYSIS\n");
        sb.append("================\n");
        sb.append("Severity: ").append(report.impact().severity()).append("\n");
        sb.append("Impact Type: ").append(report.impact().impactType()).append("\n");
        sb.append("G-Force: ").append(String.format("%.1f", report.event().gForce())).append("\n");
        sb.append("Speed at Impact: ").append(String.format("%.0f", report.impact().estimatedSpeedAtImpact())).append(" mph");
        sb.append(" (Limit: ").append(report.event().speedLimitMph()).append(" mph)\n");
        if (report.impact().wasSpeeding()) {
            sb.append("⚠ SPEEDING: Driver exceeded speed limit by ")
              .append(String.format("%.0f", report.impact().estimatedSpeedAtImpact() - report.event().speedLimitMph()))
              .append(" mph\n");
        }
        if (report.impact().airbagLikely()) {
            sb.append("AIRBAG: Deployment likely\n");
        }
        sb.append("\n");

        sb.append("INCIDENT NARRATIVE\n");
        sb.append("==================\n");
        sb.append(report.impact().narrative()).append("\n\n");

        sb.append("DRIVER & VEHICLE\n");
        sb.append("================\n");
        sb.append("Driver: ").append(report.policy().driver().name()).append("\n");
        sb.append("Phone: ").append(report.policy().driver().phone()).append("\n");
        sb.append("Vehicle: ").append(report.policy().vehicle().year()).append(" ")
          .append(report.policy().vehicle().make()).append(" ")
          .append(report.policy().vehicle().model()).append("\n");
        sb.append("VIN: ").append(report.policy().vehicle().vin()).append("\n\n");

        sb.append("ENVIRONMENT & WEATHER\n");
        sb.append("=====================\n");
        sb.append("Location: ").append(report.environment().address()).append("\n");
        sb.append("Road Type: ").append(report.environment().roadType()).append("\n");
        if (report.environment().nearestIntersection() != null) {
            sb.append("Nearest Intersection: ").append(report.environment().nearestIntersection()).append("\n");
        }
        sb.append("Lighting: ").append(report.environment().daylightStatus()).append("\n");
        sb.append("\n");
        sb.append("Weather:\n");
        sb.append("  Conditions: ").append(report.environment().weather().conditions()).append("\n");
        sb.append("  Temperature: ").append(String.format("%.1f", report.environment().weather().temperatureF())).append("°F\n");
        sb.append("  Visibility: ").append(report.environment().weather().visibilityMiles()).append(" miles\n");
        sb.append("  Wind Speed: ").append(String.format("%.1f", report.environment().weather().windSpeedMph())).append(" mph\n");
        if (report.environment().weather().precipitation() != null) {
            sb.append("  Precipitation: ").append(report.environment().weather().precipitation()).append("\n");
        }
        if (report.environment().prior24HourWeather() != null) {
            sb.append("  Prior 24hr: ").append(report.environment().prior24HourWeather()).append("\n");
        }
        sb.append("\n");
        sb.append("Road Conditions:\n");
        sb.append("  Surface: ").append(report.environment().roadConditions().surfaceCondition()).append("\n");
        if (report.environment().roadConditions().surfaceAssessmentReason() != null) {
            sb.append("  Assessment: ").append(report.environment().roadConditions().surfaceAssessmentReason()).append("\n");
        }
        sb.append("  Lanes: ").append(report.environment().roadConditions().numberOfLanes()).append("\n");
        if (report.environment().roadConditions().constructionZone()) {
            sb.append("  ⚠ CONSTRUCTION ZONE\n");
        }
        sb.append("\n");

        sb.append("RECOMMENDED ACTIONS\n");
        sb.append("================\n");
        for (String action : report.recommendedActions()) {
            sb.append("- ").append(action).append("\n");
        }
        sb.append("\n");

        if (!report.alerts().isEmpty()) {
            sb.append("ALERTS\n");
            sb.append("================\n");
            for (String alert : report.alerts()) {
                sb.append("- ").append(alert).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Record for email confirmation result.
     */
    public record EmailConfirmation(
        boolean sent,
        String messageId,
        String status,
        String recipient
    ) {}
}
