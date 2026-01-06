package com.insurancemegacorp.crash.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;

/**
 * Represents a telemetry event from a safe driver app indicating a potential accident.
 * This is the primary input to the FNOL system.
 */
@JsonClassDescription("Telemetry event from safe driver app indicating potential accident")
public record AccidentEvent(
    @JsonPropertyDescription("Insurance policy identifier")
    int policyId,

    @JsonPropertyDescription("Internal vehicle identifier")
    int vehicleId,

    @JsonPropertyDescription("Driver identifier")
    int driverId,

    @JsonPropertyDescription("Vehicle Identification Number")
    String vin,

    @JsonPropertyDescription("Event timestamp in ISO 8601 format")
    Instant eventTime,

    @JsonPropertyDescription("Vehicle speed in miles per hour at time of event")
    double speedMph,

    @JsonPropertyDescription("Posted speed limit in miles per hour")
    int speedLimitMph,

    @JsonPropertyDescription("Calculated G-force from accelerometer data")
    double gForce,

    @JsonPropertyDescription("GPS latitude coordinate")
    double latitude,

    @JsonPropertyDescription("GPS longitude coordinate")
    double longitude,

    @JsonPropertyDescription("Street name from GPS location")
    String currentStreet,

    @JsonPropertyDescription("Accelerometer X-axis reading")
    double accelerometerX,

    @JsonPropertyDescription("Accelerometer Y-axis reading")
    double accelerometerY,

    @JsonPropertyDescription("Accelerometer Z-axis reading")
    double accelerometerZ,

    @JsonPropertyDescription("Gyroscope X-axis (pitch) reading")
    Double gyroscopeX,

    @JsonPropertyDescription("Gyroscope Y-axis (roll) reading")
    Double gyroscopeY,

    @JsonPropertyDescription("Gyroscope Z-axis (yaw) reading")
    Double gyroscopeZ,

    @JsonPropertyDescription("Device battery level percentage")
    Integer deviceBatteryLevel,

    @JsonPropertyDescription("Cellular signal strength in dBm")
    Integer deviceSignalStrength
) {
    /**
     * Convenience constructor with required fields only.
     */
    public AccidentEvent(int policyId, int vehicleId, int driverId, String vin,
                         Instant eventTime, double speedMph, int speedLimitMph,
                         double gForce, double latitude, double longitude,
                         String currentStreet, double accelerometerX,
                         double accelerometerY, double accelerometerZ) {
        this(policyId, vehicleId, driverId, vin, eventTime, speedMph, speedLimitMph,
             gForce, latitude, longitude, currentStreet, accelerometerX,
             accelerometerY, accelerometerZ, null, null, null, null, null);
    }
}
