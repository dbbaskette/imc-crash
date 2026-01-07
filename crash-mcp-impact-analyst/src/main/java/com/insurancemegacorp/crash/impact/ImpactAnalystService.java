package com.insurancemegacorp.crash.impact;

import com.insurancemegacorp.crash.domain.ImpactAnalysis;
import com.insurancemegacorp.crash.domain.ImpactAnalysis.ImpactType;
import com.insurancemegacorp.crash.domain.ImpactAnalysis.Severity;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * MCP Tools for analyzing accident impact from telemetry data.
 * Exposes tools that can be called by the FNOL orchestrator.
 */
@Service
public class ImpactAnalystService {

    @Value("${fnol.impact.thresholds.severe-g-force:5.0}")
    private double severeGForceThreshold;

    @Value("${fnol.impact.thresholds.moderate-g-force:3.0}")
    private double moderateGForceThreshold;

    @Value("${fnol.impact.thresholds.severe-speed-delta:45}")
    private double severeSpeedDelta;

    @Value("${fnol.impact.thresholds.moderate-speed-delta:25}")
    private double moderateSpeedDelta;

    /**
     * Analyzes accident telemetry to determine severity and impact type.
     */
    @Tool(description = "Analyze accident telemetry data to classify impact severity and type. " +
                        "Returns a complete impact analysis including severity level, impact type, " +
                        "whether airbags likely deployed, and a narrative description.")
    public ImpactAnalysis analyzeImpact(
            @ToolParam(description = "G-force measurement from accelerometer") 
            double gForce,
            
            @ToolParam(description = "Vehicle speed in mph at time of event") 
            double speedMph,
            
            @ToolParam(description = "Posted speed limit in mph") 
            int speedLimitMph,
            
            @ToolParam(description = "Accelerometer X-axis reading (lateral)") 
            double accelerometerX,
            
            @ToolParam(description = "Accelerometer Y-axis reading (longitudinal)") 
            double accelerometerY,
            
            @ToolParam(description = "Accelerometer Z-axis reading (vertical)") 
            double accelerometerZ
    ) {
        // Classify severity
        Severity severity = classifySeverity(gForce, speedMph);
        
        // Determine impact type from accelerometer pattern
        ImpactType impactType = determineImpactType(accelerometerX, accelerometerY, accelerometerZ);
        
        // Check if speeding
        boolean wasSpeeding = speedMph > speedLimitMph;
        
        // Airbag deployment likely for severe impacts
        boolean airbagLikely = gForce > 4.0 || severity == Severity.SEVERE;
        
        // Calculate confidence based on data quality
        double confidence = calculateConfidence(gForce, accelerometerX, accelerometerY, accelerometerZ);
        
        // Generate narrative
        String narrative = generateNarrative(severity, impactType, gForce, speedMph, 
                                              speedLimitMph, wasSpeeding, airbagLikely);
        
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
    @Tool(description = "Quick check to determine if an event meets the threshold for accident detection")
    public boolean isAccidentDetected(
            @ToolParam(description = "G-force measurement from accelerometer") 
            double gForce,
            
            @ToolParam(description = "Threshold g-force value for accident detection (default 2.5)") 
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
        // Profile: accelZ range -8 to 8, dominant axis
        if (absZ > 6.0 || (absZ > 4.0 && absZ > absX * 1.5 && absZ > absY * 1.5)) {
            return ImpactType.ROLLOVER;
        }

        // T_BONE: Strong lateral Y-axis impact (side impact, perpendicular)
        // Profile: accelY range 5 to 9, dominant axis
        if (absY > 4.5 && absY > absX * 1.5) {
            return ImpactType.SIDE;  // Severe side impact (T-bone)
        }

        // HEAD_ON: Extreme frontal deceleration
        // Profile: accelX range -8 to -12 (most extreme)
        if (x < -7.0) {
            return ImpactType.FRONTAL;  // Head-on collision
        }

        // SINGLE_VEHICLE / REAR_END_COLLISION: Strong frontal impact
        // Profile: accelX range -5 to -9 (single vehicle) or -4 to -8 (rear-end)
        if (x < -3.5 && absX > absY) {
            return ImpactType.FRONTAL;  // Includes rear-end collision, single vehicle
        }

        // REAR_ENDED: Positive X (vehicle pushed forward)
        // Profile: accelX range 2 to 6 (positive = forward jolt)
        if (x > 1.5 && absX > absY) {
            return ImpactType.REAR;  // Struck from behind
        }

        // SIDE_SWIPE: Moderate lateral impact
        // Profile: accelY range 2 to 4 (less than T-bone)
        if (absY > 1.5 && absY > absX) {
            return ImpactType.SIDE;  // Side-swipe or glancing side impact
        }

        // MULTI_VEHICLE_PILEUP / HIT_AND_RUN: Varied forces, less clear pattern
        // Default to dominant axis if we have clear readings
        if (absX > absY && absX > absZ) {
            return x < 0 ? ImpactType.FRONTAL : ImpactType.REAR;
        }
        if (absY > absX && absY > absZ) {
            return ImpactType.SIDE;
        }

        return ImpactType.UNKNOWN;
    }

    private double calculateConfidence(double gForce, double x, double y, double z) {
        // Higher g-force readings give higher confidence
        double gForceConfidence = Math.min(gForce / 5.0, 1.0);
        
        // Consistent accelerometer readings increase confidence
        double totalAccel = Math.sqrt(x*x + y*y + z*z);
        double accelConfidence = Math.min(totalAccel / 4.0, 1.0);
        
        // Average the two metrics
        return Math.round((gForceConfidence + accelConfidence) / 2.0 * 100) / 100.0;
    }

    private String generateNarrative(Severity severity, ImpactType impactType, 
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
