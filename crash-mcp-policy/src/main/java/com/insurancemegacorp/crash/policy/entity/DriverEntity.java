package com.insurancemegacorp.crash.policy.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "drivers")
public class DriverEntity {

    @Id
    @Column(name = "driver_id")
    private Integer driverId;

    @Column(name = "policy_id")
    private Integer policyId;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "license_number")
    private String licenseNumber;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    // Getters
    public Integer getDriverId() { return driverId; }
    public Integer getPolicyId() { return policyId; }
    public Integer getCustomerId() { return customerId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getLicenseNumber() { return licenseNumber; }
    public Integer getRiskScore() { return riskScore; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public Boolean getIsPrimary() { return isPrimary; }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
