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
    String daylightStatus,

    @JsonPropertyDescription("Weather summary for the 24 hours prior to accident")
    String prior24HourWeather
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

    @JsonClassDescription("Road surface conditions assessed by LLM based on weather data")
    public record RoadConditions(
        @JsonPropertyDescription("Surface condition: Dry, Wet, Icy, Snow-covered, Black Ice Risk, Slushy")
        String surfaceCondition,

        @JsonPropertyDescription("Number of lanes (estimated from road type)")
        int numberOfLanes,

        @JsonPropertyDescription("Whether there is road construction nearby")
        boolean constructionZone,

        @JsonPropertyDescription("LLM reasoning for the surface condition assessment")
        String surfaceAssessmentReason
    ) {}
}
