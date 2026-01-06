package com.insurancemegacorp.crash.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.List;

/**
 * The complete First Notice of Loss report.
 * This is the final output of the FNOL multi-agent system,
 * aggregating results from all specialist agents.
 */
@JsonClassDescription("Complete First Notice of Loss claim report")
public record FNOLReport(
    @JsonPropertyDescription("Unique claim number")
    String claimNumber,

    @JsonPropertyDescription("Claim status: INITIATED, PENDING_REVIEW, ASSIGNED, IN_PROGRESS")
    String status,

    @JsonPropertyDescription("Original accident event data")
    AccidentEvent event,

    @JsonPropertyDescription("Impact analysis from telemetry")
    ImpactAnalysis impact,

    @JsonPropertyDescription("Environmental context at time of accident")
    EnvironmentContext environment,

    @JsonPropertyDescription("Policy and driver information")
    PolicyInfo policy,

    @JsonPropertyDescription("Nearby services for the driver")
    NearbyServices services,

    @JsonPropertyDescription("Status of communications with driver and adjuster")
    CommunicationsStatus communications,

    @JsonPropertyDescription("Timestamp when this report was generated")
    Instant generatedAt,

    @JsonPropertyDescription("Recommended next actions for the claim")
    List<String> recommendedActions,

    @JsonPropertyDescription("Any flags or alerts for claims review")
    List<String> alerts
) {
    /**
     * Builder for creating FNOL reports incrementally as agent results arrive.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String claimNumber;
        private String status = "INITIATED";
        private AccidentEvent event;
        private ImpactAnalysis impact;
        private EnvironmentContext environment;
        private PolicyInfo policy;
        private NearbyServices services;
        private CommunicationsStatus communications;
        private Instant generatedAt;
        private List<String> recommendedActions = List.of();
        private List<String> alerts = List.of();

        public Builder claimNumber(String claimNumber) {
            this.claimNumber = claimNumber;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder event(AccidentEvent event) {
            this.event = event;
            return this;
        }

        public Builder impact(ImpactAnalysis impact) {
            this.impact = impact;
            return this;
        }

        public Builder environment(EnvironmentContext environment) {
            this.environment = environment;
            return this;
        }

        public Builder policy(PolicyInfo policy) {
            this.policy = policy;
            return this;
        }

        public Builder services(NearbyServices services) {
            this.services = services;
            return this;
        }

        public Builder communications(CommunicationsStatus communications) {
            this.communications = communications;
            return this;
        }

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder recommendedActions(List<String> recommendedActions) {
            this.recommendedActions = recommendedActions;
            return this;
        }

        public Builder alerts(List<String> alerts) {
            this.alerts = alerts;
            return this;
        }

        public FNOLReport build() {
            if (generatedAt == null) {
                generatedAt = Instant.now();
            }
            if (claimNumber == null) {
                claimNumber = generateClaimNumber();
            }
            return new FNOLReport(
                claimNumber, status, event, impact, environment,
                policy, services, communications, generatedAt,
                recommendedActions, alerts
            );
        }

        private String generateClaimNumber() {
            return "CLM-" + java.time.Year.now().getValue() + "-" +
                   String.format("%06d", (int)(Math.random() * 999999));
        }
    }
}
