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

    private ImpactType determineImpactType(double x, double y, double z) {
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);
        
        // Check for rollover (high Z-axis)
        if (absZ > 2.0 && absZ > absX && absZ > absY) {
            return ImpactType.ROLLOVER;
        }
        
        // Frontal or rear impact (high Y-axis - longitudinal)
        if (absY > absX) {
            return y < 0 ? ImpactType.FRONTAL : ImpactType.REAR;
        }
        
        // Side impact (high X-axis - lateral)
        if (absX > absY) {
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
