package com.insurancemegacorp.crash.sink.scdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancemegacorp.crash.domain.AccidentEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Function;

/**
 * SCDF Stream processor for accident detection.
 * Consumes flattened telemetry JSON from RabbitMQ, filters for accidents
 * based on g-force threshold, and forwards to crash-orchestrator via HTTP.
 */
@Component
public class AccidentProcessor {

    private static final Logger log = LoggerFactory.getLogger(AccidentProcessor.class);

    @Value("${telemetry.accident.gforce.threshold:2.5}")
    private double gForceThreshold;

    private final OrchestratorClient orchestratorClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter messagesProcessed;
    private Counter accidentsDetected;
    private Counter messagesFiltered;
    private Counter processingErrors;

    public AccidentProcessor(OrchestratorClient orchestratorClient,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.orchestratorClient = orchestratorClient;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void initMetrics() {
        messagesProcessed = meterRegistry.counter("crash_sink_messages_total");
        accidentsDetected = meterRegistry.counter("crash_sink_accidents_total");
        messagesFiltered = meterRegistry.counter("crash_sink_filtered_total");
        processingErrors = meterRegistry.counter("crash_sink_errors_total");
    }

    /**
     * SCDF Stream processor function.
     * Consumes flattened telemetry JSON, filters for accidents, forwards to orchestrator.
     * Returns null to filter non-accident messages, or passthrough for downstream consumers.
     */
    @Bean
    public Function<String, String> accidentDetector() {
        return jsonMessage -> {
            messagesProcessed.increment();

            try {
                JsonNode root = objectMapper.readTree(jsonMessage);

                // Extract g-force from flattened JSON
                double gForce = root.path("g_force").asDouble(0.0);

                if (gForce < gForceThreshold) {
                    log.debug("Telemetry filtered: g-force={} below threshold {}",
                            String.format("%.2f", gForce), gForceThreshold);
                    messagesFiltered.increment();
                    return null; // Filter out non-accidents
                }

                // Extract fields for AccidentEvent
                AccidentEvent event = mapToAccidentEvent(root, gForce);

                log.info("Accident detected: policyId={}, gForce={:.2f}, location=[{:.4f}, {:.4f}]",
                        event.policyId(), gForce, event.latitude(), event.longitude());

                // Forward to orchestrator
                orchestratorClient.submitAccident(event);
                accidentsDetected.increment();

                // Return original message for downstream consumers (e.g., HDFS archival)
                return jsonMessage;

            } catch (Exception e) {
                log.error("Failed to process telemetry: {}", e.getMessage(), e);
                processingErrors.increment();
                return null; // Don't propagate malformed messages
            }
        };
    }

    /**
     * Map flattened telemetry JSON to AccidentEvent domain object.
     * The flattened format has 35 fields from imc-telematics-flattener.
     */
    private AccidentEvent mapToAccidentEvent(JsonNode root, double gForce) {
        // Parse event time - handle ISO format, epoch seconds (with decimals), or epoch millis
        Instant eventTime;
        JsonNode eventTimeNode = root.path("event_time");
        if (eventTimeNode.isTextual()) {
            eventTime = Instant.parse(eventTimeNode.asText());
        } else if (eventTimeNode.isNumber()) {
            // Telemetry sends epoch seconds with nanosecond decimals (e.g., 1768325426.542811890)
            // Check if value is in seconds (< 10 billion) or milliseconds (> 10 billion)
            double epochValue = eventTimeNode.asDouble();
            if (epochValue < 10_000_000_000L) {
                // Value is in seconds - convert to Instant
                long seconds = (long) epochValue;
                long nanos = (long) ((epochValue - seconds) * 1_000_000_000);
                eventTime = Instant.ofEpochSecond(seconds, nanos);
            } else {
                // Value is in milliseconds
                eventTime = Instant.ofEpochMilli(eventTimeNode.asLong());
            }
        } else {
            eventTime = Instant.now();
        }

        return new AccidentEvent(
                root.path("policy_id").asInt(),
                root.path("vehicle_id").asInt(),
                root.path("driver_id").asInt(),
                root.path("vin").asText(""),
                eventTime,
                root.path("speed_mph").asDouble(0.0),
                root.path("speed_limit_mph").asInt(0),
                gForce,
                root.path("gps_latitude").asDouble(0.0),
                root.path("gps_longitude").asDouble(0.0),
                root.path("current_street").asText(""),
                root.path("accelerometer_x").asDouble(0.0),
                root.path("accelerometer_y").asDouble(0.0),
                root.path("accelerometer_z").asDouble(0.0),
                getDoubleOrNull(root, "gyroscope_x"),
                getDoubleOrNull(root, "gyroscope_y"),
                getDoubleOrNull(root, "gyroscope_z"),
                getIntOrNull(root, "device_battery_level"),
                getIntOrNull(root, "device_signal_strength")
        );
    }

    private Double getDoubleOrNull(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asDouble();
    }

    private Integer getIntOrNull(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asInt();
    }
}
