package com.insurancemegacorp.crash.sink;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a flattened telemetry message from the vehicle telematics system.
 * This matches the JSON schema output by imc-telematics-gen.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryMessage(
    // Core identifiers
    @JsonProperty("policy_id") int policyId,
    @JsonProperty("vehicle_id") int vehicleId,
    @JsonProperty("driver_id") int driverId,
    @JsonProperty("vin") String vin,

    // Event timing
    @JsonProperty("event_time") String eventTime,

    // Speed data
    @JsonProperty("speed_mph") double speedMph,
    @JsonProperty("speed_limit_mph") int speedLimitMph,

    // Impact detection
    @JsonProperty("g_force") double gForce,
    @JsonProperty("accelerometer_x") double accelerometerX,
    @JsonProperty("accelerometer_y") double accelerometerY,
    @JsonProperty("accelerometer_z") double accelerometerZ,

    // Rotation detection
    @JsonProperty("gyroscope_x") Double gyroscopeX,
    @JsonProperty("gyroscope_y") Double gyroscopeY,
    @JsonProperty("gyroscope_z") Double gyroscopeZ,

    // GPS location
    @JsonProperty("gps_latitude") double gpsLatitude,
    @JsonProperty("gps_longitude") double gpsLongitude,
    @JsonProperty("current_street") String currentStreet,
    @JsonProperty("heading_degrees") Double headingDegrees,
    @JsonProperty("gps_altitude") Double gpsAltitude,
    @JsonProperty("gps_accuracy_meters") Double gpsAccuracyMeters,

    // Device status
    @JsonProperty("device_battery_level") Integer deviceBatteryLevel,
    @JsonProperty("device_signal_strength") Integer deviceSignalStrength,

    // Vehicle status
    @JsonProperty("engine_rpm") Integer engineRpm,
    @JsonProperty("fuel_level_percent") Integer fuelLevelPercent,
    @JsonProperty("odometer_miles") Integer odometerMiles,
    @JsonProperty("ambient_temp_celsius") Double ambientTempCelsius,

    // Tire pressure
    @JsonProperty("tire_pressure_fl_psi") Double tirePressureFlPsi,
    @JsonProperty("tire_pressure_fr_psi") Double tirePressureFrPsi,
    @JsonProperty("tire_pressure_rl_psi") Double tirePressureRlPsi,
    @JsonProperty("tire_pressure_rr_psi") Double tirePressureRrPsi,

    // Safety systems
    @JsonProperty("abs_engaged") Boolean absEngaged,
    @JsonProperty("airbag_deployed") Boolean airbagDeployed,
    @JsonProperty("seatbelt_fastened") Boolean seatbeltFastened,
    @JsonProperty("doors_locked") Boolean doorsLocked,
    @JsonProperty("hazard_lights_on") Boolean hazardLightsOn,

    // Accident metadata
    @JsonProperty("accident_type") String accidentType
) {
    /**
     * Calculate the 3D accelerometer magnitude.
     */
    public double accelerometerMagnitude() {
        return Math.sqrt(
            accelerometerX * accelerometerX +
            accelerometerY * accelerometerY +
            accelerometerZ * accelerometerZ
        );
    }

    /**
     * Classify severity based on g-force thresholds.
     */
    public String severity() {
        if (gForce >= 5.0) {
            return "SEVERE";
        } else if (gForce >= 3.0) {
            return "MODERATE";
        } else {
            return "MINOR";
        }
    }
}
