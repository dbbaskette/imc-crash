package com.insurancemegacorp.crash.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancemegacorp.crash.domain.NearbyServices.ServiceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for interacting with Geoapify Places API.
 * Used to find real nearby businesses like body shops, tow services, hospitals, etc.
 *
 * API Documentation: https://apidocs.geoapify.com/docs/places/
 * Free tier: 3,000 requests/day
 */
@Service
public class GeoapifyService {

    private static final Logger log = LoggerFactory.getLogger(GeoapifyService.class);
    private static final String PLACES_API_URL = "https://api.geoapify.com/v2/places";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${geoapify.api-key:}")
    private String apiKey;

    public GeoapifyService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if Geoapify API is configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Find nearby places by category.
     *
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusMeters Search radius in meters
     * @param categories Geoapify category filter (e.g., "service.vehicle.car_repair")
     * @param limit Maximum number of results
     * @return List of ServiceLocation objects
     */
    public List<ServiceLocation> findNearbyPlaces(
            double latitude,
            double longitude,
            int radiusMeters,
            String categories,
            int limit
    ) {
        if (!isConfigured()) {
            log.warn("Geoapify API key not configured, returning empty results");
            return List.of();
        }

        try {
            String url = String.format(
                    "%s?categories=%s&filter=circle:%f,%f,%d&limit=%d&apiKey=%s",
                    PLACES_API_URL,
                    categories,
                    longitude, latitude, radiusMeters,
                    limit,
                    apiKey
            );

            log.debug("Calling Geoapify Places API: categories={}, lat={}, lon={}, radius={}m",
                    categories, latitude, longitude, radiusMeters);

            String response = restTemplate.getForObject(url, String.class);
            return parseGeoapifyResponse(response, latitude, longitude);

        } catch (Exception e) {
            log.error("Error calling Geoapify API: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find nearby auto body shops / car repair services.
     * Uses Geoapify categories: service.vehicle.repair.car
     */
    public List<ServiceLocation> findBodyShops(double latitude, double longitude, double radiusMiles, int limit) {
        int radiusMeters = (int) (radiusMiles * 1609.34); // Convert miles to meters
        return findNearbyPlaces(latitude, longitude, radiusMeters,
                "service.vehicle.repair.car", limit);
    }

    /**
     * Find nearby tow services.
     * Note: Geoapify doesn't have a specific tow category, so we return empty
     * to trigger the simulated fallback data in NearbyServicesService.
     */
    public List<ServiceLocation> findTowServices(double latitude, double longitude, double radiusMiles, int limit) {
        // Geoapify doesn't have a towing category - return empty to trigger simulated fallback
        return List.of();
    }

    /**
     * Find nearby hospitals and medical facilities.
     * Uses Geoapify categories: healthcare.hospital
     */
    public List<ServiceLocation> findHospitals(double latitude, double longitude, double radiusMiles, int limit) {
        int radiusMeters = (int) (radiusMiles * 1609.34);
        return findNearbyPlaces(latitude, longitude, radiusMeters,
                "healthcare.hospital", limit);
    }

    /**
     * Find nearby car rental locations.
     * Uses Geoapify categories: rental.car
     */
    public List<ServiceLocation> findCarRentals(double latitude, double longitude, double radiusMiles, int limit) {
        int radiusMeters = (int) (radiusMiles * 1609.34);
        return findNearbyPlaces(latitude, longitude, radiusMeters,
                "rental.car", limit);
    }

    /**
     * Parse Geoapify API response into ServiceLocation objects.
     */
    private List<ServiceLocation> parseGeoapifyResponse(String response, double originLat, double originLon) {
        List<ServiceLocation> locations = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode features = root.get("features");

            if (features == null || !features.isArray()) {
                log.warn("No features found in Geoapify response");
                return locations;
            }

            for (JsonNode feature : features) {
                JsonNode properties = feature.get("properties");
                if (properties == null) continue;

                String name = getStringOrDefault(properties, "name", "Unknown Business");
                String address = buildAddress(properties);
                String phone = getStringOrDefault(properties, "contact:phone",
                        getStringOrDefault(properties, "phone", null));

                // Get coordinates for distance calculation
                JsonNode geometry = feature.get("geometry");
                double placeLat = originLat;
                double placeLon = originLon;
                if (geometry != null && geometry.has("coordinates")) {
                    JsonNode coords = geometry.get("coordinates");
                    if (coords.isArray() && coords.size() >= 2) {
                        placeLon = coords.get(0).asDouble();
                        placeLat = coords.get(1).asDouble();
                    }
                }

                // Calculate distance in miles
                double distanceMiles = calculateDistanceMiles(originLat, originLon, placeLat, placeLon);

                // Get rating if available (Geoapify may not always have this)
                Double rating = null;
                if (properties.has("rating")) {
                    rating = properties.get("rating").asDouble();
                }

                // Check if currently open
                Boolean isOpen = null;
                if (properties.has("opening_hours")) {
                    // Geoapify provides opening_hours, but parsing current status is complex
                    // For now, we'll leave it as null (unknown)
                    isOpen = null;
                }

                // Skip entries without a name
                if (name.equals("Unknown Business")) {
                    continue;
                }

                locations.add(new ServiceLocation(
                        name,
                        address,
                        phone,
                        Math.round(distanceMiles * 10.0) / 10.0, // Round to 1 decimal
                        rating,
                        null, // ETA not applicable for stationary locations
                        false, // isPreferred - could be enhanced with a preferred provider list
                        isOpen
                ));
            }

            log.info("Found {} locations from Geoapify", locations.size());

        } catch (Exception e) {
            log.error("Error parsing Geoapify response: {}", e.getMessage(), e);
        }

        return locations;
    }

    /**
     * Build a formatted address from Geoapify properties.
     */
    private String buildAddress(JsonNode properties) {
        StringBuilder address = new StringBuilder();

        String street = getStringOrDefault(properties, "street", null);
        String housenumber = getStringOrDefault(properties, "housenumber", null);
        String city = getStringOrDefault(properties, "city", null);
        String state = getStringOrDefault(properties, "state", null);
        String postcode = getStringOrDefault(properties, "postcode", null);

        if (housenumber != null) {
            address.append(housenumber).append(" ");
        }
        if (street != null) {
            address.append(street);
        }
        if (city != null) {
            if (address.length() > 0) address.append(", ");
            address.append(city);
        }
        if (state != null) {
            if (address.length() > 0) address.append(", ");
            address.append(state);
        }
        if (postcode != null) {
            if (address.length() > 0) address.append(" ");
            address.append(postcode);
        }

        // Fallback to formatted address if available
        if (address.length() == 0) {
            return getStringOrDefault(properties, "formatted", "Address not available");
        }

        return address.toString();
    }

    /**
     * Get a string value from JSON node or return default.
     */
    private String getStringOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            String value = node.get(field).asText();
            return value.isEmpty() ? defaultValue : value;
        }
        return defaultValue;
    }

    /**
     * Calculate distance between two points using Haversine formula.
     * Returns distance in miles.
     */
    private double calculateDistanceMiles(double lat1, double lon1, double lat2, double lon2) {
        final double R = 3958.8; // Earth's radius in miles

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
