package com.insurancemegacorp.crash.policy.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicles")
public class VehicleEntity {

    @Id
    @Column(name = "vehicle_id")
    private Integer vehicleId;

    @Column(name = "policy_id")
    private Integer policyId;

    @Column(name = "vin")
    private String vin;

    @Column(name = "make")
    private String make;

    @Column(name = "model")
    private String model;

    @Column(name = "year")
    private Integer year;

    @Column(name = "color")
    private String color;

    @Column(name = "license_plate")
    private String licensePlate;

    @Column(name = "estimated_value")
    private Integer estimatedValue;

    // Getters
    public Integer getVehicleId() { return vehicleId; }
    public Integer getPolicyId() { return policyId; }
    public String getVin() { return vin; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public Integer getYear() { return year; }
    public String getColor() { return color; }
    public String getLicensePlate() { return licensePlate; }
    public Integer getEstimatedValue() { return estimatedValue; }
}
