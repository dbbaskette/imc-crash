package com.insurancemegacorp.crash.orchestrator;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for persisting FNOL reports to PostgreSQL.
 * Stores both denormalized key fields for querying and the full JSON report.
 */
@Entity
@Table(name = "fnol_reports")
public class FnolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Claim identification
    @Column(name = "claim_number", nullable = false, unique = true, length = 50)
    private String claimNumber;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    // Event details
    @Column(name = "policy_id", nullable = false)
    private Integer policyId;

    @Column(name = "vehicle_id")
    private Integer vehicleId;

    @Column(name = "driver_id")
    private Integer driverId;

    @Column(name = "vin", length = 20)
    private String vin;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    // Location
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "street")
    private String street;

    // Impact analysis
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "impact_type", length = 50)
    private String impactType;

    @Column(name = "speed_at_impact")
    private Double speedAtImpact;

    @Column(name = "g_force")
    private Double gForce;

    @Column(name = "was_speeding")
    private Boolean wasSpeeding;

    @Column(name = "airbag_deployed")
    private Boolean airbagDeployed;

    // Environment
    @Column(name = "weather_conditions", length = 100)
    private String weatherConditions;

    @Column(name = "road_surface", length = 50)
    private String roadSurface;

    @Column(name = "daylight_status", length = 20)
    private String daylightStatus;

    // Policy info
    @Column(name = "policy_number", length = 50)
    private String policyNumber;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "driver_phone", length = 50)
    private String driverPhone;

    @Column(name = "driver_email")
    private String driverEmail;

    @Column(name = "vehicle_make", length = 100)
    private String vehicleMake;

    @Column(name = "vehicle_model", length = 100)
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    // Communications
    @Column(name = "sms_sent")
    private Boolean smsSent;

    @Column(name = "push_sent")
    private Boolean pushSent;

    @Column(name = "adjuster_notified")
    private Boolean adjusterNotified;

    @Column(name = "assigned_adjuster")
    private String assignedAdjuster;

    @Column(name = "roadside_dispatched")
    private Boolean roadsideDispatched;

    // Full JSON report
    @Column(name = "report_json", nullable = false, columnDefinition = "TEXT")
    private String reportJson;

    // Metadata
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public void setClaimNumber(String claimNumber) {
        this.claimNumber = claimNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Integer policyId) {
        this.policyId = policyId;
    }

    public Integer getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Integer vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Integer getDriverId() {
        return driverId;
    }

    public void setDriverId(Integer driverId) {
        this.driverId = driverId;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getImpactType() {
        return impactType;
    }

    public void setImpactType(String impactType) {
        this.impactType = impactType;
    }

    public Double getSpeedAtImpact() {
        return speedAtImpact;
    }

    public void setSpeedAtImpact(Double speedAtImpact) {
        this.speedAtImpact = speedAtImpact;
    }

    public Double getGForce() {
        return gForce;
    }

    public void setGForce(Double gForce) {
        this.gForce = gForce;
    }

    public Boolean getWasSpeeding() {
        return wasSpeeding;
    }

    public void setWasSpeeding(Boolean wasSpeeding) {
        this.wasSpeeding = wasSpeeding;
    }

    public Boolean getAirbagDeployed() {
        return airbagDeployed;
    }

    public void setAirbagDeployed(Boolean airbagDeployed) {
        this.airbagDeployed = airbagDeployed;
    }

    public String getWeatherConditions() {
        return weatherConditions;
    }

    public void setWeatherConditions(String weatherConditions) {
        this.weatherConditions = weatherConditions;
    }

    public String getRoadSurface() {
        return roadSurface;
    }

    public void setRoadSurface(String roadSurface) {
        this.roadSurface = roadSurface;
    }

    public String getDaylightStatus() {
        return daylightStatus;
    }

    public void setDaylightStatus(String daylightStatus) {
        this.daylightStatus = daylightStatus;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public void setDriverPhone(String driverPhone) {
        this.driverPhone = driverPhone;
    }

    public String getDriverEmail() {
        return driverEmail;
    }

    public void setDriverEmail(String driverEmail) {
        this.driverEmail = driverEmail;
    }

    public String getVehicleMake() {
        return vehicleMake;
    }

    public void setVehicleMake(String vehicleMake) {
        this.vehicleMake = vehicleMake;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public Integer getVehicleYear() {
        return vehicleYear;
    }

    public void setVehicleYear(Integer vehicleYear) {
        this.vehicleYear = vehicleYear;
    }

    public Boolean getSmsSent() {
        return smsSent;
    }

    public void setSmsSent(Boolean smsSent) {
        this.smsSent = smsSent;
    }

    public Boolean getPushSent() {
        return pushSent;
    }

    public void setPushSent(Boolean pushSent) {
        this.pushSent = pushSent;
    }

    public Boolean getAdjusterNotified() {
        return adjusterNotified;
    }

    public void setAdjusterNotified(Boolean adjusterNotified) {
        this.adjusterNotified = adjusterNotified;
    }

    public String getAssignedAdjuster() {
        return assignedAdjuster;
    }

    public void setAssignedAdjuster(String assignedAdjuster) {
        this.assignedAdjuster = assignedAdjuster;
    }

    public Boolean getRoadsideDispatched() {
        return roadsideDispatched;
    }

    public void setRoadsideDispatched(Boolean roadsideDispatched) {
        this.roadsideDispatched = roadsideDispatched;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
