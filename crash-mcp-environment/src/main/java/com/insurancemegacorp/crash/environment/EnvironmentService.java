package com.insurancemegacorp.crash.environment;

import com.insurancemegacorp.crash.domain.EnvironmentContext;
import com.insurancemegacorp.crash.domain.EnvironmentContext.RoadConditions;
import com.insurancemegacorp.crash.domain.EnvironmentContext.WeatherConditions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MCP Tools for gathering environmental context for accident locations.
 * In a real implementation, these would call external APIs (OpenWeatherMap, Google Geocoding, etc.)
 */
@Service
public class EnvironmentService {

    private final Random random = new Random();

    /**
     * Get weather conditions at a specific location and time.
     * In production, this would call OpenWeatherMap or similar API.
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
        // Simulated weather data - in production, call weather API
        String[] conditions = {"Clear", "Partly Cloudy", "Cloudy", "Light Rain", "Rain", "Snow"};
        String condition = conditions[random.nextInt(conditions.length)];
        
        double temperature = 35 + random.nextDouble() * 50; // 35-85°F
        double visibility = condition.contains("Rain") || condition.contains("Snow") 
                            ? 2 + random.nextDouble() * 5 
                            : 8 + random.nextDouble() * 2;
        double windSpeed = random.nextDouble() * 25;
        
        String precipitation = null;
        if (condition.contains("Rain")) precipitation = "Rain";
        if (condition.contains("Snow")) precipitation = "Snow";
        
        return new WeatherConditions(
            condition,
            Math.round(temperature * 10) / 10.0,
            Math.round(visibility * 10) / 10.0,
            Math.round(windSpeed * 10) / 10.0,
            precipitation
        );
    }

    /**
     * Reverse geocode coordinates to get a street address.
     * In production, this would call Google Geocoding API or similar.
     */
    @Tool(description = "Convert GPS coordinates to a street address. " +
                        "Returns full address, road type, and nearest intersection.")
    public LocationDetails reverseGeocode(
            @ToolParam(description = "Latitude coordinate") 
            double latitude,
            
            @ToolParam(description = "Longitude coordinate") 
            double longitude
    ) {
        // Simulated geocoding - in production, call geocoding API
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
     */
    @Tool(description = "Get road surface conditions at the accident location. " +
                        "Returns surface condition, speed limit, lane count, and construction status.")
    public RoadConditions getRoadConditions(
            @ToolParam(description = "Latitude coordinate") 
            double latitude,
            
            @ToolParam(description = "Longitude coordinate") 
            double longitude
    ) {
        // Simulated road conditions
        String[] surfaces = {"Dry", "Wet", "Icy", "Snow-covered"};
        String surface = surfaces[random.nextInt(surfaces.length)];
        
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
        RoadConditions road = getRoadConditions(latitude, longitude);
        
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
        if (road.surfaceCondition().equals("Wet") || road.surfaceCondition().equals("Icy")) {
            factors.add("Slippery road surface");
        }
        if (weather.visibilityMiles() < 5) {
            factors.add("Reduced visibility");
        }
        if (daylight.equals("Night") || daylight.equals("Dusk") || daylight.equals("Dawn")) {
            factors.add("Low light conditions");
        }
        if (road.constructionZone()) {
            factors.add("Construction zone");
        }
        if (weather.windSpeedMph() > 20) {
            factors.add("High winds");
        }
        
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
