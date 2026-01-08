# Impact Analyst Agent

**Port:** 8081
**Implementation:** Real Business Logic

The Impact Analyst agent analyzes vehicle telemetry data to classify accident severity and impact type. It uses algorithmic analysis of accelerometer readings and g-force measurements to determine the nature and severity of a collision.

## MCP Tools

### `analyzeImpact`

Analyzes accident telemetry data to classify impact severity and type.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `gForce` | double | G-force measurement from accelerometer |
| `speedMph` | double | Vehicle speed in mph at time of event |
| `speedLimitMph` | int | Posted speed limit in mph |
| `accelerometerX` | double | Longitudinal axis (negative=deceleration, positive=pushed forward) |
| `accelerometerY` | double | Lateral axis (side impacts) |
| `accelerometerZ` | double | Vertical axis (rollovers) |

**Returns:** `ImpactAnalysis` containing:
- `severity` - MINOR, MODERATE, or SEVERE
- `impactType` - FRONTAL, REAR, SIDE, ROLLOVER, or UNKNOWN
- `estimatedSpeed` - Speed at impact
- `wasSpeeding` - Whether vehicle exceeded speed limit
- `airbagDeployed` - Likelihood of airbag deployment
- `confidence` - Analysis confidence score (0.0-1.0)
- `narrative` - Human-readable description

### `isAccidentDetected`

Quick check to determine if an event meets the threshold for accident detection.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `gForce` | double | G-force measurement |
| `threshold` | Double | Optional threshold (default 2.5g) |

**Returns:** `boolean` - Whether the event qualifies as an accident

## Severity Classification

| Severity | G-Force Threshold | Speed Threshold |
|----------|-------------------|-----------------|
| **SEVERE** | >= 5.0g | >= 45 mph |
| **MODERATE** | >= 3.0g | >= 25 mph |
| **MINOR** | < 3.0g | < 25 mph |

## Impact Type Detection

The agent uses accelerometer axis patterns to detect 9 types of accidents:

| Impact Type | Detection Pattern |
|-------------|-------------------|
| **ROLLOVER** | Extreme Z-axis (> 6.0g) or dominant Z |
| **HEAD_ON** | Extreme negative X (< -7.0g) |
| **T_BONE** | Strong lateral Y (> 4.5g, dominant) |
| **FRONTAL** | Strong negative X (< -3.5g, dominant) |
| **REAR** | Positive X (> 1.5g) - vehicle pushed forward |
| **SIDE** | Moderate lateral Y (> 1.5g, dominant) |

## Configuration

Thresholds can be customized in `application.yml`:

```yaml
fnol:
  impact:
    thresholds:
      severe-g-force: 5.0
      moderate-g-force: 3.0
      severe-speed-delta: 45
      moderate-speed-delta: 25
```

## Example Usage

```bash
# Test the analyzeImpact tool via MCP
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "analyzeImpact",
      "arguments": {
        "gForce": 4.2,
        "speedMph": 35.0,
        "speedLimitMph": 35,
        "accelerometerX": -3.8,
        "accelerometerY": 0.5,
        "accelerometerZ": 0.2
      }
    }
  }'
```

## Sample Output

```
Vehicle experienced moderate frontal impact. G-force of 4.2 detected,
suggesting moderate collision. Speed at event: 35 mph (limit: 35 mph).
Vehicle was within posted speed limit.
```

## Health Check

```bash
curl http://localhost:8081/actuator/health
```

## Dependencies

- Spring Boot 3.5.x
- Spring AI MCP Server
- crash-domain (shared domain objects)
