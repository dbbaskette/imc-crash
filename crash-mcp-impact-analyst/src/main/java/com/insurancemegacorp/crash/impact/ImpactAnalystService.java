package com.insurancemegacorp.crash.impact;

import com.insurancemegacorp.crash.domain.ImpactAnalysis;
import com.insurancemegacorp.crash.domain.ImpactAnalysis.ImpactType;
import com.insurancemegacorp.crash.domain.ImpactAnalysis.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * MCP Tools for analyzing accident impact from telemetry data.
 * Exposes tools that can be called by the FNOL orchestrator.
 * Uses Google Gemini LLM for professional incident narrative generation.
 */
@Service
public class ImpactAnalystService {

    private static final Logger log = LoggerFactory.getLogger(ImpactAnalystService.class);

    @Value("${fnol.impact.thresholds.severe-g-force:5.0}")
    private double severeGForceThreshold;

    @Value("${fnol.impact.thresholds.moderate-g-force:3.0}")
    private double moderateGForceThreshold;

    @Value("${fnol.impact.thresholds.severe-speed-delta:45}")
    private double severeSpeedDelta;

    @Value("${fnol.impact.thresholds.moderate-speed-delta:25}")
    private double moderateSpeedDelta;

    @Autowired(required = false)
    private ChatModel chatModel;

    /**
     * Analyzes accident telemetry to determine severity and impact type.
     * Uses LLM to generate professional incident narrative.
     */
    @McpTool(description = "Analyze accident telemetry data to classify impact severity and type. " +
                        "Returns a complete impact analysis including severity level, impact type, " +
                        "whether airbags likely deployed, and an AI-generated professional narrative.")
    public ImpactAnalysis analyzeImpact(
            @McpToolParam(description = "G-force measurement from accelerometer")
            double gForce,

            @McpToolParam(description = "Vehicle speed in mph at time of event")
            double speedMph,

            @McpToolParam(description = "Posted speed limit in mph")
            int speedLimitMph,

            @McpToolParam(description = "Accelerometer X-axis reading (longitudinal/front-back: negative=deceleration, positive=pushed forward)")
            double accelerometerX,

            @McpToolParam(description = "Accelerometer Y-axis reading (lateral/left-right: side impacts)")
            double accelerometerY,

            @McpToolParam(description = "Accelerometer Z-axis reading (vertical/up-down: rollovers)")
            double accelerometerZ
    ) {
        log.info("Analyzing impact: gForce={}, speed={}, accel=[{},{},{}]",
                gForce, speedMph, accelerometerX, accelerometerY, accelerometerZ);

        // Classify severity using rule-based logic (fast, deterministic)
        Severity severity = classifySeverity(gForce, speedMph);

        // Determine impact type from accelerometer pattern
        ImpactType impactType = determineImpactType(accelerometerX, accelerometerY, accelerometerZ);

        // Check if speeding
        boolean wasSpeeding = speedMph > speedLimitMph;

        // Airbag deployment likely for severe impacts
        boolean airbagLikely = gForce > 4.0 || severity == Severity.SEVERE;

        // Calculate confidence based on data quality
        double confidence = calculateConfidence(gForce, accelerometerX, accelerometerY, accelerometerZ);

        // Generate narrative using LLM if available, otherwise fall back to rule-based
        String narrative = generateNarrative(severity, impactType, gForce, speedMph,
                                              speedLimitMph, wasSpeeding, airbagLikely,
                                              accelerometerX, accelerometerY, accelerometerZ);

        log.info("Impact analysis complete: severity={}, type={}, confidence={}",
                severity, impactType, confidence);

        return new ImpactAnalysis(
            severity,
            impactType,
            speedMph,  // Estimated speed at impact
            wasSpeeding,
            airbagLikely,
            confidence,
            narrative
        );
    }

    /**
     * Quick severity check without full analysis.
     */
    @McpTool(description = "Quick check to determine if an event meets the threshold for accident detection")
    public boolean isAccidentDetected(
            @McpToolParam(description = "G-force measurement from accelerometer")
            double gForce,

            @McpToolParam(description = "Threshold g-force value for accident detection (default 2.5)")
            Double threshold
    ) {
        double effectiveThreshold = threshold != null ? threshold : 2.5;
        return gForce >= effectiveThreshold;
    }

    private Severity classifySeverity(double gForce, double speedMph) {
        if (gForce >= severeGForceThreshold || speedMph >= severeSpeedDelta) {
            return Severity.SEVERE;
        } else if (gForce >= moderateGForceThreshold || speedMph >= moderateSpeedDelta) {
            return Severity.MODERATE;
        } else {
            return Severity.MINOR;
        }
    }

    /**
     * Enhanced impact type detection based on 9 accident type sensor profiles.
     *
     * Accident types from telemetry generator:
     * - ROLLOVER: Extreme Z-axis (-8 to 8 g), chaotic rotation
     * - HEAD_ON: Extreme negative X (-8 to -12 g)
     * - T_BONE: Strong lateral Y (5 to 9 g)
     * - SINGLE_VEHICLE: Strong negative X (-5 to -9 g)
     * - REAR_END_COLLISION: Negative X (-4 to -8 g)
     * - MULTI_VEHICLE_PILEUP: Moderate negative X, varied axes
     * - REAR_ENDED: Positive X (2 to 6 g) - pushed forward
     * - SIDE_SWIPE: Moderate lateral Y (2 to 4 g)
     * - HIT_AND_RUN: Varied, moderate forces
     */
    private ImpactType determineImpactType(double x, double y, double z) {
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);

        // ROLLOVER: Extreme Z-axis or very high Z relative to others
        if (absZ > 6.0 || (absZ > 4.0 && absZ > absX * 1.5 && absZ > absY * 1.5)) {
            return ImpactType.ROLLOVER;
        }

        // T_BONE: Strong lateral Y-axis impact (side impact, perpendicular)
        if (absY > 4.5 && absY > absX * 1.5) {
            return ImpactType.SIDE;
        }

        // HEAD_ON: Extreme frontal deceleration
        if (x < -7.0) {
            return ImpactType.FRONTAL;
        }

        // SINGLE_VEHICLE / REAR_END_COLLISION: Strong frontal impact
        if (x < -3.5 && absX > absY) {
            return ImpactType.FRONTAL;
        }

        // REAR_ENDED: Positive X (vehicle pushed forward)
        if (x > 1.5 && absX > absY) {
            return ImpactType.REAR;
        }

        // SIDE_SWIPE: Moderate lateral impact
        if (absY > 1.5 && absY > absX) {
            return ImpactType.SIDE;
        }

        // Default to dominant axis
        if (absX > absY && absX > absZ) {
            return x < 0 ? ImpactType.FRONTAL : ImpactType.REAR;
        }
        if (absY > absX && absY > absZ) {
            return ImpactType.SIDE;
        }

        return ImpactType.UNKNOWN;
    }

    private double calculateConfidence(double gForce, double x, double y, double z) {
        double gForceConfidence = Math.min(gForce / 5.0, 1.0);
        double totalAccel = Math.sqrt(x*x + y*y + z*z);
        double accelConfidence = Math.min(totalAccel / 4.0, 1.0);
        return Math.round((gForceConfidence + accelConfidence) / 2.0 * 100) / 100.0;
    }

    /**
     * Generate incident narrative using LLM if available, otherwise use rule-based fallback.
     */
    private String generateNarrative(Severity severity, ImpactType impactType,
                                      double gForce, double speedMph, int speedLimitMph,
                                      boolean wasSpeeding, boolean airbagLikely,
                                      double accelX, double accelY, double accelZ) {
        if (chatModel != null) {
            try {
                return generateNarrativeWithLlm(severity, impactType, gForce, speedMph,
                        speedLimitMph, wasSpeeding, airbagLikely, accelX, accelY, accelZ);
            } catch (Exception e) {
                log.warn("LLM narrative generation failed, using fallback: {}", e.getMessage());
            }
        }
        return generateNarrativeFallback(severity, impactType, gForce, speedMph,
                speedLimitMph, wasSpeeding, airbagLikely);
    }

    /**
     * Generate professional incident narrative using Google Gemini LLM.
     */
    private String generateNarrativeWithLlm(Severity severity, ImpactType impactType,
                                             double gForce, double speedMph, int speedLimitMph,
                                             boolean wasSpeeding, boolean airbagLikely,
                                             double accelX, double accelY, double accelZ) {
        String prompt = String.format("""
            You are a senior insurance claims analyst writing a professional incident summary
            for a First Notice of Loss (FNOL) report. Based on the telemetry data, write a
            clear, factual, 3-4 sentence narrative that:

            1. Describes the collision physics in professional insurance terminology
            2. Explains what the sensor data indicates about the crash dynamics
            3. Notes any safety concerns or risk factors
            4. Uses objective language suitable for legal/regulatory documentation

            TELEMETRY DATA:
            - G-Force at impact: %.1f g
            - Vehicle speed: %.0f mph (Speed limit: %d mph)
            - Speeding: %s
            - Accelerometer X (longitudinal): %.2f g (negative=forward deceleration, positive=pushed forward)
            - Accelerometer Y (lateral): %.2f g (side-to-side forces)
            - Accelerometer Z (vertical): %.2f g (vertical forces, rollover indicator)

            CLASSIFICATION (pre-determined by rule engine):
            - Severity: %s
            - Impact Type: %s
            - Airbag Deployment Likely: %s

            PHYSICS INTERPRETATION GUIDE:
            - X < -7g: Head-on collision or high-speed frontal impact
            - X between -4g and -7g: Moderate frontal impact or rear-end collision (vehicle hitting something)
            - X > +2g: Vehicle was struck from behind (pushed forward)
            - |Y| > 4.5g: T-bone or severe side impact
            - |Y| between 1.5g and 4.5g: Side-swipe or glancing side impact
            - |Z| > 4g: Rollover event or vehicle became airborne

            Write ONLY the narrative paragraph. Do not include headers, bullet points, or metadata.
            Keep the tone professional and factual, similar to an accident reconstruction report.
            """,
            gForce, speedMph, speedLimitMph,
            wasSpeeding ? "Yes" : "No",
            accelX, accelY, accelZ,
            severity.name(), impactType.name(),
            airbagLikely ? "Yes" : "No"
        );

        log.info("Generating LLM narrative for {} {} impact", severity, impactType);
        String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();

        // Clean up any extra whitespace or formatting
        String narrative = response.trim();
        log.debug("LLM narrative generated: {}", narrative.substring(0, Math.min(100, narrative.length())));

        return narrative;
    }

    /**
     * Fallback rule-based narrative generation when LLM is unavailable.
     */
    private String generateNarrativeFallback(Severity severity, ImpactType impactType,
                                              double gForce, double speedMph, int speedLimitMph,
                                              boolean wasSpeeding, boolean airbagLikely) {
        StringBuilder narrative = new StringBuilder();

        narrative.append("Vehicle experienced ");
        narrative.append(severity.name().toLowerCase());
        narrative.append(" ");
        narrative.append(impactType.name().toLowerCase().replace("_", " "));
        narrative.append(" impact. ");

        narrative.append(String.format("G-force of %.1f detected", gForce));

        if (severity == Severity.SEVERE) {
            narrative.append(", indicating significant collision forces. ");
        } else if (severity == Severity.MODERATE) {
            narrative.append(", suggesting moderate collision. ");
        } else {
            narrative.append(", consistent with minor impact. ");
        }

        narrative.append(String.format("Speed at event: %.0f mph (limit: %d mph). ",
                                       speedMph, speedLimitMph));

        if (wasSpeeding) {
            narrative.append("Vehicle was exceeding posted speed limit. ");
        } else {
            narrative.append("Vehicle was within posted speed limit. ");
        }

        if (airbagLikely) {
            narrative.append("Airbag deployment is likely based on impact force.");
        }

        return narrative.toString();
    }
}
