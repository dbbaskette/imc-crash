package com.insurancemegacorp.crash.policy;

import com.insurancemegacorp.crash.domain.PolicyInfo;
import com.insurancemegacorp.crash.domain.PolicyInfo.Driver;
import com.insurancemegacorp.crash.domain.PolicyInfo.Policy;
import com.insurancemegacorp.crash.domain.PolicyInfo.Vehicle;
import com.insurancemegacorp.crash.policy.entity.DriverEntity;
import com.insurancemegacorp.crash.policy.entity.PolicyEntity;
import com.insurancemegacorp.crash.policy.entity.VehicleEntity;
import com.insurancemegacorp.crash.policy.repository.DriverRepository;
import com.insurancemegacorp.crash.policy.repository.PolicyRepository;
import com.insurancemegacorp.crash.policy.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP Tools for looking up insurance policy, driver, and vehicle information.
 * Queries the PostgreSQL database for real policy data.
 */
@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);

    private final PolicyRepository policyRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;

    public PolicyService(PolicyRepository policyRepository,
                         DriverRepository driverRepository,
                         VehicleRepository vehicleRepository) {
        this.policyRepository = policyRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
    }

    /**
     * Look up policy details by policy ID.
     */
    @McpTool(description = "Look up insurance policy details including coverage types, " +
                        "deductible, and available benefits.")
    public Policy lookupPolicy(
            @McpToolParam(description = "Insurance policy ID")
            int policyId
    ) {
        log.info("Looking up policy: {}", policyId);

        PolicyEntity entity = policyRepository.findById(policyId).orElse(null);

        if (entity == null) {
            log.warn("Policy not found: {}, returning default", policyId);
            return createDefaultPolicy(policyId);
        }

        List<String> coverage = buildCoverageList(entity);

        return new Policy(
            entity.getPolicyNumber(),
            entity.getStatus(),
            coverage,
            entity.getDeductible() != null ? entity.getDeductible() : 500,
            entity.getHasRoadside() != null ? entity.getHasRoadside() : true,
            entity.getHasRental() != null ? entity.getHasRental() : true
        );
    }

    /**
     * Get driver profile by driver ID.
     */
    @McpTool(description = "Get driver profile including contact information, " +
                        "risk score, and emergency contacts.")
    public Driver getDriverProfile(
            @McpToolParam(description = "Driver ID")
            int driverId,

            @McpToolParam(description = "Associated policy ID")
            int policyId
    ) {
        log.info("Looking up driver: {} for policy: {}", driverId, policyId);

        // First try to find by driver ID
        DriverEntity entity = driverRepository.findById(driverId).orElse(null);

        // If not found, try to find primary driver for the policy
        if (entity == null) {
            entity = driverRepository.findByPolicyIdAndIsPrimaryTrue(policyId).orElse(null);
        }

        // If still not found, return default
        if (entity == null) {
            log.warn("Driver not found: {}, returning default", driverId);
            return createDefaultDriver(driverId);
        }

        return new Driver(
            entity.getFullName(),
            entity.getPhoneNumber() != null ? entity.getPhoneNumber() : "+1-555-000-0000",
            entity.getEmail() != null ? entity.getEmail() : "unknown@email.com",
            entity.getRiskScore() != null ? entity.getRiskScore() : 75,
            entity.getEmergencyContactName() != null ? entity.getEmergencyContactName() : "Emergency Contact",
            entity.getEmergencyContactPhone() != null ? entity.getEmergencyContactPhone() : "+1-555-999-9999"
        );
    }

    /**
     * Get vehicle details by vehicle ID or VIN.
     */
    @McpTool(description = "Get vehicle details including make, model, year, " +
                        "and estimated value.")
    public Vehicle getVehicleDetails(
            @McpToolParam(description = "Vehicle ID")
            int vehicleId,

            @McpToolParam(description = "Vehicle Identification Number")
            String vin,

            @McpToolParam(description = "Associated policy ID")
            int policyId
    ) {
        log.info("Looking up vehicle: {} (VIN: {}) for policy: {}", vehicleId, vin, policyId);

        // Try to find by vehicle ID first
        VehicleEntity entity = vehicleRepository.findById(vehicleId).orElse(null);

        // If not found, try by VIN
        if (entity == null && vin != null && !vin.isBlank()) {
            entity = vehicleRepository.findByVin(vin).orElse(null);
        }

        // If still not found, try to find any vehicle for the policy
        if (entity == null) {
            List<VehicleEntity> vehicles = vehicleRepository.findByPolicyId(policyId);
            if (!vehicles.isEmpty()) {
                entity = vehicles.get(0);
            }
        }

        // If still not found, return default
        if (entity == null) {
            log.warn("Vehicle not found: {}, returning default", vehicleId);
            return createDefaultVehicle(vehicleId, vin);
        }

        return new Vehicle(
            entity.getYear() != null ? entity.getYear() : 2020,
            entity.getMake() != null ? entity.getMake() : "Unknown",
            entity.getModel() != null ? entity.getModel() : "Unknown",
            entity.getColor() != null ? entity.getColor() : "Unknown",
            entity.getVin() != null ? entity.getVin() : vin,
            entity.getLicensePlate() != null ? entity.getLicensePlate() : "UNK-0000",
            entity.getEstimatedValue() != null ? entity.getEstimatedValue() : 20000
        );
    }

    /**
     * Get complete policy information in one call.
     */
    @McpTool(description = "Get complete policy information including policy details, " +
                        "driver profile, and vehicle information.")
    public PolicyInfo getFullPolicyInfo(
            @McpToolParam(description = "Insurance policy ID")
            int policyId,

            @McpToolParam(description = "Driver ID")
            int driverId,

            @McpToolParam(description = "Vehicle ID")
            int vehicleId,

            @McpToolParam(description = "Vehicle Identification Number")
            String vin
    ) {
        log.info("Getting full policy info: policy={}, driver={}, vehicle={}", policyId, driverId, vehicleId);

        Policy policy = lookupPolicy(policyId);
        Driver driver = getDriverProfile(driverId, policyId);
        Vehicle vehicle = getVehicleDetails(vehicleId, vin, policyId);

        return new PolicyInfo(policy, driver, vehicle);
    }

    // Helper methods

    private List<String> buildCoverageList(PolicyEntity entity) {
        List<String> coverage = new ArrayList<>();
        if (entity.getHasComprehensive() != null && entity.getHasComprehensive()) {
            coverage.add("Comprehensive");
        }
        if (entity.getHasCollision() != null && entity.getHasCollision()) {
            coverage.add("Collision");
        }
        if (entity.getHasLiability() != null && entity.getHasLiability()) {
            coverage.add("Liability");
        }
        if (entity.getHasMedical() != null && entity.getHasMedical()) {
            coverage.add("Medical Payments");
        }
        if (entity.getHasUninsured() != null && entity.getHasUninsured()) {
            coverage.add("Uninsured Motorist");
        }
        // Default coverage if nothing is set
        if (coverage.isEmpty()) {
            coverage = List.of("Comprehensive", "Collision", "Liability", "Medical Payments", "Uninsured Motorist");
        }
        return coverage;
    }

    private Policy createDefaultPolicy(int policyId) {
        return new Policy(
            "POL-" + policyId,
            "ACTIVE",
            List.of("Comprehensive", "Collision", "Liability", "Medical Payments", "Uninsured Motorist"),
            500,
            true,
            true
        );
    }

    private Driver createDefaultDriver(int driverId) {
        return new Driver(
            "Unknown Driver",
            "+1-555-000-0000",
            "unknown@email.com",
            75,
            "Emergency Contact",
            "+1-555-999-9999"
        );
    }

    private Vehicle createDefaultVehicle(int vehicleId, String vin) {
        return new Vehicle(
            2020,
            "Unknown",
            "Unknown",
            "Unknown",
            vin != null ? vin : "UNKNOWN" + vehicleId,
            "UNK-0000",
            20000
        );
    }
}
