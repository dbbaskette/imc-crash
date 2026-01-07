package com.insurancemegacorp.crash.environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancemegacorp.crash.domain.EnvironmentContext;
import com.insurancemegacorp.crash.domain.EnvironmentContext.RoadConditions;
import com.insurancemegacorp.crash.domain.EnvironmentContext.WeatherConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
    @Tool(description = "Get weather conditions at the accident location and time. " +
                        "Returns temperature, visibility, wind speed, and precipitation status.")
    public WeatherConditions getWeather(
            @ToolParam(description = "Latitude coordinate")
            double latitude,

            @ToolParam(description = "Longitude coordinate")
            double longitude,

            @ToolParam(description = "ISO 8601 timestamp of the event")
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
                // Use forecast API for recent/current data
                url = String.format(
                    "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f" +
                    "&hourly=temperature_2m,weather_code,wind_speed_10m,visibility" +
                    "&temperature_unit=fahrenheit&wind_speed_unit=mph" +
                    "&past_days=7&forecast_days=1",
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
     * Reverse geocode coordinates to get a street address.
     * Uses simulated data - could integrate with Nominatim or Google Geocoding.
     */
    @Tool(description = "Convert GPS coordinates to a street address. " +
                        "Returns full address, road type, and nearest intersection.")
    public LocationDetails reverseGeocode(
            @ToolParam(description = "Latitude coordinate")
            double latitude,

            @ToolParam(description = "Longitude coordinate")
            double longitude
    ) {
        // For now, simulated geocoding
        // Could integrate with OpenStreetMap Nominatim (free, no API key)
        String[] streets = {"Main Street", "Oak Avenue", "Maple Drive", "Highway 7", "Industrial Blvd"};
        String[] cities = {"Leesburg", "Ashburn", "Sterling", "Herndon", "Reston"};
        String[] roadTypes = {"Urban arterial", "Highway", "Residential", "Commercial"};

        int streetNum = 100 + random.nextInt(9900);
        String street = streets[random.nextInt(streets.length)];
        String city = cities[random.nextInt(cities.length)];
        String roadType = roadTypes[random.nextInt(roadTypes.length)];

        String address = String.format("%d %s, %s, VA 20176", streetNum, street, city);
        String intersection = street + " & " + streets[(random.nextInt(streets.length))];

        return new LocationDetails(address, roadType, intersection);
    }

    /**
     * Get road conditions at a location.
     * Infers conditions based on weather data.
     */
    @Tool(description = "Get road surface conditions at the accident location. " +
                        "Returns surface condition, speed limit, lane count, and construction status.")
    public RoadConditions getRoadConditions(
            @ToolParam(description = "Latitude coordinate")
            double latitude,

            @ToolParam(description = "Longitude coordinate")
            double longitude,

            @ToolParam(description = "Current weather conditions for inference")
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
        int speedLimit = random.nextBoolean() ? 35 : (random.nextBoolean() ? 45 : 55);
        int lanes = random.nextBoolean() ? 2 : 4;
        boolean construction = random.nextDouble() < 0.1; // 10% chance

        return new RoadConditions(surface, speedLimit, lanes, construction);
    }

    /**
     * Get complete environmental context combining all data sources.
     */
    @Tool(description = "Get complete environmental context for an accident. " +
                        "Combines weather, location, and road conditions into a comprehensive report.")
    public EnvironmentContext getFullEnvironmentContext(
            @ToolParam(description = "Latitude coordinate")
            double latitude,

            @ToolParam(description = "Longitude coordinate")
            double longitude,

            @ToolParam(description = "ISO 8601 timestamp of the event")
            String timestamp
    ) {
        LocationDetails location = reverseGeocode(latitude, longitude);
        WeatherConditions weather = getWeather(latitude, longitude, timestamp);
        RoadConditions road = getRoadConditions(latitude, longitude, weather.conditions());

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

        log.info("Environment context generated: weather={}, road={}, factors={}",
                weather.conditions(), road.surfaceCondition(), factors.size());

        return new EnvironmentContext(
            location.address(),
            location.roadType(),
            location.intersection(),
            weather,
            road,
            factors,
            daylight
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
