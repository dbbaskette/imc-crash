package com.insurancemegacorp.crash.environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancemegacorp.crash.domain.EnvironmentContext;
import com.insurancemegacorp.crash.domain.EnvironmentContext.RoadConditions;
import com.insurancemegacorp.crash.domain.EnvironmentContext.WeatherConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MCP Tools for gathering environmental context for accident locations.
 * Uses Open-Meteo API for real weather data (free, no API key required).
 */
@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    // Open-Meteo WMO Weather Codes mapping
    private static final java.util.Map<Integer, String> WEATHER_CODES = java.util.Map.ofEntries(
        java.util.Map.entry(0, "Clear"),
        java.util.Map.entry(1, "Mainly Clear"),
        java.util.Map.entry(2, "Partly Cloudy"),
        java.util.Map.entry(3, "Overcast"),
        java.util.Map.entry(45, "Fog"),
        java.util.Map.entry(48, "Depositing Rime Fog"),
        java.util.Map.entry(51, "Light Drizzle"),
        java.util.Map.entry(53, "Moderate Drizzle"),
        java.util.Map.entry(55, "Dense Drizzle"),
        java.util.Map.entry(56, "Freezing Drizzle"),
        java.util.Map.entry(57, "Dense Freezing Drizzle"),
        java.util.Map.entry(61, "Light Rain"),
        java.util.Map.entry(63, "Moderate Rain"),
        java.util.Map.entry(65, "Heavy Rain"),
        java.util.Map.entry(66, "Light Freezing Rain"),
        java.util.Map.entry(67, "Heavy Freezing Rain"),
        java.util.Map.entry(71, "Light Snow"),
        java.util.Map.entry(73, "Moderate Snow"),
        java.util.Map.entry(75, "Heavy Snow"),
        java.util.Map.entry(77, "Snow Grains"),
        java.util.Map.entry(80, "Light Rain Showers"),
        java.util.Map.entry(81, "Moderate Rain Showers"),
        java.util.Map.entry(82, "Violent Rain Showers"),
        java.util.Map.entry(85, "Light Snow Showers"),
        java.util.Map.entry(86, "Heavy Snow Showers"),
        java.util.Map.entry(95, "Thunderstorm"),
        java.util.Map.entry(96, "Thunderstorm with Light Hail"),
        java.util.Map.entry(99, "Thunderstorm with Heavy Hail")
    );

    /**
     * Get weather conditions at a specific location and time using Open-Meteo API.
     */
    @McpTool(description = "Get weather conditions at the accident location and time. " +
                        "Returns temperature, visibility, wind speed, and precipitation status.")
    public WeatherConditions getWeather(
            @McpToolParam(description = "Latitude coordinate")
            double latitude,

            @McpToolParam(description = "Longitude coordinate")
            double longitude,

            @McpToolParam(description = "ISO 8601 timestamp of the event")
            String timestamp
    ) {
        try {
            Instant eventTime = Instant.parse(timestamp);
            ZonedDateTime zdt = eventTime.atZone(ZoneId.of("America/New_York"));
            LocalDate date = zdt.toLocalDate();
            int hour = zdt.getHour();

            // Determine if we need historical or forecast API
            LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
            String url;

            if (date.isBefore(today.minusDays(5))) {
                // Use archive API for historical data
                url = String.format(
                    "https://archive-api.open-meteo.com/v1/archive?latitude=%f&longitude=%f" +
                    "&start_date=%s&end_date=%s" +
                    "&hourly=temperature_2m,weather_code,wind_speed_10m,visibility" +
                    "&temperature_unit=fahrenheit&wind_speed_unit=mph",
                    latitude, longitude, date, date
                );
            } else {
                // Use forecast API for recent/current data with 1 day history
                url = String.format(
                    "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f" +
                    "&hourly=temperature_2m,weather_code,wind_speed_10m,visibility,precipitation" +
                    "&temperature_unit=fahrenheit&wind_speed_unit=mph" +
                    "&past_days=1&forecast_days=1",
                    latitude, longitude
                );
            }

            log.info("Fetching weather from Open-Meteo: lat={}, lon={}, date={}", latitude, longitude, date);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode hourly = root.path("hourly");

            // Find the index for the requested hour
            JsonNode times = hourly.path("time");
            int targetIndex = -1;
            String targetPrefix = date + "T" + String.format("%02d", hour);

            for (int i = 0; i < times.size(); i++) {
                if (times.get(i).asText().startsWith(targetPrefix)) {
                    targetIndex = i;
                    break;
                }
            }

            if (targetIndex == -1) {
                log.warn("Could not find exact hour, using first available");
                targetIndex = 0;
            }

            double temperature = hourly.path("temperature_2m").get(targetIndex).asDouble();
            int weatherCode = hourly.path("weather_code").get(targetIndex).asInt();
            double windSpeed = hourly.path("wind_speed_10m").get(targetIndex).asDouble();
            double visibilityMeters = hourly.path("visibility").get(targetIndex).asDouble(16000);
            double visibilityMiles = visibilityMeters / 1609.34;

            String conditions = WEATHER_CODES.getOrDefault(weatherCode, "Unknown");
            String precipitation = determinePrecipitation(weatherCode);

            log.info("Weather result: conditions={}, temp={}°F, wind={} mph, visibility={} mi",
                    conditions, temperature, windSpeed, visibilityMiles);

            return new WeatherConditions(
                conditions,
                Math.round(temperature * 10) / 10.0,
                Math.round(visibilityMiles * 10) / 10.0,
                Math.round(windSpeed * 10) / 10.0,
                precipitation
            );

        } catch (Exception e) {
            log.error("Failed to fetch weather from Open-Meteo, using fallback: {}", e.getMessage());
            return getFallbackWeather();
        }
    }

    private String determinePrecipitation(int weatherCode) {
        if (weatherCode >= 51 && weatherCode <= 67) {
            return "Rain";
        } else if (weatherCode >= 71 && weatherCode <= 86) {
            return "Snow";
        } else if (weatherCode >= 95) {
            return "Thunderstorm";
        }
        return null;
    }

    private WeatherConditions getFallbackWeather() {
        return new WeatherConditions(
            "Unknown (API unavailable)",
            55.0,
            10.0,
            5.0,
            null
        );
    }

    /**
     * Analyze weather conditions for the 24 hours prior to the accident.
     * Returns summary of significant weather that may have affected road conditions.
     */
    @McpTool(description = "Analyze weather for the 24 hours before the accident. " +
                        "Returns summary of precipitation, temperature extremes, and adverse conditions.")
    public String getPrior24HourWeather(
            @McpToolParam(description = "Latitude coordinate")
            double latitude,

            @McpToolParam(description = "Longitude coordinate")
            double longitude,

            @McpToolParam(description = "ISO 8601 timestamp of the event")
            String timestamp
    ) {
        try {
            Instant eventTime = Instant.parse(timestamp);
            ZonedDateTime zdt = eventTime.atZone(ZoneId.of("America/New_York"));
            LocalDate date = zdt.toLocalDate();

            // Fetch weather with past_days=1 to get yesterday's data
            String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f" +
                "&hourly=temperature_2m,weather_code,precipitation" +
                "&temperature_unit=fahrenheit" +
                "&past_days=1&forecast_days=1",
                latitude, longitude
            );

            log.info("Fetching 24hr weather history from Open-Meteo: lat={}, lon={}", latitude, longitude);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode hourly = root.path("hourly");
            JsonNode times = hourly.path("time");

            // Find the accident time index
            String targetPrefix = date + "T" + String.format("%02d", zdt.getHour());
            int accidentIndex = -1;
            for (int i = 0; i < times.size(); i++) {
                if (times.get(i).asText().startsWith(targetPrefix)) {
                    accidentIndex = i;
                    break;
                }
            }

            if (accidentIndex == -1 || accidentIndex < 24) {
                return "Prior 24-hour weather data unavailable";
            }

            // Analyze the 24 hours before the accident
            int startIndex = accidentIndex - 24;
            int endIndex = accidentIndex;

            double totalPrecip = 0.0;
            int snowHours = 0;
            int rainHours = 0;
            int freezingHours = 0;
            double minTemp = 999;
            double maxTemp = -999;
            boolean hadThunderstorm = false;

            for (int i = startIndex; i < endIndex; i++) {
                double precip = hourly.path("precipitation").get(i).asDouble(0);
                int weatherCode = hourly.path("weather_code").get(i).asInt(0);
                double temp = hourly.path("temperature_2m").get(i).asDouble();

                totalPrecip += precip;
                minTemp = Math.min(minTemp, temp);
                maxTemp = Math.max(maxTemp, temp);

                // Count hours with significant weather
                if (weatherCode >= 71 && weatherCode <= 86) {
                    snowHours++;
                } else if (weatherCode >= 51 && weatherCode <= 67) {
                    rainHours++;
                }

                if (temp <= 32) {
                    freezingHours++;
                }

                if (weatherCode >= 95) {
                    hadThunderstorm = true;
                }
            }

            // Build summary
            List<String> summary = new ArrayList<>();

            if (totalPrecip > 0.5) {
                summary.add(String.format("Heavy precipitation: %.1f inches", totalPrecip));
            } else if (totalPrecip > 0.1) {
                summary.add(String.format("Light precipitation: %.1f inches", totalPrecip));
            }

            if (snowHours > 0) {
                summary.add(String.format("Snow for %d hours", snowHours));
            }

            if (rainHours > 4) {
                summary.add(String.format("Rain for %d hours", rainHours));
            }

            if (freezingHours > 6) {
                summary.add(String.format("Freezing temperatures for %d hours", freezingHours));
            }

            if (hadThunderstorm) {
                summary.add("Thunderstorm activity");
            }

            if (minTemp < 32 && maxTemp > 32) {
                summary.add(String.format("Temperature fluctuation across freezing point (%d°F to %d°F)",
                    Math.round(minTemp), Math.round(maxTemp)));
            }

            if (summary.isEmpty()) {
                return String.format("Clear conditions for prior 24 hours (Temp: %d°F to %d°F)",
                    Math.round(minTemp), Math.round(maxTemp));
            }

            String result = "Prior 24 hours: " + String.join("; ", summary);
            log.info("24hr weather summary: {}", result);
            return result;

        } catch (Exception e) {
            log.error("Failed to fetch 24hr weather history: {}", e.getMessage());
            return "Prior 24-hour weather data unavailable";
        }
    }

    /**
     * Reverse geocode coordinates to get a street address.
     * Uses OpenStreetMap Nominatim API (free, no API key required).
     */
    @McpTool(description = "Convert GPS coordinates to a street address. " +
                        "Returns full address, road type, and nearest intersection.")
    public LocationDetails reverseGeocode(
            @McpToolParam(description = "Latitude coordinate")
            double latitude,

            @McpToolParam(description = "Longitude coordinate")
            double longitude
    ) {
        try {
            // Use OpenStreetMap Nominatim API for real reverse geocoding
            String url = String.format(
                "https://nominatim.openstreetmap.org/reverse?lat=%f&lon=%f&format=json&addressdetails=1",
                latitude, longitude
            );

            log.info("Reverse geocoding via Nominatim: lat={}, lon={}", latitude, longitude);

            // Nominatim requires a User-Agent header
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "CRASH-Insurance-Claims-System/1.0");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, entity, String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());

            // Extract the display name (full address)
            String displayName = root.path("display_name").asText("Unknown location");

            // Extract address components
            JsonNode address = root.path("address");
            String houseNumber = address.path("house_number").asText("");
            String road = address.path("road").asText(address.path("street").asText(""));
            String city = address.path("city").asText(
                address.path("town").asText(
                    address.path("village").asText(
                        address.path("municipality").asText(""))));
            String state = address.path("state").asText("");
            String postcode = address.path("postcode").asText("");

            // Build a clean address
            StringBuilder cleanAddress = new StringBuilder();
            if (!houseNumber.isEmpty()) {
                cleanAddress.append(houseNumber).append(" ");
            }
            if (!road.isEmpty()) {
                cleanAddress.append(road);
            }
            if (!city.isEmpty()) {
                if (cleanAddress.length() > 0) cleanAddress.append(", ");
                cleanAddress.append(city);
            }
            if (!state.isEmpty()) {
                if (cleanAddress.length() > 0) cleanAddress.append(", ");
                cleanAddress.append(state);
            }
            if (!postcode.isEmpty()) {
                if (cleanAddress.length() > 0) cleanAddress.append(" ");
                cleanAddress.append(postcode);
            }

            String finalAddress = cleanAddress.length() > 0 ? cleanAddress.toString() : displayName;

            // Determine road type from OSM class/type
            String osmClass = root.path("class").asText("");
            String osmType = root.path("type").asText("");
            String roadType = determineRoadType(osmClass, osmType, road);

            // For intersection, use the road name (we don't have intersection data from Nominatim)
            String intersection = road.isEmpty() ? null : "Near " + road;

            log.info("Geocode result: address={}, roadType={}", finalAddress, roadType);

            return new LocationDetails(finalAddress, roadType, intersection);

        } catch (Exception e) {
            log.error("Failed to reverse geocode via Nominatim: {}", e.getMessage());
            return getFallbackLocation(latitude, longitude);
        }
    }

    /**
     * Determine road type from OSM classification.
     * OSM highway types: https://wiki.openstreetmap.org/wiki/Key:highway
     */
    private String determineRoadType(String osmClass, String osmType, String roadName) {
        // Check road name for hints first
        String roadLower = roadName.toLowerCase();
        if (roadLower.contains("interstate") || roadLower.contains("i-") || roadLower.contains("freeway")) {
            return "Interstate/Freeway";
        }
        if (roadLower.contains("highway") || roadLower.contains("hwy") || roadLower.contains("us-") || roadLower.contains("state route")) {
            return "Highway";
        }
        if (roadLower.contains("expressway") || roadLower.contains("parkway") || roadLower.contains("turnpike")) {
            return "Expressway";
        }
        if (roadLower.contains("boulevard") || roadLower.contains("blvd")) {
            return "Boulevard";
        }
        if (roadLower.contains("avenue") || roadLower.contains("ave")) {
            return "Avenue";
        }

        // Use OSM highway type classification
        // Major roads - high speed, limited access
        if (osmType.equals("motorway")) {
            return "Interstate/Freeway";
        }
        if (osmType.equals("motorway_link")) {
            return "Freeway on/off ramp";
        }
        if (osmType.equals("trunk")) {
            return "Highway";
        }
        if (osmType.equals("trunk_link")) {
            return "Highway ramp";
        }

        // Urban arterials - major city roads
        if (osmType.equals("primary")) {
            return "Primary arterial";
        }
        if (osmType.equals("primary_link")) {
            return "Primary arterial ramp";
        }
        if (osmType.equals("secondary")) {
            return "Secondary arterial";
        }
        if (osmType.equals("secondary_link")) {
            return "Secondary arterial ramp";
        }

        // Collector roads - connect residential to arterials
        if (osmType.equals("tertiary")) {
            return "Collector road";
        }
        if (osmType.equals("tertiary_link")) {
            return "Collector road ramp";
        }

        // Local roads
        if (osmType.equals("unclassified")) {
            return "Local road";
        }
        if (osmType.equals("residential")) {
            return "Residential street";
        }
        if (osmType.equals("living_street")) {
            return "Residential (shared space)";
        }

        // Service and access roads
        if (osmType.equals("service")) {
            return "Service/Access road";
        }
        if (osmType.equals("track")) {
            return "Unpaved track";
        }

        // Parking and driveways
        if (osmType.equals("parking_aisle")) {
            return "Parking lot";
        }
        if (osmType.equals("driveway")) {
            return "Private driveway";
        }

        // Pedestrian and bike areas (accident may have occurred here)
        if (osmType.equals("pedestrian")) {
            return "Pedestrian area";
        }
        if (osmType.equals("cycleway")) {
            return "Bike path";
        }
        if (osmType.equals("footway") || osmType.equals("path")) {
            return "Footpath";
        }

        // Other OSM place types (when coordinates land on a building/place, not a road)
        if (osmClass.equals("building")) {
            return "Building/Structure";
        }
        if (osmClass.equals("amenity")) {
            return "Commercial/Public facility";
        }
        if (osmClass.equals("shop")) {
            return "Commercial area";
        }
        if (osmClass.equals("place")) {
            return "General area";
        }
        if (osmClass.equals("landuse")) {
            return osmType.equals("residential") ? "Residential area" : "Land use area";
        }

        // Generic highway class (catch-all for road types)
        if (osmClass.equals("highway")) {
            return "Road";
        }

        // Default
        return "Unknown (" + osmClass + "/" + osmType + ")";
    }

    /**
     * Fallback location when geocoding fails.
     */
    private LocationDetails getFallbackLocation(double latitude, double longitude) {
        String address = String.format("GPS: %.4f, %.4f (geocoding unavailable)", latitude, longitude);
        return new LocationDetails(address, "Unknown", null);
    }

    /**
     * Get road conditions at a location.
     * Infers conditions based on weather data.
     */
    @McpTool(description = "Get road surface conditions at the accident location. " +
                        "Returns surface condition, speed limit, lane count, and construction status.")
    public RoadConditions getRoadConditions(
            @McpToolParam(description = "Latitude coordinate")
            double latitude,

            @McpToolParam(description = "Longitude coordinate")
            double longitude,

            @McpToolParam(description = "Current weather conditions for inference")
            String weatherCondition
    ) {
        // Infer road surface from weather
        String surface;
        if (weatherCondition.toLowerCase().contains("snow")) {
            surface = "Snow-covered";
        } else if (weatherCondition.toLowerCase().contains("rain") ||
                   weatherCondition.toLowerCase().contains("drizzle")) {
            surface = "Wet";
        } else if (weatherCondition.toLowerCase().contains("freezing") ||
                   weatherCondition.toLowerCase().contains("ice")) {
            surface = "Icy";
        } else {
            surface = "Dry";
        }

        // Simulated road attributes
        // Note: Speed limit comes from telemetry device data (shown in Impact Analysis)
        // We use 0 here to indicate "not determined by environment service"
        int speedLimit = 0;
        int lanes = random.nextBoolean() ? 2 : 4;
        boolean construction = random.nextDouble() < 0.1; // 10% chance

        return new RoadConditions(surface, speedLimit, lanes, construction);
    }

    /**
     * Get complete environmental context combining all data sources.
     */
    @McpTool(description = "Get complete environmental context for an accident. " +
                        "Combines weather, location, and road conditions into a comprehensive report.")
    public EnvironmentContext getFullEnvironmentContext(
            @McpToolParam(description = "Latitude coordinate")
            double latitude,

            @McpToolParam(description = "Longitude coordinate")
            double longitude,

            @McpToolParam(description = "ISO 8601 timestamp of the event")
            String timestamp
    ) {
        LocationDetails location = reverseGeocode(latitude, longitude);
        WeatherConditions weather = getWeather(latitude, longitude, timestamp);
        RoadConditions road = getRoadConditions(latitude, longitude, weather.conditions());
        String prior24hr = getPrior24HourWeather(latitude, longitude, timestamp);

        // Determine daylight status
        Instant eventTime = Instant.parse(timestamp);
        LocalTime localTime = eventTime.atZone(ZoneId.of("America/New_York")).toLocalTime();
        String daylight;
        if (localTime.isAfter(LocalTime.of(6, 30)) && localTime.isBefore(LocalTime.of(8, 0))) {
            daylight = "Dawn";
        } else if (localTime.isAfter(LocalTime.of(8, 0)) && localTime.isBefore(LocalTime.of(17, 30))) {
            daylight = "Daylight";
        } else if (localTime.isAfter(LocalTime.of(17, 30)) && localTime.isBefore(LocalTime.of(19, 30))) {
            daylight = "Dusk";
        } else {
            daylight = "Night";
        }

        // Identify contributing factors
        List<String> factors = new ArrayList<>();
        if (road.surfaceCondition().equals("Wet") || road.surfaceCondition().equals("Icy") ||
            road.surfaceCondition().equals("Snow-covered")) {
            factors.add("Slippery road surface (" + road.surfaceCondition() + ")");
        }
        if (weather.visibilityMiles() < 5) {
            factors.add("Reduced visibility (" + weather.visibilityMiles() + " miles)");
        }
        if (weather.conditions().toLowerCase().contains("fog")) {
            factors.add("Fog conditions");
        }
        if (daylight.equals("Night") || daylight.equals("Dusk") || daylight.equals("Dawn")) {
            factors.add("Low light conditions (" + daylight + ")");
        }
        if (road.constructionZone()) {
            factors.add("Construction zone");
        }
        if (weather.windSpeedMph() > 20) {
            factors.add("High winds (" + weather.windSpeedMph() + " mph)");
        }
        if (weather.precipitation() != null) {
            factors.add("Active precipitation (" + weather.precipitation() + ")");
        }

        // Add prior 24-hour weather to contributing factors if significant
        if (prior24hr != null && !prior24hr.contains("unavailable") && !prior24hr.contains("Clear conditions")) {
            // Extract key concerns from 24hr summary
            if (prior24hr.contains("Heavy precipitation") || prior24hr.contains("Snow for")) {
                factors.add("Recent adverse weather in past 24 hours may have left residual hazards");
            } else if (prior24hr.contains("Light precipitation") && road.surfaceCondition().equals("Wet")) {
                factors.add("Prior precipitation may have contributed to wet road surface");
            }
            if (prior24hr.contains("Temperature fluctuation across freezing")) {
                factors.add("Recent freeze/thaw cycles may have created ice patches");
            }
        }

        log.info("Environment context generated: weather={}, road={}, factors={}, 24hr={}",
                weather.conditions(), road.surfaceCondition(), factors.size(),
                prior24hr != null ? prior24hr.substring(0, Math.min(50, prior24hr.length())) : "none");

        return new EnvironmentContext(
            location.address(),
            location.roadType(),
            location.intersection(),
            weather,
            road,
            factors,
            daylight,
            prior24hr
        );
    }

    /**
     * Internal record for location details from geocoding.
     */
    public record LocationDetails(
        String address,
        String roadType,
        String intersection
    ) {}
}
