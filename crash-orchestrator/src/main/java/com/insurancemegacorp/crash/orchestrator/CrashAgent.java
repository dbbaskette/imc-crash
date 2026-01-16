package com.insurancemegacorp.crash.orchestrator;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.insurancemegacorp.crash.domain.*;
import com.insurancemegacorp.crash.orchestrator.service.AccidentStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final AccidentStatusService statusService;
    private final StatsService statsService;

    // Virtual thread executor for parallel execution (Java 21+)
    private final ExecutorService parallelExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CrashAgent(AccidentStatusService statusService, StatsService statsService) {
        this.statusService = statusService;
        this.statsService = statsService;
    }

    /**
     * Generate a claim reference number from the policy ID.
     */
    private String generateClaimReference(long policyId) {
        return "CLM-" + Year.now().getValue() + "-" + policyId;
    }

    /**
     * Format phone number for display, returning default text if unavailable.
     */
    private String formatPhone(String phone) {
        return (phone != null && !phone.isBlank()) ? phone : "Call for info";
    }

    /**
     * PARALLEL Action: Gather all initial data (Impact, Environment, Policy) concurrently.
     * This single action replaces the three sequential Level 0 actions with parallel execution.
     * Uses Java 21 virtual threads to run all three MCP calls simultaneously.
     */
    @Action(
        description = "Gather impact analysis, environment context, and policy info in parallel",
        toolGroups = {"impact-analyst-tools", "environment-tools", "policy-tools"}
    )
    public InitialData gatherInitialData(AccidentEvent event, Ai ai) {
        log.info(">>> PARALLEL EXECUTION: Starting 3 concurrent actions for policyId={}", event.policyId());
        long startTime = System.currentTimeMillis();

        // Launch all three actions in parallel using CompletableFuture
        CompletableFuture<ImpactAnalysis> impactFuture = CompletableFuture.supplyAsync(() -> {
            log.info("[PARALLEL] Starting analyzeImpact");
            statusService.broadcastAgentStatus("impact-analyst", "analyzeImpact", "STARTED");
            try {
                ImpactAnalysis result = ai.withAutoLlm().createObject(
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
                statusService.broadcastAgentStatus("impact-analyst", "analyzeImpact", "COMPLETED");
                log.info("[PARALLEL] Completed analyzeImpact: severity={}", result.severity());
                return result;
            } catch (Exception e) {
                statusService.broadcastAgentStatus("impact-analyst", "analyzeImpact", "FAILED");
                throw new RuntimeException("Impact analysis failed", e);
            }
        }, parallelExecutor);

        CompletableFuture<EnvironmentContext> envFuture = CompletableFuture.supplyAsync(() -> {
            log.info("[PARALLEL] Starting gatherEnvironment");
            statusService.broadcastAgentStatus("environment", "gatherEnvironment", "STARTED");
            try {
                EnvironmentContext result = ai.withAutoLlm().createObject(
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
                statusService.broadcastAgentStatus("environment", "gatherEnvironment", "COMPLETED");
                log.info("[PARALLEL] Completed gatherEnvironment: address={}", result.address());
                return result;
            } catch (Exception e) {
                statusService.broadcastAgentStatus("environment", "gatherEnvironment", "FAILED");
                throw new RuntimeException("Environment gathering failed", e);
            }
        }, parallelExecutor);

        CompletableFuture<PolicyInfo> policyFuture = CompletableFuture.supplyAsync(() -> {
            log.info("[PARALLEL] Starting lookupPolicy");
            statusService.broadcastAgentStatus("policy", "lookupPolicy", "STARTED");
            try {
                PolicyInfo result = ai.withAutoLlm().createObject(
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
                statusService.broadcastAgentStatus("policy", "lookupPolicy", "COMPLETED");

                // Broadcast customer detected event for UI sidebar
                String claimReference = generateClaimReference(event.policyId());
                statusService.broadcastCustomerDetected(
                    claimReference,
                    event.policyId(),
                    result.driver().name(),
                    result.driver().phone(),
                    result.driver().email()
                );
                log.info("[PARALLEL] Completed lookupPolicy: driver={}", result.driver().name());
                return result;
            } catch (Exception e) {
                statusService.broadcastAgentStatus("policy", "lookupPolicy", "FAILED");
                throw new RuntimeException("Policy lookup failed", e);
            }
        }, parallelExecutor);

        // Wait for all three to complete
        try {
            CompletableFuture.allOf(impactFuture, envFuture, policyFuture).join();

            ImpactAnalysis impact = impactFuture.get();
            EnvironmentContext environment = envFuture.get();
            PolicyInfo policy = policyFuture.get();

            long duration = System.currentTimeMillis() - startTime;
            log.info("<<< PARALLEL EXECUTION COMPLETE: 3 actions finished in {} ms", duration);

            return new InitialData(impact, environment, policy);
        } catch (Exception e) {
            log.error("Parallel execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to gather initial data in parallel", e);
        }
    }

    /**
     * PARALLEL Action: Find services AND initiate communications concurrently.
     * Both depend only on InitialData, so they can run in parallel.
     * Uses Java 21 virtual threads to run both MCP calls simultaneously.
     */
    @Action(
        description = "Find nearby services and initiate communications in parallel",
        toolGroups = {"services-tools", "communications-tools"}
    )
    public SecondaryData gatherSecondaryData(AccidentEvent event, InitialData initialData, Ai ai) {
        log.info(">>> PARALLEL EXECUTION: Starting 2 concurrent Level-1 actions for policyId={}", event.policyId());
        long startTime = System.currentTimeMillis();

        // Launch findServices in parallel
        CompletableFuture<NearbyServices> servicesFuture = CompletableFuture.supplyAsync(() -> {
            log.info("[PARALLEL-L1] Starting findServices");
            statusService.broadcastAgentStatus("services", "findServices", "STARTED");
            try {
                NearbyServices result = ai.withAutoLlm().createObject(
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
                        initialData.impact().severity().name()
                    ),
                    NearbyServices.class
                );
                statusService.broadcastAgentStatus("services", "findServices", "COMPLETED");
                log.info("[PARALLEL-L1] Completed findServices: drivable={}", result.vehicleLikelyDrivable());
                return result;
            } catch (Exception e) {
                statusService.broadcastAgentStatus("services", "findServices", "FAILED");
                throw new RuntimeException("Find services failed", e);
            }
        }, parallelExecutor);

        // Launch initiateComms in parallel
        CompletableFuture<CommunicationsStatus> commsFuture = CompletableFuture.supplyAsync(() -> {
            String claimReference = generateClaimReference(event.policyId());
            log.info("[PARALLEL-L1] Starting initiateComms for claim={}", claimReference);
            statusService.broadcastAgentStatus("communications", "initiateComms", "STARTED");
            try {
                CommunicationsStatus result = ai.withAutoLlm().createObject(
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
                        initialData.policy().driver().name(),
                        initialData.policy().driver().phone(),
                        initialData.impact().severity().name()
                    ),
                    CommunicationsStatus.class
                );
                statusService.broadcastAgentStatus("communications", "initiateComms", "COMPLETED");
                log.info("[PARALLEL-L1] Completed initiateComms: smsSent={}", result.driverOutreach().smsSent());
                return result;
            } catch (Exception e) {
                statusService.broadcastAgentStatus("communications", "initiateComms", "FAILED");
                throw new RuntimeException("Initiate communications failed", e);
            }
        }, parallelExecutor);

        // Wait for both to complete
        try {
            CompletableFuture.allOf(servicesFuture, commsFuture).join();

            NearbyServices services = servicesFuture.get();
            CommunicationsStatus communications = commsFuture.get();

            // Track SMS sent
            if (communications.driverOutreach() != null && communications.driverOutreach().smsSent()) {
                statsService.incrementSmsSent();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("<<< PARALLEL EXECUTION COMPLETE: 2 Level-1 actions finished in {} ms", duration);

            return new SecondaryData(services, communications);
        } catch (Exception e) {
            log.error("Parallel Level-1 execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to gather secondary data in parallel", e);
        }
    }

    /**
     * Action 3: Compile the FNOL report (not final - email follows).
     * Takes InitialData + SecondaryData (services and communications).
     */
    @Action(description = "Compile all agent results into FNOL report")
    public FNOLReport compileReport(
            AccidentEvent event,
            InitialData initialData,
            SecondaryData secondaryData
    ) {
        NearbyServices services = secondaryData.services();
        CommunicationsStatus communications = secondaryData.communications();
        ImpactAnalysis impact = initialData.impact();
        EnvironmentContext environment = initialData.environment();
        PolicyInfo policy = initialData.policy();

        log.info("Compiling FNOL report for policyId={}, severity={}",
                event.policyId(), impact.severity());
        statusService.broadcastAgentStatus("orchestrator", "compileReport", "STARTED");

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

        String claimNumber = generateClaimReference(event.policyId());

        log.info("FNOL report compiled: claimNumber={}, alerts={}", claimNumber, alerts.size());
        statusService.broadcastAgentStatus("orchestrator", "compileReport", "COMPLETED");

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
        statusService.broadcastAgentStatus("communications", "sendFnolToAdjuster", "STARTED");

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
            statsService.incrementEmailsSent();
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
                - customerEmail: "%s"
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
                    report.policy().driver().email(),
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
            statsService.incrementEmailsSent();
            log.info("Customer follow-up email sent successfully for claim: {}", report.claimNumber());
        } catch (Exception e) {
            log.error("Failed to send customer follow-up email for claim {}: {}", report.claimNumber(), e.getMessage(), e);
        }

        statusService.broadcastAgentStatus("communications", "sendFnolToAdjuster", "COMPLETED");

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
            sb.append("\n  Phone: ").append(formatPhone(svc.phone()));
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
                sb.append(" - ").append(formatPhone(shop.phone()));
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
                sb.append(" - ").append(formatPhone(tow.phone()));
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
                sb.append(" - ").append(formatPhone(med.phone()));
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!report.services().rentalCarLocations().isEmpty()) {
            sb.append("Rental Cars:\n");
            for (var rental : report.services().rentalCarLocations()) {
                sb.append("  - ").append(rental.name());
                sb.append(" (").append(String.format("%.1f", rental.distanceMiles())).append(" mi)");
                sb.append(" - ").append(formatPhone(rental.phone()));
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
