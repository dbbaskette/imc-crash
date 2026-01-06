package com.insurancemegacorp.crash.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Result of impact analysis performed by the Impact Analyst agent.
 * Classifies the accident severity and type based on telemetry data.
 */
@JsonClassDescription("Analysis of accident impact based on telemetry data")
public record ImpactAnalysis(
    @JsonPropertyDescription("Severity classification: MINOR, MODERATE, or SEVERE")
    Severity severity,

    @JsonPropertyDescription("Type of impact: FRONTAL, REAR, SIDE, ROLLOVER, or UNKNOWN")
    ImpactType impactType,

    @JsonPropertyDescription("Estimated speed at moment of impact in mph")
    double estimatedSpeedAtImpact,

    @JsonPropertyDescription("Whether vehicle was speeding at time of incident")
    boolean wasSpeeding,

    @JsonPropertyDescription("Whether airbag deployment is likely based on impact force")
    boolean airbagLikely,

    @JsonPropertyDescription("Confidence score of this analysis (0.0 to 1.0)")
    double confidence,

    @JsonPropertyDescription("Human-readable narrative describing the incident")
    String narrative
) {
    public enum Severity {
        MINOR,      // Fender bender, cosmetic damage
        MODERATE,   // Significant damage, possible injuries
        SEVERE      // Major collision, likely injuries, airbag deployment
    }

    public enum ImpactType {
        FRONTAL,
        REAR,
        SIDE,
        ROLLOVER,
        UNKNOWN
    }
}
