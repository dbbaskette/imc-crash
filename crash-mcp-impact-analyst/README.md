# Impact Analyst Agent

**Port:** 8081
**Implementation:** Rule-based classification with LLM-enhanced narratives

The Impact Analyst agent analyzes vehicle telemetry data to classify accident severity and impact type. It uses algorithmic analysis of accelerometer readings and g-force measurements for deterministic classification, then generates professional incident narratives using Google Gemini LLM.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Impact Analyst Agent                      │
├─────────────────────────────────────────────────────────────┤
│  Telemetry Input                                            │
│  (G-force, Speed, Accelerometer X/Y/Z)                      │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────┐                │
│  │     Rule-Based Classification           │                │
│  │     (Fast, Deterministic)               │                │
│  │                                         │                │
│  │  • Severity: MINOR/MODERATE/SEVERE      │                │
│  │  • Impact Type: FRONTAL/REAR/SIDE/etc   │                │
│  │  • Speeding Detection                   │                │
│  │  • Airbag Deployment Likelihood         │                │
│  └─────────────────────────────────────────┘                │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────┐                │
│  │     LLM Narrative Generation            │                │
│  │     (Google Gemini 2.5 Flash)           │                │
│  │                                         │                │
│  │  Professional incident summary for      │                │
│  │  FNOL reports, written like an          │                │
│  │  accident reconstruction analyst        │                │
│  └─────────────────────────────────────────┘                │
│                          │                                   │
│                          ▼                                   │
│               ImpactAnalysis Result                          │
└─────────────────────────────────────────────────────────────┘
```

## MCP Tools

### `analyzeImpact`

Analyzes accident telemetry data to classify impact severity and type. Returns AI-generated professional narrative.

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
- `narrative` - **AI-generated professional incident summary**

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

## LLM Narrative Generation

When Google Gemini is configured, the agent generates professional incident narratives that:

1. Describe collision physics in professional insurance terminology
2. Explain what sensor data indicates about crash dynamics
3. Note safety concerns and risk factors
4. Use objective language suitable for legal/regulatory documentation

### Example LLM-Generated Narrative

> Telemetry analysis indicates a moderate-severity frontal impact event occurring at 35 mph, within the posted speed limit. The longitudinal deceleration of -3.8g is consistent with a sudden forward collision, such as striking a stationary object or another vehicle's rear. The minimal lateral (0.5g) and vertical (0.2g) forces suggest a direct, non-angular impact. Given the recorded G-force of 4.2g, airbag deployment is considered probable, and occupant evaluation is recommended.

### Fallback Behavior

If the LLM is unavailable (no API key or network issues), the agent falls back to rule-based narrative generation.

## Configuration

### Severity Thresholds

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

### LLM Configuration

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

## Health Check

```bash
curl http://localhost:8081/actuator/health
```

## Dependencies

- Spring Boot 3.5.x
- Spring AI MCP Server
- Spring AI Google GenAI (Gemini)
- crash-domain (shared domain objects)

## External APIs

- **Google AI** - Gemini 2.5 Flash for professional narrative generation
