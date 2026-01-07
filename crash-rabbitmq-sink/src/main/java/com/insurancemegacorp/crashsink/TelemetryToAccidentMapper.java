package com.insurancemegacorp.crashsink;

import com.insurancemegacorp.crash.domain.AccidentEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Maps TelemetryMessage from RabbitMQ to AccidentEvent for FNOL processing.
 */
@Component
public class TelemetryToAccidentMapper {

    /**
     * Convert a TelemetryMessage to an AccidentEvent.
     *
     * @param telemetry the incoming telemetry message
     * @return AccidentEvent suitable for FNOL processing
     */
    public AccidentEvent toAccidentEvent(TelemetryMessage telemetry) {
        return new AccidentEvent(
            telemetry.policyId(),
            telemetry.vehicleId(),
            telemetry.driverId(),
            telemetry.vin(),
            parseEventTime(telemetry.eventTime()),
            telemetry.speedMph(),
            telemetry.speedLimitMph(),
            telemetry.gForce(),
            telemetry.gpsLatitude(),
            telemetry.gpsLongitude(),
            telemetry.currentStreet(),
            telemetry.accelerometerX(),
            telemetry.accelerometerY(),
            telemetry.accelerometerZ(),
            telemetry.gyroscopeX(),
            telemetry.gyroscopeY(),
            telemetry.gyroscopeZ(),
            telemetry.deviceBatteryLevel(),
            telemetry.deviceSignalStrength()
        );
    }

    private Instant parseEventTime(String eventTime) {
        if (eventTime == null || eventTime.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(eventTime);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
