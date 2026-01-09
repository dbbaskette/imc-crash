package com.insurancemegacorp.crash.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancemegacorp.crash.domain.NearbyServices.ServiceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for interacting with TomTom Search API.
 * Primary data source for all nearby services (body shops, tow trucks, hospitals, car rentals).
 * TomTom provides better phone number coverage than Geoapify.
 *
 * API Documentation: https://developer.tomtom.com/search-api/documentation/search-service/fuzzy-search
 * Free tier: 2,500 requests/day
 */
@Service
public class TomTomService {

    private static final Logger log = LoggerFactory.getLogger(TomTomService.class);

    // TomTom Search API endpoint (using fuzzy search for better phone number coverage)
    private static final String SEARCH_URL = "https://api.tomtom.com/search/2/search/%s.json";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tomtom.api-key:}")
    private String apiKey;

    public TomTomService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if TomTom API is configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Find nearby auto body shops / collision repair centers.
     * Uses free-text search for "auto body repair" which returns better results with phone numbers.
     */
    public List<ServiceLocation> findBodyShops(double latitude, double longitude, double radiusMiles, int limit) {
        return searchPlaces("auto body repair", latitude, longitude, radiusMiles, limit, false, "body shop");
    }

    /**
     * Find nearby tow truck services.
     * Uses free-text search for "tow truck" since category search (7317) returns poor results.
     */
    public List<ServiceLocation> findTowServices(double latitude, double longitude, double radiusMiles, int limit) {
        List<ServiceLocation> results = searchPlaces("tow truck", latitude, longitude, radiusMiles, limit, true, "tow service");

        // If no results, try alternative search terms
        if (results.isEmpty()) {
            results = searchPlaces("towing service", latitude, longitude, radiusMiles, limit, true, "tow service");
        }

        return results;
    }

    /**
     * Find nearby hospitals and medical facilities.
     * Uses free-text search for "hospital emergency" for better results.
     */
    public List<ServiceLocation> findHospitals(double latitude, double longitude, double radiusMiles, int limit) {
        return searchPlaces("hospital emergency", latitude, longitude, radiusMiles, limit, false, "hospital");
    }

    /**
     * Find nearby car rental locations.
     * Uses free-text search for "car rental" which returns major rental companies with phone numbers.
     */
    public List<ServiceLocation> findCarRentals(double latitude, double longitude, double radiusMiles, int limit) {
        return searchPlaces("car rental", latitude, longitude, radiusMiles, limit, false, "car rental");
    }

    /**
     * Generic place search using TomTom's fuzzy search API.
     * This provides better results and phone number coverage than category search.
     *
     * @param query Search query (e.g., "auto body repair", "tow truck")
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusMiles Search radius in miles
     * @param limit Maximum number of results
     * @param calculateEta Whether to calculate ETA (for mobile services like towing)
     * @param serviceType Type of service for logging
     * @return List of ServiceLocation objects
     */
    private List<ServiceLocation> searchPlaces(
            String query,
            double latitude,
            double longitude,
            double radiusMiles,
            int limit,
            boolean calculateEta,
            String serviceType
    ) {
        if (!isConfigured()) {
            log.warn("TomTom API key not configured, returning empty results for {}", serviceType);
            return List.of();
        }

        try {
            int radiusMeters = (int) (radiusMiles * 1609.34);
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

            // Use fuzzy search API for better results
            String url = String.format(
                    SEARCH_URL + "?key=%s&lat=%f&lon=%f&radius=%d&limit=%d&typeahead=false&language=en-US",
                    encodedQuery,
                    apiKey,
                    latitude, longitude,
                    radiusMeters,
                    limit
            );

            log.debug("Calling TomTom Search API for {}: query='{}', lat={}, lon={}, radius={}m",
                    serviceType, query, latitude, longitude, radiusMeters);

            String response = restTemplate.getForObject(url, String.class);
            List<ServiceLocation> locations = parseTomTomResponse(response, latitude, longitude, calculateEta, serviceType);

            log.info("Found {} {} from TomTom", locations.size(), serviceType + "s");
            return locations;

        } catch (Exception e) {
            log.error("Error calling TomTom API for {}: {}", serviceType, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Parse TomTom API response into ServiceLocation objects.
     */
    private List<ServiceLocation> parseTomTomResponse(
            String response,
            double originLat,
            double originLon,
            boolean calculateEta,
            String serviceType
    ) {
        List<ServiceLocation> locations = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            if (results == null || !results.isArray()) {
                log.debug("No results found in TomTom response for {}", serviceType);
                return locations;
            }

            for (JsonNode result : results) {
                // Get POI info
                JsonNode poi = result.get("poi");
                if (poi == null) continue;

                String name = getStringOrDefault(poi, "name", null);

                // Skip entries without a name
                if (name == null || name.isBlank()) {
                    continue;
                }

                // Get phone number if available
                String phone = null;
                if (poi.has("phone") && !poi.get("phone").isNull()) {
                    phone = poi.get("phone").asText();
                    if (phone.isBlank()) phone = null;
                }

                // Get address
                JsonNode address = result.get("address");
                String formattedAddress = null;
                if (address != null) {
                    formattedAddress = getStringOrDefault(address, "freeformAddress", null);
                }

                // Get position for distance calculation
                JsonNode position = result.get("position");
                double placeLat = originLat;
                double placeLon = originLon;
                if (position != null) {
                    placeLat = position.get("lat").asDouble();
                    placeLon = position.get("lon").asDouble();
                }

                // Calculate distance in miles
                double distanceMiles = calculateDistanceMiles(originLat, originLon, placeLat, placeLon);

                // Calculate ETA for mobile services (towing)
                // Estimate: ~3 minutes per mile + 5 min dispatch time
                Integer eta = calculateEta ? (int) (distanceMiles * 3 + 5) : null;

                locations.add(new ServiceLocation(
                        name,
                        formattedAddress,
                        phone,
                        Math.round(distanceMiles * 10.0) / 10.0, // Round to 1 decimal
                        null, // TomTom doesn't provide ratings in search results
                        eta,
                        false, // isPreferred - could be enhanced with preferred provider list
                        true   // Assume open (TomTom has opening hours but requires additional parsing)
                ));
            }

        } catch (Exception e) {
            log.error("Error parsing TomTom response for {}: {}", serviceType, e.getMessage(), e);
        }

        return locations;
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
