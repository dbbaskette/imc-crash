package com.insurancemegacorp.crash.services;

import com.insurancemegacorp.crash.domain.NearbyServices;
import com.insurancemegacorp.crash.domain.NearbyServices.ServiceLocation;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MCP Tools for finding nearby services relevant to accident response.
 * In a real implementation, these would call Google Places API or similar.
 */
@Service
public class NearbyServicesService {

    private final Random random = new Random();

    /**
     * Find nearby auto body shops.
     */
    @McpTool(description = "Find nearby auto body shops for vehicle repair. " +
                        "Returns list of shops with distance, ratings, and contact info.")
    public List<ServiceLocation> findBodyShops(
            @McpToolParam(description = "Latitude coordinate of accident") 
            double latitude,
            
            @McpToolParam(description = "Longitude coordinate of accident") 
            double longitude,
            
            @McpToolParam(description = "Search radius in miles") 
            double radiusMiles
    ) {
        // Simulated data - in production, call Google Places API
        List<ServiceLocation> shops = new ArrayList<>();
        
        String[][] bodyShopData = {
            {"Leesburg Auto Body", "120 Main St, Leesburg, VA", "703-555-0142"},
            {"Precision Collision Center", "456 Oak Ave, Leesburg, VA", "703-555-0188"},
            {"Quick Fix Auto Repair", "789 Industrial Blvd, Ashburn, VA", "703-555-0234"},
            {"Premier Body Works", "321 Commerce Dr, Sterling, VA", "703-555-0567"}
        };
        
        for (int i = 0; i < Math.min(3, bodyShopData.length); i++) {
            double distance = 0.5 + random.nextDouble() * (radiusMiles - 0.5);
            double rating = 3.5 + random.nextDouble() * 1.5;
            boolean preferred = random.nextDouble() < 0.3;
            
            shops.add(new ServiceLocation(
                bodyShopData[i][0],
                bodyShopData[i][1],
                bodyShopData[i][2],
                Math.round(distance * 10) / 10.0,
                Math.round(rating * 10) / 10.0,
                null,  // No ETA for stationary locations
                preferred,
                random.nextBoolean()
            ));
        }
        
        return shops;
    }

    /**
     * Find nearby tow truck services.
     */
    @McpTool(description = "Find nearby tow truck services. " +
                        "Returns list of tow services with ETA and contact info.")
    public List<ServiceLocation> findTowServices(
            @McpToolParam(description = "Latitude coordinate of accident") 
            double latitude,
            
            @McpToolParam(description = "Longitude coordinate of accident") 
            double longitude,
            
            @McpToolParam(description = "Search radius in miles") 
            double radiusMiles
    ) {
        List<ServiceLocation> towServices = new ArrayList<>();
        
        String[][] towData = {
            {"AAA Roadside", "Regional Coverage", "800-222-4357"},
            {"Loudoun Towing", "540 Route 7, Leesburg, VA", "703-555-0199"},
            {"24/7 Tow Service", "890 Industrial Park, Ashburn, VA", "703-555-0877"}
        };
        
        for (int i = 0; i < Math.min(2, towData.length); i++) {
            double distance = 0.8 + random.nextDouble() * 3;
            int eta = 10 + random.nextInt(25);
            
            towServices.add(new ServiceLocation(
                towData[i][0],
                towData[i][1],
                towData[i][2],
                Math.round(distance * 10) / 10.0,
                4.0 + random.nextDouble() * 1.0,
                eta,
                i == 0, // AAA is preferred
                true    // Tow services are always "open"
            ));
        }
        
        return towServices;
    }

    /**
     * Find nearby hospitals and urgent care facilities.
     */
    @McpTool(description = "Find nearby hospitals and urgent care facilities. " +
                        "Returns list with trauma center status and contact info.")
    public List<ServiceLocation> findMedicalFacilities(
            @McpToolParam(description = "Latitude coordinate of accident") 
            double latitude,
            
            @McpToolParam(description = "Longitude coordinate of accident") 
            double longitude,
            
            @McpToolParam(description = "Search radius in miles") 
            double radiusMiles
    ) {
        List<ServiceLocation> medical = new ArrayList<>();
        
        String[][] hospitalData = {
            {"Inova Loudoun Hospital", "44045 Riverside Pkwy, Leesburg, VA", "703-555-0911"},
            {"Reston Hospital Center", "1850 Town Center Pkwy, Reston, VA", "703-555-0922"},
            {"Urgent Care Plus", "123 Main St, Ashburn, VA", "703-555-0933"}
        };
        
        for (int i = 0; i < hospitalData.length; i++) {
            double distance = 1.0 + random.nextDouble() * 5;
            
            medical.add(new ServiceLocation(
                hospitalData[i][0],
                hospitalData[i][1],
                hospitalData[i][2],
                Math.round(distance * 10) / 10.0,
                4.2 + random.nextDouble() * 0.8,
                null,
                i < 2,  // Hospitals are preferred
                true
            ));
        }
        
        return medical;
    }

    /**
     * Find nearby rental car locations.
     */
    @McpTool(description = "Find nearby rental car locations for temporary transportation.")
    public List<ServiceLocation> findRentalCars(
            @McpToolParam(description = "Latitude coordinate of accident") 
            double latitude,
            
            @McpToolParam(description = "Longitude coordinate of accident") 
            double longitude
    ) {
        List<ServiceLocation> rentals = new ArrayList<>();
        
        String[][] rentalData = {
            {"Enterprise Rent-A-Car", "567 Market St, Leesburg, VA", "703-555-0155"},
            {"Hertz", "890 Commerce Blvd, Ashburn, VA", "703-555-0166"},
            {"Budget", "234 Route 7, Sterling, VA", "703-555-0177"}
        };
        
        for (int i = 0; i < 2; i++) {
            double distance = 1.0 + random.nextDouble() * 4;
            
            rentals.add(new ServiceLocation(
                rentalData[i][0],
                rentalData[i][1],
                rentalData[i][2],
                Math.round(distance * 10) / 10.0,
                4.0 + random.nextDouble() * 1.0,
                null,
                i == 0,  // Enterprise is preferred partner
                true
            ));
        }
        
        return rentals;
    }

    /**
     * Get all nearby services based on accident severity.
     *
     * Severity determines which services are included:
     * - SEVERE: All services (hospitals, tow, body shops, rentals)
     * - MODERATE: Tow, body shops, rentals (no hospitals)
     * - MINOR: Body shops only (vehicle likely drivable)
     */
    @McpTool(description = "Get all relevant nearby services based on accident severity. " +
                        "SEVERE: hospitals + tow + body + rentals. " +
                        "MODERATE: tow + body + rentals. " +
                        "MINOR: body shops only (vehicle drivable).")
    public NearbyServices getAllNearbyServices(
            @McpToolParam(description = "Latitude coordinate of accident")
            double latitude,

            @McpToolParam(description = "Longitude coordinate of accident")
            double longitude,

            @McpToolParam(description = "Accident severity: MINOR, MODERATE, or SEVERE")
            String severity,

            @McpToolParam(description = "Search radius in miles")
            double radiusMiles
    ) {
        // Body shops always included - every accident needs repair assessment
        List<ServiceLocation> bodyShops = findBodyShops(latitude, longitude, radiusMiles);

        // Tow services only for MODERATE and SEVERE (vehicle not drivable)
        List<ServiceLocation> towServices = severity.equals("MINOR")
            ? List.of()
            : findTowServices(latitude, longitude, radiusMiles);

        // Medical facilities only for SEVERE (potential injuries)
        List<ServiceLocation> medical = severity.equals("SEVERE")
            ? findMedicalFacilities(latitude, longitude, radiusMiles)
            : List.of();

        // Rentals for MODERATE and SEVERE (will need replacement vehicle)
        List<ServiceLocation> rentals = severity.equals("MINOR")
            ? List.of()
            : findRentalCars(latitude, longitude);

        // Determine if vehicle is drivable
        boolean drivable = severity.equals("MINOR");

        // Build dispatch recommendation based on severity
        String recommendation;
        if (severity.equals("SEVERE")) {
            recommendation = "URGENT: Dispatch tow service immediately. Medical facilities alerted. " +
                           "Rental car pre-arranged.";
        } else if (severity.equals("MODERATE")) {
            recommendation = "Tow service recommended - vehicle likely not drivable. " +
                           "Rental car information provided.";
        } else {
            recommendation = "Vehicle appears drivable. Body shop referral provided for damage assessment. " +
                           "No tow or rental needed at this time.";
        }

        return new NearbyServices(
            bodyShops,
            towServices,
            medical,
            rentals,
            recommendation,
            drivable
        );
    }
}
