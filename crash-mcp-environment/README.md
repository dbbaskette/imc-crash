# Environment Agent

**Port:** 8082
**Implementation:** Real APIs + LLM-based Assessment

The Environment agent gathers environmental context for accident locations including weather conditions, road surface assessment, and location details. It uses real external APIs combined with LLM-based intelligent analysis for road hazard detection.

## Data Sources

| Data Type | Source | Status |
|-----------|--------|--------|
| Weather (current) | Open-Meteo API | Real |
| Weather (24hr history) | Open-Meteo API | Real |
| Humidity data | Open-Meteo API | Real |
| Reverse geocoding | OpenStreetMap Nominatim | Real |
| Road type classification | OpenStreetMap | Real |
| Road surface conditions | Google Gemini LLM | Real (AI-assessed) |
| Lane count | Estimated from road type | Derived |
| Construction zones | Random 10% | Simulated |

## MCP Tools

### `getWeather`

Get weather conditions at the accident location and time.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude coordinate |
| `longitude` | double | Longitude coordinate |
| `timestamp` | String | ISO 8601 timestamp of the event |

**Returns:** `WeatherConditions` containing:
- `conditions` - Weather description (Clear, Rain, Snow, etc.)
- `temperatureF` - Temperature in Fahrenheit
- `visibilityMiles` - Visibility distance
- `windSpeedMph` - Wind speed
- `precipitation` - Precipitation type if any

### `getPrior24HourWeather`

Analyze weather for the 24 hours before the accident.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude coordinate |
| `longitude` | double | Longitude coordinate |
| `timestamp` | String | ISO 8601 timestamp of the event |

**Returns:** Summary string with:
- Total precipitation
- Snow/rain hours
- Freezing temperature duration
- Temperature fluctuations across freezing point
- Thunderstorm activity

### Hourly Data for LLM (Internal)

The `getRoadConditions` tool internally fetches detailed hourly data for the last 12 hours including:
- Temperature (°F)
- Relative humidity (%)
- Weather conditions
- Precipitation amounts

Example hourly output sent to LLM:
```
HOURLY CONDITIONS (last 12 hours, most recent first):
  1 hr ago: 32°F, 78% humidity, Clear
  2 hr ago: 35°F, 82% humidity, Light Rain, Precip: 0.05"
  3 hr ago: 38°F, 85% humidity, Light Rain, Precip: 0.12"
  ...

KEY OBSERVATIONS:
  - Last precipitation: 2 hours ago at 35°F
  - Current humidity: 78%
  - HIGH HUMIDITY (82% avg) since precipitation - roads likely still wet
  - Temperature crossed 32°F freezing point in last 12 hours
  - BLACK ICE RISK: Precipitation at 35°F followed by temp drop to 32°F
```

### `reverseGeocode`

Convert GPS coordinates to a street address.

**Returns:** `LocationDetails` containing:
- `address` - Full street address
- `roadType` - Classification (Interstate, Highway, Residential, etc.)
- `intersection` - Nearest intersection

### `getRoadConditions`

Get road surface conditions using AI-based assessment.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude coordinate |
| `longitude` | double | Longitude coordinate |
| `currentWeather` | String | Current weather conditions |
| `temperatureF` | double | Current temperature |
| `prior24HourWeather` | String | 24-hour weather summary |
| `roadType` | String | Road type from geocoding |
| `timestamp` | String | ISO 8601 timestamp |

**Returns:** `RoadConditions` containing:
- `surfaceCondition` - Dry, Wet, Damp, Icy, Snow-covered, Black Ice Risk, Slushy, Standing Water, or Slick
- `numberOfLanes` - Estimated lane count
- `constructionZone` - Whether construction is nearby
- `surfaceAssessmentReason` - LLM's reasoning for the assessment

### `getFullEnvironmentContext`

Get complete environmental context combining all data sources.

**Returns:** `EnvironmentContext` with weather, road conditions, location, contributing factors, and daylight status.

## LLM-Based Road Surface Assessment

The agent uses Google Gemini to intelligently assess road conditions based on:

### Hourly Weather Data
- Temperature and humidity for each of the last 12 hours
- Precipitation events with amounts
- Weather condition codes

### Hazard Detection

**Black Ice Detection:**
- Precipitation when temp > 32°F followed by temp drop below 32°F
- Recent rain (1-6 hours) + current temp <= 32°F
- Temperature crossing freezing point with prior moisture

**Humidity-Based Drying Assessment:**
- High humidity (>80%) after rain = roads stay wet longer
- Low humidity (<50%) + warm temps = roads dry quickly
- Moderate humidity + several hours since rain = Damp

**Other Conditions:**
- Dew/condensation risk (humidity >90% + cool temps)
- Hydroplaning risk (heavy recent rain)
- Slick conditions (dew, first rain after dry spell)

## Road Type Classifications

The agent recognizes 30+ road types from OpenStreetMap:

| Category | Types |
|----------|-------|
| High-speed | Interstate/Freeway, Highway, Expressway |
| Urban arterials | Primary arterial, Secondary arterial, Boulevard |
| Collectors | Tertiary, Collector road |
| Local | Residential, Local road, Service road |
| Other | Parking lot, Driveway, Bike path, Footpath |

## Configuration

```yaml
spring:
  ai:
    google:
      genai:
        api-key: ${GOOGLE_API_KEY:}
        chat:
          options:
            model: gemini-2.5-flash
            temperature: 0.1
```

## Example Output

```
Environmental Context:
  Address: 1234 Main Street, Leesburg, VA 20175
  Road Type: Secondary arterial
  Weather: Partly Cloudy, 42°F
  Visibility: 8.5 miles
  Surface: Black Ice Risk
  Assessment: Prior rain at 45°F followed by temp drop to 28°F creates ice formation risk
  Lanes: 4
  Prior 24 Hours: Light precipitation: 0.3 inches; Temperature fluctuation across
                  freezing point (28°F to 48°F)
  Contributing Factors:
    - Hazardous road surface: Black Ice Risk (Prior rain followed by freezing)
    - Low light conditions (Night)
```

## Health Check

```bash
curl http://localhost:8082/actuator/health
```

## Dependencies

- Spring Boot 3.5.x
- Spring AI MCP Server
- Spring AI Google GenAI (Gemini)
- crash-domain (shared domain objects)

## External APIs

- **Open-Meteo** (https://open-meteo.com) - Free weather API, no key required
- **OpenStreetMap Nominatim** - Free geocoding, requires User-Agent header
- **Google AI** - Gemini 2.5 Flash for road surface assessment
