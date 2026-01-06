package com.insurancemegacorp.crash.policy;

import com.insurancemegacorp.crash.domain.PolicyInfo;
import com.insurancemegacorp.crash.domain.PolicyInfo.Driver;
import com.insurancemegacorp.crash.domain.PolicyInfo.Policy;
import com.insurancemegacorp.crash.domain.PolicyInfo.Vehicle;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MCP Tools for looking up insurance policy, driver, and vehicle information.
 * In a real implementation, these would query the insurance company's database.
 */
@Service
public class PolicyService {

    private final Random random = new Random();

    // Simulated database of policies
    private static final Map<Integer, SimulatedPolicy> POLICIES = Map.of(
        200018, new SimulatedPolicy("POL-200018", "Jane Smith", "+1-703-555-0123", 
                                    "jane.smith@email.com", 2022, "Honda", "Accord", "Silver"),
        200019, new SimulatedPolicy("POL-200019", "John Doe", "+1-703-555-0456",
                                    "john.doe@email.com", 2021, "Toyota", "Camry", "Blue"),
        200020, new SimulatedPolicy("POL-200020", "Sarah Johnson", "+1-703-555-0789",
                                    "sarah.j@email.com", 2023, "Ford", "F-150", "White")
    );

    /**
     * Look up policy details by policy ID.
     */
    @Tool(description = "Look up insurance policy details including coverage types, " +
                        "deductible, and available benefits.")
    public Policy lookupPolicy(
            @ToolParam(description = "Insurance policy ID") 
            int policyId
    ) {
        SimulatedPolicy sim = POLICIES.getOrDefault(policyId, 
            new SimulatedPolicy("POL-" + policyId, "Unknown Driver", "+1-555-000-0000",
                               "unknown@email.com", 2020, "Unknown", "Unknown", "Unknown"));
        
        List<String> coverage = List.of("Comprehensive", "Collision", "Liability", 
                                        "Medical Payments", "Uninsured Motorist");
        
        return new Policy(
            sim.policyNumber(),
            "ACTIVE",
            coverage,
            500,  // deductible
            true, // roadside assistance
            true  // rental coverage
        );
    }

    /**
     * Get driver profile by driver ID.
     */
    @Tool(description = "Get driver profile including contact information, " +
                        "risk score, and emergency contacts.")
    public Driver getDriverProfile(
            @ToolParam(description = "Driver ID") 
            int driverId,
            
            @ToolParam(description = "Associated policy ID") 
            int policyId
    ) {
        SimulatedPolicy sim = POLICIES.getOrDefault(policyId,
            new SimulatedPolicy("POL-" + policyId, "Unknown Driver", "+1-555-000-0000",
                               "unknown@email.com", 2020, "Unknown", "Unknown", "Unknown"));
        
        int riskScore = 50 + random.nextInt(40); // 50-90
        
        return new Driver(
            sim.driverName(),
            sim.phone(),
            sim.email(),
            riskScore,
            "Emergency Contact",
            "+1-703-555-9999"
        );
    }

    /**
     * Get vehicle details by vehicle ID or VIN.
     */
    @Tool(description = "Get vehicle details including make, model, year, " +
                        "and estimated value.")
    public Vehicle getVehicleDetails(
            @ToolParam(description = "Vehicle ID") 
            int vehicleId,
            
            @ToolParam(description = "Vehicle Identification Number") 
            String vin,
            
            @ToolParam(description = "Associated policy ID") 
            int policyId
    ) {
        SimulatedPolicy sim = POLICIES.getOrDefault(policyId,
            new SimulatedPolicy("POL-" + policyId, "Unknown Driver", "+1-555-000-0000",
                               "unknown@email.com", 2020, "Unknown", "Unknown", "Unknown"));
        
        // Estimate value based on year
        int baseValue = 15000 + (sim.year() - 2018) * 3000;
        int estimatedValue = baseValue + random.nextInt(5000);
        
        return new Vehicle(
            sim.year(),
            sim.make(),
            sim.model(),
            sim.color(),
            vin,
            "VA-" + String.format("%03d", vehicleId).substring(0, 3) + "-" + 
                String.format("%04d", random.nextInt(9999)),
            estimatedValue
        );
    }

    /**
     * Get complete policy information in one call.
     */
    @Tool(description = "Get complete policy information including policy details, " +
                        "driver profile, and vehicle information.")
    public PolicyInfo getFullPolicyInfo(
            @ToolParam(description = "Insurance policy ID") 
            int policyId,
            
            @ToolParam(description = "Driver ID") 
            int driverId,
            
            @ToolParam(description = "Vehicle ID") 
            int vehicleId,
            
            @ToolParam(description = "Vehicle Identification Number") 
            String vin
    ) {
        Policy policy = lookupPolicy(policyId);
        Driver driver = getDriverProfile(driverId, policyId);
        Vehicle vehicle = getVehicleDetails(vehicleId, vin, policyId);
        
        return new PolicyInfo(policy, driver, vehicle);
    }

    /**
     * Internal record for simulated policy data.
     */
    private record SimulatedPolicy(
        String policyNumber,
        String driverName,
        String phone,
        String email,
        int year,
        String make,
        String model,
        String color
    ) {}
}
