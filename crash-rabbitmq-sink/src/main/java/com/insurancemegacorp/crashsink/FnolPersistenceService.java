package com.insurancemegacorp.crashsink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemegacorp.crash.domain.FNOLReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for persisting FNOL reports to the database.
 */
@Service
public class FnolPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(FnolPersistenceService.class);

    private final FnolRepository repository;
    private final ObjectMapper objectMapper;

    public FnolPersistenceService(FnolRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Persist an FNOL report to the database.
     *
     * @param report The FNOL report to persist
     * @return The persisted entity
     */
    @Transactional
    public FnolEntity persist(FNOLReport report) {
        log.info("Persisting FNOL report: {}", report.claimNumber());

        // Check for duplicate
        if (repository.existsByClaimNumber(report.claimNumber())) {
            log.warn("FNOL report already exists for claim: {}", report.claimNumber());
            return repository.findByClaimNumber(report.claimNumber()).orElse(null);
        }

        FnolEntity entity = mapToEntity(report);

        try {
            entity.setReportJson(objectMapper.writeValueAsString(report));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize report JSON: {}", e.getMessage());
            entity.setReportJson("{}");
        }

        FnolEntity saved = repository.save(entity);
        log.info("FNOL report persisted: id={}, claim={}", saved.getId(), saved.getClaimNumber());

        return saved;
    }

    /**
     * Map FNOLReport record to FnolEntity.
     */
    private FnolEntity mapToEntity(FNOLReport report) {
        FnolEntity entity = new FnolEntity();

        // Claim identification
        entity.setClaimNumber(report.claimNumber());
        entity.setStatus(report.status());

        // Event details
        if (report.event() != null) {
            entity.setPolicyId(report.event().policyId());
            entity.setVehicleId(report.event().vehicleId());
            entity.setDriverId(report.event().driverId());
            entity.setVin(report.event().vin());
            entity.setEventTime(report.event().eventTime());
            entity.setLatitude(report.event().latitude());
            entity.setLongitude(report.event().longitude());
            entity.setStreet(report.event().currentStreet());
            entity.setGForce(report.event().gForce());
        }

        // Impact analysis
        if (report.impact() != null) {
            entity.setSeverity(report.impact().severity() != null
                ? report.impact().severity().name()
                : "UNKNOWN");
            entity.setImpactType(report.impact().impactType() != null
                ? report.impact().impactType().name()
                : null);
            entity.setSpeedAtImpact(report.impact().estimatedSpeedAtImpact());
            entity.setWasSpeeding(report.impact().wasSpeeding());
            entity.setAirbagDeployed(report.impact().airbagLikely());
        } else {
            entity.setSeverity("UNKNOWN");
        }

        // Environment
        if (report.environment() != null) {
            if (report.environment().weather() != null) {
                entity.setWeatherConditions(report.environment().weather().conditions());
            }
            if (report.environment().roadConditions() != null) {
                entity.setRoadSurface(report.environment().roadConditions().surfaceCondition());
            }
            entity.setDaylightStatus(report.environment().daylightStatus());
        }

        // Policy info
        if (report.policy() != null) {
            if (report.policy().policy() != null) {
                entity.setPolicyNumber(report.policy().policy().policyNumber());
            }
            if (report.policy().driver() != null) {
                entity.setDriverName(report.policy().driver().name());
                entity.setDriverPhone(report.policy().driver().phone());
                entity.setDriverEmail(report.policy().driver().email());
            }
            if (report.policy().vehicle() != null) {
                entity.setVehicleMake(report.policy().vehicle().make());
                entity.setVehicleModel(report.policy().vehicle().model());
                entity.setVehicleYear(report.policy().vehicle().year());
            }
        }

        // Communications
        if (report.communications() != null) {
            if (report.communications().driverOutreach() != null) {
                entity.setSmsSent(report.communications().driverOutreach().smsSent());
                entity.setPushSent(report.communications().driverOutreach().pushSent());
            }
            entity.setAdjusterNotified(report.communications().adjusterNotified());
            entity.setAssignedAdjuster(report.communications().assignedAdjuster());
            entity.setRoadsideDispatched(report.communications().roadsideDispatched());
        }

        // Metadata
        entity.setGeneratedAt(report.generatedAt());

        return entity;
    }
}
