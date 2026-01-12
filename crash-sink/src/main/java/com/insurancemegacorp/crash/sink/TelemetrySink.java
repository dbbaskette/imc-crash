package com.insurancemegacorp.crash.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemegacorp.crash.domain.AccidentEvent;
import com.insurancemegacorp.crash.domain.FNOLReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Spring Cloud Stream sink that consumes vehicle telemetry events from RabbitMQ,
 * filters for accidents, and forwards them to the orchestrator.
 *
 * This is a lightweight, horizontally-scalable component that only handles:
 * 1. Message deserialization
 * 2. G-force threshold filtering
 * 3. HTTP forwarding to orchestrator
 *
 * All heavy processing (GOAP, MCP tools, persistence) happens in the orchestrator.
 */
@Component
public class TelemetrySink {
    private static final Logger log = LoggerFactory.getLogger(TelemetrySink.class);
    private static final double G_FORCE_THRESHOLD = 2.5;

    private final ObjectMapper mapper;
    private final TelemetryToAccidentMapper telemetryMapper;
    private final OrchestratorClient orchestratorClient;

    public TelemetrySink(TelemetryToAccidentMapper telemetryMapper, OrchestratorClient orchestratorClient) {
        this.telemetryMapper = telemetryMapper;
        this.orchestratorClient = orchestratorClient;

        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Spring Cloud Stream function that processes incoming telemetry messages.
     * Filters by g-force threshold and forwards accidents to orchestrator.
     *
     * Outputs to TWO destinations for downstream systems:
     * - processTelemetry-out-0: vehicle_events queue (original format)
     * - processTelemetry-out-1: fnol_reports queue (enriched reports)
     */
    @Bean
    public Function<String, Message<?>[]> processTelemetry() {
        return message -> {
            try {
                // Deserialize to typed TelemetryMessage
                TelemetryMessage telemetry = mapper.readValue(message, TelemetryMessage.class);

                // Quick filter - only forward actual accidents
                if (telemetry.gForce() < G_FORCE_THRESHOLD) {
                    log.debug("Telemetry event: g-force={}, skipping (below {} threshold)",
                            String.format("%.2f", telemetry.gForce()), G_FORCE_THRESHOLD);
                    return null;
                }

                // Log accident detection
                log.info("========================================");
                log.info("ACCIDENT DETECTED - Forwarding to Orchestrator");
                log.info("========================================");
                log.info("Policy ID:    {}", telemetry.policyId());
                log.info("Vehicle ID:   {}", telemetry.vehicleId());
                log.info("Driver ID:    {}", telemetry.driverId());
                log.info("VIN:          {}", telemetry.vin());
                log.info("----------------------------------------");
                log.info("Severity:     {}", telemetry.severity());
                log.info("G-Force:      {}", telemetry.gForce());
                log.info("Speed:        {} mph (limit: {})", telemetry.speedMph(), telemetry.speedLimitMph());
                log.info("----------------------------------------");
                log.info("Location:     {}", telemetry.currentStreet());
                log.info("Coordinates:  {}, {}", telemetry.gpsLatitude(), telemetry.gpsLongitude());
                log.info("========================================");

                // Map to AccidentEvent and forward to orchestrator
                AccidentEvent event = telemetryMapper.toAccidentEvent(telemetry);
                FNOLReport report = orchestratorClient.submitAccident(event);

                if (report == null) {
                    log.warn("Orchestrator did not return a report for policyId={}", telemetry.policyId());
                    return null;
                }

                // Output to downstream queues
                String severity = report.impact() != null ? report.impact().severity().name() : "UNKNOWN";

                // Output 0: Original TelemetryMessage for vehicle_events queue
                Message<String> vehicleEventsMessage = MessageBuilder
                    .withPayload(message)
                    .setHeader("severity", severity)
                    .build();

                // Output 1: Enriched FNOLReport for fnol_reports queue
                String reportJson = mapper.writeValueAsString(report);
                Message<String> fnolReportMessage = MessageBuilder
                    .withPayload(reportJson)
                    .setHeader("severity", severity)
                    .setHeader("claimNumber", report.claimNumber())
                    .build();

                log.info("Publishing to queues: vehicle_events + fnol_reports, claim={}",
                        report.claimNumber());

                return new Message<?>[] { vehicleEventsMessage, fnolReportMessage };

            } catch (Exception e) {
                log.error("Failed to process telemetry event: {}", e.getMessage(), e);
                return null;
            }
        };
    }
}
