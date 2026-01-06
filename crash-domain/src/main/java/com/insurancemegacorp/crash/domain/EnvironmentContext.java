package com.insurancemegacorp.crash.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Environmental context gathered by the Environment agent.
 * Includes location details, weather, and road conditions.
 */
@JsonClassDescription("Environmental context at the time and location of the accident")
public record EnvironmentContext(
    @JsonPropertyDescription("Full street address of the accident location")
    String address,

    @JsonPropertyDescription("Type of road: Highway, Urban arterial, Residential, etc.")
    String roadType,

    @JsonPropertyDescription("Nearest intersection if applicable")
    String nearestIntersection,

    @JsonPropertyDescription("Weather conditions at time of accident")
    WeatherConditions weather,

    @JsonPropertyDescription("Road surface conditions")
    RoadConditions roadConditions,

    @JsonPropertyDescription("Factors that may have contributed to the accident")
    List<String> contributingFactors,

    @JsonPropertyDescription("Lighting conditions: Daylight, Dusk, Dawn, Night")
    String daylightStatus
) {
    @JsonClassDescription("Weather conditions at a specific location and time")
    public record WeatherConditions(
        @JsonPropertyDescription("Weather description: Clear, Cloudy, Rain, Snow, etc.")
        String conditions,

        @JsonPropertyDescription("Temperature in Fahrenheit")
        double temperatureF,

        @JsonPropertyDescription("Visibility in miles")
        double visibilityMiles,

        @JsonPropertyDescription("Wind speed in mph")
        double windSpeedMph,

        @JsonPropertyDescription("Precipitation type if any")
        String precipitation
    ) {}

    @JsonClassDescription("Road surface conditions")
    public record RoadConditions(
        @JsonPropertyDescription("Surface condition: Dry, Wet, Icy, Snow-covered")
        String surfaceCondition,

        @JsonPropertyDescription("Posted speed limit")
        int speedLimit,

        @JsonPropertyDescription("Number of lanes")
        int numberOfLanes,

        @JsonPropertyDescription("Whether there is road construction nearby")
        boolean constructionZone
    ) {}
}
