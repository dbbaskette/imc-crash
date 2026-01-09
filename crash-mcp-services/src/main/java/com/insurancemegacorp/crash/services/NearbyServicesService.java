package com.insurancemegacorp.crash.services;

import com.insurancemegacorp.crash.domain.NearbyServices;
import com.insurancemegacorp.crash.domain.NearbyServices.ServiceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MCP Tools for finding nearby services relevant to accident response.
 * Uses TomTom Search API as the primary data source (better phone number coverage).
 * Falls back to Geoapify or simulated data if TomTom returns no results.
 */
@Service
public class NearbyServicesService {

    private static final Logger log = LoggerFactory.getLogger(NearbyServicesService.class);

    private final TomTomService tomTomService;
    private final GeoapifyService geoapifyService;
    private final Random random = new Random();

    public NearbyServicesService(TomTomService tomTomService, GeoapifyService geoapifyService) {
        this.tomTomService = tomTomService;
        this.geoapifyService = geoapifyService;
    }

    /**
     * Find nearby auto body shops.
     * Uses TomTom Search API for real results with phone numbers.
     * Falls back to Geoapify, then simulated data if needed.
     */
    @McpTool(description = "Find nearby auto body shops for vehicle repair. " +
                        "Use CUSTOMER HOME ADDRESS coordinates so repairs are convenient for them. " +
                        "Returns list of shops with distance, ratings, and contact info.")
    public List<ServiceLocation> findBodyShops(
            @McpToolParam(description = "Latitude of customer's home address")
            double latitude,

            @McpToolParam(description = "Longitude of customer's home address")
            double longitude,

            @McpToolParam(description = "Search radius in miles")
            double radiusMiles
    ) {
        // Try TomTom first (better phone number coverage)
        if (tomTomService.isConfigured()) {
            log.info("Finding body shops near ({}, {}) within {} miles using TomTom",
                    latitude, longitude, radiusMiles);

            List<ServiceLocation> shops = tomTomService.findBodyShops(latitude, longitude, radiusMiles, 5);

            if (!shops.isEmpty()) {
                log.info("Found {} body shops from TomTom API", shops.size());
                return shops;
            }
            log.warn("No body shops found from TomTom, trying Geoapify");
        }

        // Fallback to Geoapify
        if (geoapifyService.isConfigured()) {
            log.info("Finding body shops near ({}, {}) within {} miles using Geoapify",
                    latitude, longitude, radiusMiles);

            List<ServiceLocation> shops = geoapifyService.findBodyShops(latitude, longitude, radiusMiles, 5);

            if (!shops.isEmpty()) {
                log.info("Found {} body shops from Geoapify API", shops.size());
                return shops;
            }
            log.warn("No body shops found from Geoapify, falling back to simulated data");
        } else {
            log.info("APIs not configured, using simulated body shop data");
        }

        // Fallback to simulated data
        return getSimulatedBodyShops(radiusMiles);
    }

    /**
     * Generate simulated body shop data for when API is not available.
     */
    private List<ServiceLocation> getSimulatedBodyShops(double radiusMiles) {
        List<ServiceLocation> shops = new ArrayList<>();

        String[][] bodyShopData = {
            {"Atlanta Auto Body", "1234 Peachtree St NE, Atlanta, GA", "+1 404-555-0142"},
            {"Precision Collision Center", "5678 Roswell Rd, Sandy Springs, GA", "+1 770-555-0188"},
            {"Quick Fix Auto Repair", "9012 Buford Hwy, Duluth, GA", "+1 678-555-0234"},
            {"Premier Body Works", "3456 Cobb Pkwy, Marietta, GA", "+1 770-555-0567"}
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
     * Uses TomTom Search API with free-text search for better results.
     * Falls back to simulated data if API is not configured.
     */
    @McpTool(description = "Find nearby tow truck services. " +
                        "Use ACCIDENT LOCATION coordinates - tow trucks come to the crash site. " +
                        "Returns list of tow services with ETA and contact info.")
    public List<ServiceLocation> findTowServices(
            @McpToolParam(description = "Latitude of accident location")
            double latitude,

            @McpToolParam(description = "Longitude of accident location")
            double longitude,

            @McpToolParam(description = "Search radius in miles")
            double radiusMiles
    ) {
        // Try TomTom API first
        if (tomTomService.isConfigured()) {
            log.info("Finding tow services near ({}, {}) within {} miles using TomTom",
                    latitude, longitude, radiusMiles);

            List<ServiceLocation> services = tomTomService.findTowServices(latitude, longitude, radiusMiles, 5);

            if (!services.isEmpty()) {
                log.info("Found {} tow services from TomTom API", services.size());
                return services;
            }
            log.warn("No tow services found from TomTom, falling back to simulated data");
        } else {
            log.info("TomTom API not configured, using simulated tow service data");
        }

        // Fallback to simulated data
        return getSimulatedTowServices(radiusMiles);
    }

    /**
     * Generate simulated tow service data for when API is not available.
     */
    private List<ServiceLocation> getSimulatedTowServices(double radiusMiles) {
        List<ServiceLocation> towServices = new ArrayList<>();

        String[][] towData = {
            {"AAA Roadside", "Regional Coverage", "+1 800-222-4357"},
            {"Metro Atlanta Towing", "1234 Industrial Blvd, Atlanta, GA", "+1 404-555-0199"},
            {"24/7 Tow Service", "5678 Peachtree Industrial, Duluth, GA", "+1 678-555-0877"}
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
     * Uses TomTom Search API for real results with phone numbers.
     * Falls back to Geoapify, then simulated data if needed.
     */
    @McpTool(description = "Find nearby hospitals and urgent care facilities. " +
                        "Use ACCIDENT LOCATION coordinates - find closest medical care to crash site. " +
                        "Returns list with distance and contact info.")
    public List<ServiceLocation> findMedicalFacilities(
            @McpToolParam(description = "Latitude of accident location")
            double latitude,

            @McpToolParam(description = "Longitude of accident location")
            double longitude,

            @McpToolParam(description = "Search radius in miles")
            double radiusMiles
    ) {
        // Try TomTom first (better phone number coverage)
        if (tomTomService.isConfigured()) {
            log.info("Finding medical facilities near ({}, {}) within {} miles using TomTom",
                    latitude, longitude, radiusMiles);

            List<ServiceLocation> facilities = tomTomService.findHospitals(latitude, longitude, radiusMiles, 5);

            if (!facilities.isEmpty()) {
                log.info("Found {} medical facilities from TomTom API", facilities.size());
                return facilities;
            }
            log.warn("No medical facilities found from TomTom, trying Geoapify");
        }

        // Fallback to Geoapify
        if (geoapifyService.isConfigured()) {
            log.info("Finding medical facilities near ({}, {}) within {} miles using Geoapify",
                    latitude, longitude, radiusMiles);

            List<ServiceLocation> facilities = geoapifyService.findHospitals(latitude, longitude, radiusMiles, 5);

            if (!facilities.isEmpty()) {
                log.info("Found {} medical facilities from Geoapify API", facilities.size());
                return facilities;
            }
            log.warn("No medical facilities found from Geoapify, falling back to simulated data");
        } else {
            log.info("APIs not configured, using simulated medical facility data");
        }

        // Fallback to simulated data
        return getSimulatedMedicalFacilities(radiusMiles);
    }

    /**
     * Generate simulated medical facility data for when API is not available.
     */
    private List<ServiceLocation> getSimulatedMedicalFacilities(double radiusMiles) {
        List<ServiceLocation> medical = new ArrayList<>();

        String[][] hospitalData = {
            {"Emory University Hospital", "1364 Clifton Rd NE, Atlanta, GA", "+1 404-778-7777"},
            {"Grady Memorial Hospital", "80 Jesse Hill Jr Dr SE, Atlanta, GA", "+1 404-616-1000"},
            {"Northside Hospital", "1000 Johnson Ferry Rd NE, Atlanta, GA", "+1 404-851-8000"}
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
     * Uses TomTom Search API for real results with phone numbers.
     * Falls back to Geoapify, then simulated data if needed.
     */
    @McpTool(description = "Find nearby rental car locations for temporary transportation. " +
                        "Use ACCIDENT LOCATION coordinates - customer needs a rental near the crash site.")
    public List<ServiceLocation> findRentalCars(
            @McpToolParam(description = "Latitude of accident location")
            double latitude,

            @McpToolParam(description = "Longitude of accident location")
            double longitude
    ) {
        double radiusMiles = 10.0; // Default search radius for rentals

        // Try TomTom first (better phone number coverage)
        if (tomTomService.isConfigured()) {
            log.info("Finding rental car locations near ({}, {}) within {} miles using TomTom",
                    latitude, longitude, radiusMiles);

            List<ServiceLocation> rentals = tomTomService.findCarRentals(latitude, longitude, radiusMiles, 5);

            if (!rentals.isEmpty()) {
                log.info("Found {} rental car locations from TomTom API", rentals.size());
                return rentals;
            }
            log.warn("No rental car locations found from TomTom, trying Geoapify");
        }

        // Fallback to Geoapify
        if (geoapifyService.isConfigured()) {
            log.info("Finding rental car locations near ({}, {}) within {} miles using Geoapify",
                    latitude, longitude, radiusMiles);

            List<ServiceLocation> rentals = geoapifyService.findCarRentals(latitude, longitude, radiusMiles, 5);

            if (!rentals.isEmpty()) {
                log.info("Found {} rental car locations from Geoapify API", rentals.size());
                return rentals;
            }
            log.warn("No rental car locations found from Geoapify, falling back to simulated data");
        } else {
            log.info("APIs not configured, using simulated rental car data");
        }

        // Fallback to simulated data
        return getSimulatedRentalCars();
    }

    /**
     * Generate simulated rental car data for when API is not available.
     */
    private List<ServiceLocation> getSimulatedRentalCars() {
        List<ServiceLocation> rentals = new ArrayList<>();

        String[][] rentalData = {
            {"Enterprise Rent-A-Car", "2636 Piedmont Rd NE, Atlanta, GA", "+1 404-266-2277"},
            {"Hertz", "3400 Peachtree Rd NE, Atlanta, GA", "+1 800-654-3131"},
            {"Budget", "1888 Century Blvd NE, Atlanta, GA", "+1 800-218-7992"}
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
