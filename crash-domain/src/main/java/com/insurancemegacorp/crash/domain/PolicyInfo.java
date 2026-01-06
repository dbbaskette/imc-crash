package com.insurancemegacorp.crash.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Policy and driver information retrieved by the Policy agent.
 */
@JsonClassDescription("Insurance policy, driver, and vehicle information")
public record PolicyInfo(
    @JsonPropertyDescription("Policy details")
    Policy policy,

    @JsonPropertyDescription("Driver information")
    Driver driver,

    @JsonPropertyDescription("Vehicle information")
    Vehicle vehicle
) {
    @JsonClassDescription("Insurance policy details")
    public record Policy(
        @JsonPropertyDescription("Policy number")
        String policyNumber,

        @JsonPropertyDescription("Policy status: ACTIVE, SUSPENDED, CANCELLED")
        String status,

        @JsonPropertyDescription("List of coverage types")
        List<String> coverageTypes,

        @JsonPropertyDescription("Collision deductible amount in dollars")
        int deductible,

        @JsonPropertyDescription("Whether policy includes roadside assistance")
        boolean hasRoadsideAssistance,

        @JsonPropertyDescription("Whether policy includes rental car coverage")
        boolean hasRentalCoverage
    ) {}

    @JsonClassDescription("Driver information")
    public record Driver(
        @JsonPropertyDescription("Driver's full name")
        String name,

        @JsonPropertyDescription("Driver's phone number")
        String phone,

        @JsonPropertyDescription("Driver's email address")
        String email,

        @JsonPropertyDescription("Driver risk score (0-100, lower is better)")
        int riskScore,

        @JsonPropertyDescription("Emergency contact name")
        String emergencyContactName,

        @JsonPropertyDescription("Emergency contact phone")
        String emergencyContactPhone
    ) {}

    @JsonClassDescription("Vehicle information")
    public record Vehicle(
        @JsonPropertyDescription("Vehicle year")
        int year,

        @JsonPropertyDescription("Vehicle make")
        String make,

        @JsonPropertyDescription("Vehicle model")
        String model,

        @JsonPropertyDescription("Vehicle color")
        String color,

        @JsonPropertyDescription("Vehicle Identification Number")
        String vin,

        @JsonPropertyDescription("License plate number")
        String licensePlate,

        @JsonPropertyDescription("Estimated vehicle value in dollars")
        int estimatedValue
    ) {}
}
