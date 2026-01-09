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
     * Final Action: Send FNOL email to adjuster AND customer follow-up email, then return the report.
     * This is the goal - achieves the workflow by sending both emails.
     */
    @AchievesGoal(description = "Generate complete First Notice of Loss report and email to adjuster and customer")
    @Action(
        description = "Email the compiled FNOL report to the claims adjuster and customer, then return the report",
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

        // 1. Send adjuster notification email
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
            log.info("FNOL email sent to adjuster for claim: {}", report.claimNumber());
        } catch (Exception e) {
            log.error("Failed to send adjuster FNOL email for claim {}: {}", report.claimNumber(), e.getMessage());
        }

        // 2. Send customer follow-up email with claim details and service recommendations
        try {
            log.info("Preparing customer follow-up email for claim: {}", report.claimNumber());

            String adjusterInfo = report.impact().severity() == ImpactAnalysis.Severity.SEVERE
                ? "Sarah Martinez (Senior Adjuster) - Priority: HIGH\nPhone: 1-800-555-2583\nEmail: sarah.martinez@insurancemegacorp.com"
                : "Mike Johnson (Adjuster) - Priority: MEDIUM\nPhone: 1-800-555-2584\nEmail: mike.johnson@insurancemegacorp.com";

            String bodyShopsInfo = buildServiceListForEmail(report.services().bodyShops(), "body shop");
            String towServicesInfo = buildServiceListForEmail(report.services().towServices(), "tow service");
            String rentalCarsInfo = buildServiceListForEmail(report.services().rentalCarLocations(), "rental");
            String medicalInfo = buildServiceListForEmail(report.services().medicalFacilities(), "medical");

            log.debug("Customer email services - bodyShops: {} chars, towServices: {} chars, rentals: {} chars, medical: {} chars",
                    bodyShopsInfo.length(), towServicesInfo.length(), rentalCarsInfo.length(), medicalInfo.length());

            String nextSteps = buildCustomerNextSteps(report);

            log.info("Calling sendCustomerFollowupEmail tool for customer: {}", report.policy().driver().name());
            ai.withAutoLlm().createObject(
                """
                Call the sendCustomerFollowupEmail tool to send a follow-up email to the customer.

                Use these parameters:
                - claimReference: "%s"
                - customerName: "%s"
                - policyNumber: "%s"
                - severity: "%s"
                - adjusterInfo: "%s"
                - bodyShopsInfo: "%s"
                - towServicesInfo: "%s"
                - rentalCarsInfo: "%s"
                - medicalInfo: "%s"
                - nextSteps: "%s"

                This will email the customer with their claim details and recommended services.
                Return confirmation of the email.
                """.formatted(
                    report.claimNumber(),
                    report.policy().driver().name(),
                    report.policy().policy().policyNumber(),
                    report.impact().severity().name(),
                    adjusterInfo.replace("\"", "\\\"").replace("\n", "\\n"),
                    bodyShopsInfo.replace("\"", "\\\"").replace("\n", "\\n"),
                    towServicesInfo.replace("\"", "\\\"").replace("\n", "\\n"),
                    rentalCarsInfo.replace("\"", "\\\"").replace("\n", "\\n"),
                    medicalInfo.replace("\"", "\\\"").replace("\n", "\\n"),
                    nextSteps.replace("\"", "\\\"").replace("\n", "\\n")
                ),
                EmailConfirmation.class
            );
            log.info("Customer follow-up email sent successfully for claim: {}", report.claimNumber());
        } catch (Exception e) {
            log.error("Failed to send customer follow-up email for claim {}: {}", report.claimNumber(), e.getMessage(), e);
        }

        // Return the original report
        return report;
    }

    private String buildServiceListForEmail(List<NearbyServices.ServiceLocation> services, String type) {
        if (services == null || services.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var svc : services) {
            sb.append("- ").append(svc.name());
            if (type.equals("tow service") && svc.etaMinutes() != null) {
                sb.append(" (ETA: ").append(svc.etaMinutes()).append(" min)");
            } else {
                sb.append(" (").append(String.format("%.1f", svc.distanceMiles())).append(" mi)");
            }
            // Always show phone line
            sb.append("\n  Phone: ");
            if (svc.phone() != null && !svc.phone().isBlank()) {
                sb.append(svc.phone());
            } else {
                sb.append("Call for info");
            }
            if (svc.address() != null && !svc.address().isBlank()) {
                sb.append("\n  Address: ").append(svc.address());
            }
            if (svc.isPreferred()) {
                sb.append("\n  [PREFERRED PROVIDER]");
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private String buildCustomerNextSteps(FNOLReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("1. Your adjuster will contact you within 24 hours to discuss your claim.\n\n");

        if (report.services().vehicleLikelyDrivable()) {
            sb.append("2. Your vehicle appears drivable. Please schedule an inspection at one of our recommended body shops at your convenience.\n\n");
        } else {
            sb.append("2. A tow truck has been dispatched. Please wait at a safe location.\n\n");
            if (report.policy().policy().hasRentalCoverage()) {
                sb.append("3. Your policy includes rental car coverage. See the rental locations above for options.\n\n");
            }
        }

        sb.append("If you need immediate assistance, call our 24/7 claims hotline: 1-800-555-CLAIM\n\n");

        if (report.impact().airbagLikely()) {
            sb.append("IMPORTANT: If you are experiencing any pain or discomfort, please seek medical attention immediately. Medical facilities are listed above.");
        }

        return sb.toString();
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

        sb.append("POLICY INFORMATION\n");
        sb.append("==================\n");
        sb.append("Policy Number: ").append(report.policy().policy().policyNumber()).append("\n");
        sb.append("Status: ").append(report.policy().policy().status()).append("\n");
        sb.append("Deductible: $").append(report.policy().policy().deductible()).append("\n");
        sb.append("Coverage Types: ").append(String.join(", ", report.policy().policy().coverageTypes())).append("\n");
        sb.append("Roadside Assistance: ").append(report.policy().policy().hasRoadsideAssistance() ? "Yes" : "No").append("\n");
        sb.append("Rental Coverage: ").append(report.policy().policy().hasRentalCoverage() ? "Yes" : "No").append("\n\n");

        sb.append("DRIVER INFORMATION\n");
        sb.append("==================\n");
        sb.append("Name: ").append(report.policy().driver().name()).append("\n");
        sb.append("Phone: ").append(report.policy().driver().phone()).append("\n");
        sb.append("Email: ").append(report.policy().driver().email()).append("\n");
        sb.append("Risk Score: ").append(report.policy().driver().riskScore()).append("/100\n");
        sb.append("Emergency Contact: ").append(report.policy().driver().emergencyContactName())
          .append(" (").append(report.policy().driver().emergencyContactPhone()).append(")\n\n");

        sb.append("VEHICLE INFORMATION\n");
        sb.append("===================\n");
        sb.append("Vehicle: ").append(report.policy().vehicle().year()).append(" ")
          .append(report.policy().vehicle().make()).append(" ")
          .append(report.policy().vehicle().model()).append("\n");
        sb.append("Color: ").append(report.policy().vehicle().color()).append("\n");
        sb.append("VIN: ").append(report.policy().vehicle().vin()).append("\n");
        sb.append("License Plate: ").append(report.policy().vehicle().licensePlate()).append("\n");
        sb.append("Estimated Value: $").append(String.format("%,d", report.policy().vehicle().estimatedValue())).append("\n\n");

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

        // Nearby Services section
        sb.append("NEARBY SERVICES\n");
        sb.append("===============\n");
        sb.append("Vehicle Drivable: ").append(report.services().vehicleLikelyDrivable() ? "Yes" : "No").append("\n");
        sb.append("Dispatch Recommendation: ").append(report.services().dispatchRecommendation()).append("\n\n");

        if (!report.services().bodyShops().isEmpty()) {
            sb.append("Body Shops:\n");
            for (var shop : report.services().bodyShops()) {
                sb.append("  - ").append(shop.name());
                sb.append(" (").append(String.format("%.1f", shop.distanceMiles())).append(" mi)");
                sb.append(" - ").append(shop.phone() != null && !shop.phone().isBlank() ? shop.phone() : "Call for info");
                if (shop.isPreferred()) {
                    sb.append(" [PREFERRED]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!report.services().towServices().isEmpty()) {
            sb.append("Tow Services:\n");
            for (var tow : report.services().towServices()) {
                sb.append("  - ").append(tow.name());
                if (tow.etaMinutes() != null) {
                    sb.append(" (ETA: ").append(tow.etaMinutes()).append(" min)");
                }
                sb.append(" - ").append(tow.phone() != null && !tow.phone().isBlank() ? tow.phone() : "Call for info");
                if (tow.isPreferred()) {
                    sb.append(" [PREFERRED]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!report.services().medicalFacilities().isEmpty()) {
            sb.append("Medical Facilities:\n");
            for (var med : report.services().medicalFacilities()) {
                sb.append("  - ").append(med.name());
                sb.append(" (").append(String.format("%.1f", med.distanceMiles())).append(" mi)");
                sb.append(" - ").append(med.phone() != null && !med.phone().isBlank() ? med.phone() : "Call for info");
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!report.services().rentalCarLocations().isEmpty()) {
            sb.append("Rental Cars:\n");
            for (var rental : report.services().rentalCarLocations()) {
                sb.append("  - ").append(rental.name());
                sb.append(" (").append(String.format("%.1f", rental.distanceMiles())).append(" mi)");
                sb.append(" - ").append(rental.phone() != null && !rental.phone().isBlank() ? rental.phone() : "Call for info");
                if (rental.isPreferred()) {
                    sb.append(" [PREFERRED]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

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
