package com.insurancemegacorp.crashsink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemegacorp.crash.domain.AccidentEvent;
import com.insurancemegacorp.crash.domain.FNOLReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Spring Cloud Stream sink that consumes vehicle accident events from RabbitMQ
 * and processes them through the CRASH agent pipeline.
 *
 * Phase 2: Basic message consumption with typed TelemetryMessage ✅
 * Phase 3: Full FNOL processing via Embabel agents ✅
 * Phase 4: Database persistence ✅
 * Phase 5: Output queue publishing (via Spring Cloud Stream output binding)
 */
@Component
public class CrashSink {
    private static final Logger log = LoggerFactory.getLogger(CrashSink.class);
    private final ObjectMapper mapper;

    {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    private final TelemetryToAccidentMapper telemetryMapper;
    private final FnolService fnolService;
    private final FnolPersistenceService persistenceService;

    public CrashSink(TelemetryToAccidentMapper telemetryMapper,
                     FnolService fnolService,
                     FnolPersistenceService persistenceService) {
        this.telemetryMapper = telemetryMapper;
        this.fnolService = fnolService;
        this.persistenceService = persistenceService;
    }

    /**
     * Spring Cloud Stream function that processes incoming telemetry messages.
     * Deserializes to TelemetryMessage, maps to AccidentEvent, and processes.
     */
    @Bean
    public Function<String, String> processTelemetry() {
        return message -> {
            try {
                // Deserialize to typed TelemetryMessage
                TelemetryMessage telemetry = mapper.readValue(message, TelemetryMessage.class);

                // Map to AccidentEvent for FNOL processing
                AccidentEvent event = telemetryMapper.toAccidentEvent(telemetry);

                log.info("========================================");
                log.info("ACCIDENT EVENT RECEIVED");
                log.info("========================================");
                log.info("Policy ID:    {}", event.policyId());
                log.info("Vehicle ID:   {}", event.vehicleId());
                log.info("Driver ID:    {}", event.driverId());
                log.info("VIN:          {}", event.vin());
                log.info("----------------------------------------");
                log.info("Severity:     {}", telemetry.severity());
                log.info("G-Force:      {}", event.gForce());
                log.info("Accel Mag:    {}", String.format("%.2f", telemetry.accelerometerMagnitude()));
                log.info("Speed:        {} mph (limit: {})", event.speedMph(), event.speedLimitMph());
                log.info("----------------------------------------");
                log.info("Location:     {}", event.currentStreet());
                log.info("Coordinates:  {}, {}", event.latitude(), event.longitude());
                log.info("Time:         {}", event.eventTime());
                log.info("----------------------------------------");
                log.info("Airbag:       {}", telemetry.airbagDeployed() != null && telemetry.airbagDeployed() ? "DEPLOYED" : "OK");
                log.info("ABS:          {}", telemetry.absEngaged() != null && telemetry.absEngaged() ? "ENGAGED" : "OK");
                log.info("Hazards:      {}", telemetry.hazardLightsOn() != null && telemetry.hazardLightsOn() ? "ON" : "OFF");
                log.info("========================================");

                // Check if this should be processed as an accident
                if (!fnolService.shouldProcess(event.gForce())) {
                    log.info("G-force below threshold, skipping FNOL processing");
                    return null;
                }

                // Phase 3: Process through Embabel agents
                FNOLReport report = fnolService.processAccident(event);

                // Phase 4: Persist FNOL to database
                try {
                    FnolEntity saved = persistenceService.persist(report);
                    log.info("FNOL persisted to database: id={}, claim={}",
                            saved.getId(), saved.getClaimNumber());
                } catch (Exception e) {
                    log.error("Failed to persist FNOL to database: {}", e.getMessage(), e);
                    // Continue processing even if persistence fails
                }

                // Phase 5: Return serialized report for output queue binding
                String reportJson = mapper.writeValueAsString(report);
                log.info("Publishing FNOL to output queue: severity={}",
                        report.impact() != null ? report.impact().severity() : "UNKNOWN");
                return reportJson;

            } catch (Exception e) {
                log.error("Failed to process accident event: {}", e.getMessage(), e);
                return null;
            }
        };
    }
}
