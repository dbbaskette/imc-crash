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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Uses Google Gemini LLM for intelligent road surface condition assessment.
 */
@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    @Autowired(required = false)
    private ChatModel chatModel;

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
     * Get detailed hourly weather data for the last 12 hours.
     * Returns structured data optimized for LLM road condition assessment including:
     * - Black ice detection
     * - Wet road drying estimation (using humidity)
     * - Hydroplaning risk assessment
     */
    private String getHourlyWeatherForLlm(double latitude, double longitude, String timestamp) {
        try {
            Instant eventTime = Instant.parse(timestamp);
            ZonedDateTime zdt = eventTime.atZone(ZoneId.of("America/New_York"));
            LocalDate date = zdt.toLocalDate();

            // Include relative_humidity_2m for drying assessment
            String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f" +
                "&hourly=temperature_2m,weather_code,precipitation,relative_humidity_2m" +
                "&temperature_unit=fahrenheit" +
                "&past_days=1&forecast_days=1",
                latitude, longitude
            );

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

            if (accidentIndex == -1 || accidentIndex < 12) {
                return "Hourly data unavailable";
            }

            // Build detailed hourly report for last 12 hours (most recent first)
            StringBuilder sb = new StringBuilder();
            sb.append("HOURLY CONDITIONS (last 12 hours, most recent first):\n");

            for (int i = accidentIndex - 1; i >= accidentIndex - 12 && i >= 0; i--) {
                int hoursAgo = accidentIndex - i;
                double temp = hourly.path("temperature_2m").get(i).asDouble();
                int weatherCode = hourly.path("weather_code").get(i).asInt(0);
                double precip = hourly.path("precipitation").get(i).asDouble(0);
                int humidity = hourly.path("relative_humidity_2m").get(i).asInt(50);

                String weatherDesc = WEATHER_CODES.getOrDefault(weatherCode, "Unknown");
                String precipStr = precip > 0 ? String.format(", Precip: %.2f\"", precip) : "";

                sb.append(String.format("  %d hr ago: %.0f°F, %d%% humidity, %s%s\n",
                    hoursAgo, temp, humidity, weatherDesc, precipStr));
            }

            // Add key observations for the LLM
            sb.append("\nKEY OBSERVATIONS:\n");

            // Find when precipitation last occurred
            int lastPrecipHoursAgo = -1;
            double lastPrecipTemp = 0;
            for (int i = accidentIndex - 1; i >= accidentIndex - 12 && i >= 0; i--) {
                double precip = hourly.path("precipitation").get(i).asDouble(0);
                if (precip > 0) {
                    lastPrecipHoursAgo = accidentIndex - i;
                    lastPrecipTemp = hourly.path("temperature_2m").get(i).asDouble();
                    break;
                }
            }

            if (lastPrecipHoursAgo > 0) {
                sb.append(String.format("  - Last precipitation: %d hours ago at %.0f°F\n",
                    lastPrecipHoursAgo, lastPrecipTemp));
            } else {
                sb.append("  - No precipitation in last 12 hours\n");
            }

            // Check for temperature crossing 32°F (freeze point)
            boolean crossedFreezing = false;
            double currentTemp = hourly.path("temperature_2m").get(accidentIndex).asDouble();
            for (int i = accidentIndex - 12; i < accidentIndex; i++) {
                if (i >= 0) {
                    double temp = hourly.path("temperature_2m").get(i).asDouble();
                    if ((temp > 32 && currentTemp <= 32) || (temp <= 32 && currentTemp > 32)) {
                        crossedFreezing = true;
                        break;
                    }
                }
            }

            if (crossedFreezing) {
                sb.append("  - ⚠ Temperature crossed 32°F freezing point in last 12 hours\n");
            }

            // Check for recent warm-to-cold transition with prior moisture
            if (lastPrecipHoursAgo > 0 && lastPrecipTemp > 32 && currentTemp <= 32) {
                sb.append("  - ⚠ BLACK ICE RISK: Precipitation at %.0f°F followed by temp drop to %.0f°F\n"
                    .formatted(lastPrecipTemp, currentTemp));
            }

            // Analyze humidity trend for drying assessment
            int currentHumidity = hourly.path("relative_humidity_2m").get(accidentIndex).asInt(50);
            sb.append(String.format("  - Current humidity: %d%%\n", currentHumidity));

            // Check if roads had time to dry after precipitation
            if (lastPrecipHoursAgo > 0 && lastPrecipHoursAgo <= 12) {
                // Calculate average humidity since precipitation
                int humiditySum = 0;
                int humidityCount = 0;
                for (int i = accidentIndex - lastPrecipHoursAgo; i < accidentIndex; i++) {
                    if (i >= 0) {
                        humiditySum += hourly.path("relative_humidity_2m").get(i).asInt(50);
                        humidityCount++;
                    }
                }
                int avgHumiditySincePrecip = humidityCount > 0 ? humiditySum / humidityCount : 50;

                if (avgHumiditySincePrecip >= 80) {
                    sb.append(String.format("  - ⚠ HIGH HUMIDITY (%d%% avg) since precipitation - roads likely still wet\n",
                        avgHumiditySincePrecip));
                } else if (avgHumiditySincePrecip >= 60) {
                    sb.append(String.format("  - Moderate humidity (%d%% avg) since precipitation - roads may still be damp\n",
                        avgHumiditySincePrecip));
                } else if (currentTemp > 50) {
                    sb.append(String.format("  - Low humidity (%d%% avg) + warm temps - roads likely dry\n",
                        avgHumiditySincePrecip));
                }
            }

            // Check for dew/condensation risk (high humidity at night with dropping temps)
            if (currentHumidity >= 90 && currentTemp < 60) {
                sb.append("  - ⚠ DEW/CONDENSATION RISK: High humidity with cool temperatures\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("Failed to fetch hourly weather for LLM: {}", e.getMessage());
            return "Hourly data unavailable";
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
     * Get road conditions at a location using LLM-based assessment.
     * Intelligently infers surface conditions based on current weather, hourly history, and temperature patterns.
     */
    @McpTool(description = "Get road surface conditions at the accident location. " +
                        "Uses AI to assess surface hazards based on weather patterns.")
    public RoadConditions getRoadConditions(
            @McpToolParam(description = "Latitude coordinate")
            double latitude,

            @McpToolParam(description = "Longitude coordinate")
            double longitude,

            @McpToolParam(description = "Current weather conditions")
            String currentWeather,

            @McpToolParam(description = "Current temperature in Fahrenheit")
            double temperatureF,

            @McpToolParam(description = "24-hour weather history summary")
            String prior24HourWeather,

            @McpToolParam(description = "Road type from geocoding (e.g., Interstate/Freeway, Primary arterial, Residential street)")
            String roadType,

            @McpToolParam(description = "ISO 8601 timestamp for hourly weather lookup")
            String timestamp
    ) {
        // Get detailed hourly weather for LLM
        String hourlyWeather = getHourlyWeatherForLlm(latitude, longitude, timestamp);

        // Try LLM-based assessment first
        if (chatModel != null) {
            try {
                return assessRoadConditionsWithLlm(currentWeather, temperatureF, hourlyWeather, roadType);
            } catch (Exception e) {
                log.warn("LLM road assessment failed, using fallback: {}", e.getMessage());
            }
        }

        // Fallback to rule-based assessment (uses summary, not hourly)
        return assessRoadConditionsFallback(currentWeather, temperatureF, prior24HourWeather, roadType);
    }

    /**
     * Use LLM to intelligently assess road surface conditions with detailed hourly data.
     */
    private RoadConditions assessRoadConditionsWithLlm(
            String currentWeather,
            double temperatureF,
            String hourlyWeatherData,
            String roadType
    ) {
        String prompt = String.format("""
            You are a road safety analyst specializing in detecting hazardous road conditions.
            Based on the weather data including humidity, assess the current road surface condition.

            CURRENT CONDITIONS:
            - Weather: %s
            - Temperature: %.1f°F

            %s

            ROAD TYPE: %s

            Analyze the hourly temperature, precipitation, and humidity patterns carefully.
            Respond with EXACTLY this format (no other text):
            SURFACE: [one of: Dry, Wet, Damp, Icy, Snow-covered, Black Ice Risk, Slushy, Standing Water, Slick]
            LANES: [estimated number of lanes based on road type, integer only]
            REASON: [brief 10-15 word explanation of surface assessment]

            BLACK ICE DETECTION RULES (critical for safety):
            - If precipitation occurred when temp was above 32°F, then temp dropped below 32°F = BLACK ICE RISK
            - Recent rain (1-6 hours ago) + current temp ≤32°F = HIGH BLACK ICE RISK
            - Bridges and overpasses freeze first even at 33-35°F with moisture
            - Temperature crossing 32°F with any prior moisture = potential ice formation

            HUMIDITY AND DRYING ASSESSMENT:
            - High humidity (>80%%) after rain = roads stay wet longer, report as Wet
            - Low humidity (<50%%) + warm temps (>60°F) + no recent precip = roads dry quickly, report as Dry
            - Moderate humidity (50-80%%) + several hours since rain = Damp (reduced traction but not standing water)
            - High humidity (>90%%) at night with cool temps = DEW forming, report as Slick or Damp
            - Rain stopped 2-4 hours ago + high humidity = still Wet
            - Rain stopped 6+ hours ago + low humidity + warm = likely Dry

            OTHER WET ROAD CONDITIONS:
            - Active rain/drizzle = Wet
            - Heavy recent rain (<2 hrs) = Standing Water or Wet (hydroplaning risk)
            - Light rain stopped recently + high humidity = Damp
            - Snow in last few hours + above freezing now = Slushy
            - Active snow = Snow-covered
            - Extended freezing + any precipitation = Icy

            SLICK ASSESSMENT (use when roads have reduced traction without standing water):
            - Oil on roads from first rain after dry spell = Slick
            - Dew or frost formation from humidity = Slick
            - Leaf litter + moisture = Slick

            LANE ESTIMATION:
            - Interstate/Freeway: 6 lanes
            - Highway/Primary arterial: 4 lanes
            - Secondary/Collector: 2-4 lanes
            - Residential: 2 lanes
            """,
            currentWeather, temperatureF, hourlyWeatherData, roadType
        );

        log.info("Requesting LLM road surface assessment for roadType={}, weather={}, temp={}°F",
                roadType, currentWeather, temperatureF);

        String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
        log.debug("LLM response: {}", response);

        // Parse the structured response
        String surface = "Dry";
        int lanes = estimateLanesFromRoadType(roadType);
        String reason = "Based on current conditions";

        for (String line : response.split("\n")) {
            line = line.trim();
            if (line.startsWith("SURFACE:")) {
                String parsedSurface = line.substring("SURFACE:".length()).trim();
                // Validate against known surface conditions
                if (isValidSurfaceCondition(parsedSurface)) {
                    surface = parsedSurface;
                }
            } else if (line.startsWith("LANES:")) {
                try {
                    lanes = Integer.parseInt(line.substring("LANES:".length()).trim().replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    // Keep default
                }
            } else if (line.startsWith("REASON:")) {
                reason = line.substring("REASON:".length()).trim();
            }
        }

        boolean construction = random.nextDouble() < 0.1; // 10% chance (still simulated)

        log.info("LLM road assessment: surface={}, lanes={}, reason={}", surface, lanes, reason);

        return new RoadConditions(surface, lanes, construction, reason);
    }

    /**
     * Fallback rule-based road condition assessment when LLM is unavailable.
     */
    private RoadConditions assessRoadConditionsFallback(
            String currentWeather,
            double temperatureF,
            String prior24HourWeather,
            String roadType
    ) {
        String surface;
        String reason;
        String weatherLower = currentWeather.toLowerCase();
        String priorLower = prior24HourWeather != null ? prior24HourWeather.toLowerCase() : "";

        // Check for black ice conditions first (most dangerous)
        if (temperatureF <= 32 && (priorLower.contains("rain") || priorLower.contains("precipitation"))) {
            surface = "Black Ice Risk";
            reason = "Prior precipitation with current freezing temperatures";
        }
        // Current snow
        else if (weatherLower.contains("snow")) {
            surface = "Snow-covered";
            reason = "Active snowfall";
        }
        // Recent snow with warm temps = slushy
        else if (priorLower.contains("snow") && temperatureF > 32) {
            surface = "Slushy";
            reason = "Recent snow melting in above-freezing temperatures";
        }
        // Current freezing conditions
        else if (weatherLower.contains("freezing") || weatherLower.contains("ice")) {
            surface = "Icy";
            reason = "Freezing precipitation";
        }
        // Extended freezing with any moisture
        else if (temperatureF <= 32 && priorLower.contains("freezing")) {
            surface = "Icy";
            reason = "Extended freezing conditions";
        }
        // Current rain
        else if (weatherLower.contains("rain") || weatherLower.contains("drizzle")) {
            surface = "Wet";
            reason = "Active precipitation";
        }
        // Recent rain
        else if (priorLower.contains("rain") || priorLower.contains("precipitation")) {
            surface = "Wet";
            reason = "Recent precipitation in prior 24 hours";
        }
        // Default dry
        else {
            surface = "Dry";
            reason = "No adverse weather conditions";
        }

        int lanes = estimateLanesFromRoadType(roadType);
        boolean construction = random.nextDouble() < 0.1;

        log.info("Fallback road assessment: surface={}, lanes={}, reason={}", surface, lanes, reason);

        return new RoadConditions(surface, lanes, construction, reason);
    }

    /**
     * Valid surface condition values for road assessment.
     */
    private static final java.util.Set<String> VALID_SURFACE_CONDITIONS = java.util.Set.of(
        "Dry", "Wet", "Damp", "Icy", "Snow-covered", "Black Ice Risk",
        "Slushy", "Standing Water", "Slick"
    );

    /**
     * Check if a surface condition is valid.
     */
    private boolean isValidSurfaceCondition(String surface) {
        return surface != null && VALID_SURFACE_CONDITIONS.contains(surface);
    }

    /**
     * Estimate lane count based on road type classification.
     */
    private int estimateLanesFromRoadType(String roadType) {
        if (roadType == null) return 2;

        String type = roadType.toLowerCase();
        if (type.contains("interstate") || type.contains("freeway") || type.contains("motorway")) {
            return 6; // 3 lanes each direction typical
        }
        if (type.contains("highway") || type.contains("expressway") || type.contains("trunk")) {
            return 4;
        }
        if (type.contains("primary") || type.contains("boulevard")) {
            return 4;
        }
        if (type.contains("secondary") || type.contains("arterial") || type.contains("avenue")) {
            return 4;
        }
        if (type.contains("collector") || type.contains("tertiary")) {
            return 2;
        }
        if (type.contains("residential") || type.contains("local")) {
            return 2;
        }
        if (type.contains("service") || type.contains("driveway") || type.contains("parking")) {
            return 1;
        }
        return 2; // Default
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
        String prior24hr = getPrior24HourWeather(latitude, longitude, timestamp);

        // Use LLM-based road condition assessment with full context including hourly data
        RoadConditions road = getRoadConditions(
            latitude, longitude,
            weather.conditions(),
            weather.temperatureF(),
            prior24hr,
            location.roadType(),
            timestamp
        );

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
        String surface = road.surfaceCondition();
        if (surface.equals("Wet") || surface.equals("Damp") || surface.equals("Icy") ||
            surface.equals("Snow-covered") || surface.equals("Black Ice Risk") || surface.equals("Slushy") ||
            surface.equals("Standing Water") || surface.equals("Slick")) {
            factors.add("Hazardous road surface: " + surface + " (" + road.surfaceAssessmentReason() + ")");
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
