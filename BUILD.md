# CRASH - Claims Response Agent System Hive

## Overview

**CRASH** is a multi-agent First Notice of Loss (FNOL) system demonstrating:

- **Embabel Agent Framework** as the orchestrator with goal-based planning
- **Spring AI MCP Servers** for 5 independent specialist agents
- **Model Context Protocol (MCP)** for agent-to-agent communication

When an accident is detected (g-force threshold exceeded), the orchestrator dynamically plans and coordinates 5 specialist agents — working like a hive — to generate a complete FNOL claim report.

```
   ____  ____      _     ____   _   _ 
  / ___||  _ \    / \   / ___| | | | |
 | |    | |_) |  / _ \  \___ \ | |_| |
 | |___ |  _ <  / ___ \  ___) ||  _  |
  \____||_| \_\/_/   \_\|____/ |_| |_|
                                      
  Claims Response Agent System Hive
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CRASH ORCHESTRATOR                                  │
│                    (Goal: Generate FNOL Report)                             │
│                                                                             │
│   Uses GOAP planning to determine execution order based on dependencies     │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    MCP Client Connections (SSE)                      │   │
│  └──────┬──────────┬──────────┬──────────┬──────────┬──────────────────┘   │
└─────────┼──────────┼──────────┼──────────┼──────────┼───────────────────────┘
          │          │          │          │          │
          ▼          ▼          ▼          ▼          ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
    │ IMPACT   │ │ENVIRON-  │ │ POLICY   │ │ SERVICES │ │  COMMS   │
    │ ANALYST  │ │  MENT    │ │  AGENT   │ │  AGENT   │ │  AGENT   │
    │   MCP    │ │   MCP    │ │   MCP    │ │   MCP    │ │   MCP    │
    │ :8081    │ │  :8082   │ │  :8083   │ │  :8084   │ │  :8085   │
    └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
           \          |          |          |          /
            \         |          |          |         /
             +--------+----------+----------+--------+
                              THE HIVE
                    (Independent Specialist Agents)
```

## Module Structure

```
imc-crash/
├── pom.xml                              # Parent POM
├── BUILD.md                             # This file
├── crash-domain/                        # Shared domain objects
├── crash-mcp-impact-analyst/            # Impact analysis MCP server (:8081)
├── crash-mcp-environment/               # Environment context MCP server (:8082)
├── crash-mcp-policy/                    # Policy lookup MCP server (:8083)
├── crash-mcp-services/                  # Nearby services MCP server (:8084)
├── crash-mcp-communications/            # Driver communications MCP server (:8085)
└── crash-orchestrator/                  # Embabel orchestrator (:8080)
```

## The Hive — Agent Responsibilities

| Agent | Port | Tools | Role in the Hive |
|-------|------|-------|------------------|
| **Impact Analyst** | 8081 | `analyze_impact` | Analyzes telemetry to classify severity (MINOR/MODERATE/SEVERE) and impact type |
| **Environment** | 8082 | `get_weather`, `reverse_geocode`, `get_road_conditions` | Gathers contextual data about accident location/conditions |
| **Policy** | 8083 | `lookup_policy`, `get_driver_profile`, `get_vehicle_details` | Retrieves insurance coverage and driver information |
| **Services** | 8084 | `find_body_shops`, `find_tow_services`, `find_hospitals` | Locates nearby relevant services based on severity |
| **Communications** | 8085 | `send_sms`, `send_push_notification`, `log_communication` | Handles driver outreach and status tracking |

## Prerequisites

1. **Java 21+**
   ```bash
   java -version
   ```

2. **Maven 3.9+**
   ```bash
   mvn -version
   ```

3. **OpenAI API Key** (for the CRASH orchestrator)
   ```bash
   export OPENAI_API_KEY=sk-...
   ```

4. **Optional: Anthropic API Key** (if using Claude models)
   ```bash
   export ANTHROPIC_API_KEY=sk-ant-...
   ```

## Building the Project

### Build Everything

```bash
cd imc-crash
mvn clean install
```

### Build Individual Modules

```bash
# Build just the domain module
mvn clean install -pl crash-domain

# Build a specific MCP server
mvn clean install -pl crash-mcp-impact-analyst
```

## Running the Hive

### Option 1: Run All Services (Recommended for Demo)

Open 6 terminal windows:

**Terminal 1 - Impact Analyst (port 8081):**
```bash
cd crash-mcp-impact-analyst
mvn spring-boot:run
```

**Terminal 2 - Environment Agent (port 8082):**
```bash
cd crash-mcp-environment
mvn spring-boot:run
```

**Terminal 3 - Policy Agent (port 8083):**
```bash
cd crash-mcp-policy
mvn spring-boot:run
```

**Terminal 4 - Services Agent (port 8084):**
```bash
cd crash-mcp-services
mvn spring-boot:run
```

**Terminal 5 - Communications Agent (port 8085):**
```bash
cd crash-mcp-communications
mvn spring-boot:run
```

**Terminal 6 - Orchestrator (port 8080):**
```bash
cd crash-orchestrator
mvn spring-boot:run -Dspring-boot.run.profiles=shell
```

### Option 2: Docker Compose (Production-like)

```bash
# Build all images
mvn clean package -DskipTests
docker-compose up --build
```

## Using CRASH

### Interactive Shell

Once the orchestrator is running with the `shell` profile:

```bash
# In the Embabel shell, run:
execute "Process accident event for policy 200018" -p -r
```

### REST API

Send an accident event directly:

```bash
curl -X POST http://localhost:8080/api/accident \
  -H "Content-Type: application/json" \
  -d '{
    "policyId": 200018,
    "vehicleId": 300021,
    "driverId": 400018,
    "vin": "1HGBH41JXMN109186",
    "eventTime": "2025-01-06T14:47:00Z",
    "speedMph": 34.5,
    "speedLimitMph": 35,
    "gForce": 3.8,
    "latitude": 39.1157,
    "longitude": -77.5636,
    "currentStreet": "Main Street",
    "accelerometerX": -2.1,
    "accelerometerY": 0.3,
    "accelerometerZ": 0.8
  }'
```

### Simulating Accidents

Quick simulation endpoint:

```bash
# Simulate a moderate accident
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200018&gForce=3.8&speedMph=34.5"

# Simulate a severe accident
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200019&gForce=6.2&speedMph=55"

# Simulate a minor fender bender
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200020&gForce=2.8&speedMph=15"
```

## Demo Walkthrough

### 1. Start the Hive
Start each MCP server and the orchestrator as described above.

### 2. Verify MCP Servers
Each server exposes an SSE endpoint. You can verify with:

```bash
# Check Impact Analyst health
curl http://localhost:8081/actuator/health

# Use MCP Inspector for interactive exploration
npx @modelcontextprotocol/inspector
# Connect to: http://localhost:8081
```

### 3. Trigger an Accident Event
Send an event to the orchestrator and watch the logs across all terminals to see:

1. **Orchestrator** receives event, creates a plan
2. **Impact Analyst** called first to determine severity
3. **Environment**, **Policy** called in parallel (no dependencies on each other)
4. **Services** called after Impact (needs severity classification)
5. **Communications** called after Policy (needs driver contact info)
6. **Report Compiler** aggregates everything into final FNOL

### 4. Observe Dynamic Planning
Try different scenarios:

**Severe Accident (g_force > 5.0):**
- Services agent prioritizes hospitals
- Communications agent escalates to adjuster immediately

**Minor Accident (g_force < 3.0):**
- Services agent only finds body shops
- Communications sends routine notification

## Extending the Hive

### Adding a New MCP Tool

1. Create a new method in the appropriate service:

```java
@Tool(
    description = "Estimate vehicle repair cost based on damage"
)
public RepairEstimate estimateRepairCost(
    @ToolParam(description = "Severity level") String severity,
    @ToolParam(description = "Vehicle year") int year,
    @ToolParam(description = "Vehicle make") String make
) {
    // Implementation
}
```

2. The tool is automatically exposed via MCP - no additional configuration needed.

### Adding a New Agent to the Hive

1. Create a new module: `crash-mcp-<name>`
2. Copy the structure from an existing MCP server
3. Implement your tools with `@Tool`
4. Add the connection to orchestrator's `application.yml`
5. Create new domain objects if needed
6. Add actions to the orchestrator that use the new tools

### Integrating Real APIs

Replace simulated services with real ones:

**Weather (Environment Agent):**
```java
@Value("${openweathermap.api-key}")
private String apiKey;

@Tool(description = "Get weather at location")
public WeatherConditions getWeather(double lat, double lon, String timestamp) {
    return restTemplate.getForObject(
        "https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid={key}",
        WeatherConditions.class, lat, lon, apiKey
    );
}
```

## Troubleshooting

### MCP Server Not Responding
```bash
# Check if the server is running
curl http://localhost:8081/actuator/health

# Check logs
tail -f crash-mcp-impact-analyst/logs/app.log
```

### Orchestrator Can't Connect to Hive
Ensure all MCP servers are started before the orchestrator. The SSE connections are established on startup.

### Tool Callbacks Not Available
Check that `spring.ai.mcp.client.toolcallback.enabled=true` in orchestrator config.

## Configuration Reference

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key for orchestrator | Required |
| `ANTHROPIC_API_KEY` | Anthropic API key (optional) | - |
| `IMPACT_ANALYST_URL` | Impact analyst MCP URL | http://localhost:8081 |
| `ENVIRONMENT_URL` | Environment MCP URL | http://localhost:8082 |
| `POLICY_URL` | Policy MCP URL | http://localhost:8083 |
| `SERVICES_URL` | Services MCP URL | http://localhost:8084 |
| `COMMUNICATIONS_URL` | Communications MCP URL | http://localhost:8085 |

### Customizing Severity Thresholds

In `crash-mcp-impact-analyst/src/main/resources/application.yml`:

```yaml
crash:
  impact:
    thresholds:
      severe-g-force: 5.0
      moderate-g-force: 3.0
      severe-speed-delta: 45
      moderate-speed-delta: 25
```

## Real vs Simulated Services

The following table shows which components use real external services vs simulated/local data:

| Component | Service | Status | Details |
|-----------|---------|--------|---------|
| **Environment** | Reverse Geocoding | **REAL** | OpenStreetMap Nominatim API - real addresses from GPS |
| **Environment** | Weather | **REAL** | Open-Meteo API - current conditions + 24hr history |
| **Environment** | Road Conditions | **REAL** | LLM-assessed from weather data (precipitation, visibility) |
| **Communications** | SMS | **REAL** | Twilio SDK - sends actual SMS messages |
| **Communications** | Email | **REAL** | Gmail SMTP - sends FNOL reports to adjusters |
| **Policy** | Policy Data | **REAL** | PostgreSQL database - 15 pre-loaded policies |
| **Policy** | Driver Info | **REAL** | PostgreSQL database - full driver profiles |
| **Policy** | Vehicle Info | **REAL** | PostgreSQL database - complete vehicle details |
| **Impact Analyst** | Severity Classification | Rules-based | Threshold algorithm (g-force ≥5.0=SEVERE, ≥3.0=MODERATE) |
| **Impact Analyst** | Impact Type Detection | Rules-based | Accelerometer pattern matching (X/Y/Z axis analysis) |
| **Impact Analyst** | Narrative Generation | **REAL** | LLM-generated professional incident description |
| **Services** | Body Shops | Simulated | Mock data based on location + severity |
| **Services** | Tow Services | Simulated | Mock data based on location + severity |
| **Services** | Hospitals | Simulated | Mock data based on location + severity |
| **Telematics** | Driver Simulation | Local | 15 drivers on realistic Atlanta GPS routes |
| **Telematics** | Crash Events | Local | RabbitMQ with publisher confirms |
| **Orchestrator** | GOAP Planning | **REAL** | Embabel Agent Framework goal-based planning |
| **Orchestrator** | LLM Reasoning | **REAL** | Google Gemini 2.5 Flash via Google AI API |
| **Persistence** | FNOL Reports | **REAL** | PostgreSQL database storage |

### Configuration Required for Real Services

| Service | Configuration | Location |
|---------|--------------|----------|
| Google AI (Gemini) | `GOOGLE_API_KEY` | `vars.yaml` → `google.api_key` |
| Twilio SMS | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | `vars.yaml` → `twilio.*` |
| Gmail SMTP | `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD` | `vars.yaml` → `gmail.*` |
| PostgreSQL | Auto-configured | Docker Compose (localhost:5432) |

### Test Mode Options

| Option | Purpose |
|--------|---------|
| `twilio.test_to_number` | Redirect all SMS to a single test number instead of customer phones |
| Simulated services | Body shops, tow, hospitals return realistic mock data |

## Next Steps

1. **Add Real Services API** - Replace simulated body shops/tow/hospitals with Google Places API
2. **Add UI** - Build a claims dashboard showing real-time FNOL processing
3. **Add Fraud Detection Agent** - New hive member that analyzes patterns for anomalies
4. **Add Fleet Manager Agent** - For commercial fleet use cases
5. **Deploy to Cloud** - Containerize and deploy to Kubernetes

## License

Apache 2.0

---

*CRASH - Claims Response Agent System Hive*
*A multi-agent FNOL demonstration by Insurance Mega Corp*
