package com.insurancemegacorp.crash.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Nearby services located by the Services agent.
 * Includes body shops, tow services, hospitals, and rental car locations.
 */
@JsonClassDescription("Nearby services relevant to the accident")
public record NearbyServices(
    @JsonPropertyDescription("Nearby body shops")
    List<ServiceLocation> bodyShops,

    @JsonPropertyDescription("Nearby tow services")
    List<ServiceLocation> towServices,

    @JsonPropertyDescription("Nearby hospitals or urgent care facilities")
    List<ServiceLocation> medicalFacilities,

    @JsonPropertyDescription("Nearby rental car locations")
    List<ServiceLocation> rentalCarLocations,

    @JsonPropertyDescription("Recommendation for immediate dispatch")
    String dispatchRecommendation,

    @JsonPropertyDescription("Whether the vehicle is likely drivable")
    boolean vehicleLikelyDrivable
) {
    @JsonClassDescription("A service location with contact information")
    public record ServiceLocation(
        @JsonPropertyDescription("Business name")
        String name,

        @JsonPropertyDescription("Street address")
        String address,

        @JsonPropertyDescription("Phone number")
        String phone,

        @JsonPropertyDescription("Distance from accident in miles")
        double distanceMiles,

        @JsonPropertyDescription("Average rating (1-5 stars)")
        Double rating,

        @JsonPropertyDescription("Estimated arrival time in minutes (for mobile services)")
        Integer etaMinutes,

        @JsonPropertyDescription("Whether this is a preferred/in-network provider")
        boolean isPreferred,

        @JsonPropertyDescription("Whether location is currently open")
        Boolean isOpen
    ) {}
}
