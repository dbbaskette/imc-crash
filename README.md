# CRASH - Claims Response Agent System Hive

```
   ____  ____      _     ____   _   _
  / ___||  _ \    / \   / ___| | | | |
 | |    | |_) |  / _ \  \___ \ | |_| |
 | |___ |  _ <  / ___ \  ___) ||  _  |
  \____||_| \_\/_/   \_\|____/ |_| |_|

  Claims Response Agent System Hive
```

A multi-agent **First Notice of Loss (FNOL)** system that automatically processes vehicle accident claims using coordinated AI agents working together like a hive.

## What is CRASH?

When a vehicle accident is detected via telemetry (g-force threshold exceeded from a safe driver app), CRASH dynamically orchestrates **5 specialized AI agents** to generate a complete, comprehensive FNOL claim report — automatically.

Each agent is an independent microservice that contributes its expertise:

| Agent | Responsibility |
|-------|----------------|
| **Impact Analyst** | Analyzes telemetry to classify severity (MINOR/MODERATE/SEVERE) and impact type |
| **Environment** | Gathers weather, road conditions, and location context |
| **Policy** | Retrieves insurance coverage, driver profile, and vehicle details |
| **Services** | Locates nearby body shops, tow services, hospitals based on severity |
| **Communications** | Handles driver outreach, SMS notifications, and adjuster alerts |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CRASH ORCHESTRATOR (:8080)                          │
│                      (Goal: Generate FNOL Report)                           │
│                                                                             │
│    Uses GOAP planning to determine execution order based on dependencies    │
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
    │  :8081   │ │  :8082   │ │  :8083   │ │  :8084   │ │  :8085   │
    └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
           \          |          |          |          /
            \         |          |          |         /
             +--------+----------+----------+--------+
                              THE HIVE
                    (Independent Specialist Agents)
```

**Key Technologies:**
- **Embabel Agent Framework 0.3.1** — Goal-based planning orchestrator using GOAP (full Java support)
- **Spring AI 1.1.0** — Model Context Protocol for agent communication via SSE
- **Spring Boot 3.4** — Microservice foundation
- **OpenAI GPT-5-nano** — Fast, cost-effective LLM for reasoning and planning

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- OpenAI API Key

### 1. Clone and Build

```bash
git clone <repository-url>
cd imc-crash
mvn clean install
```

### 2. Configure Variables

**Option A: Using vars.yaml (recommended)**
```bash
cp vars.yaml.template vars.yaml
# Edit vars.yaml with your API key and settings
```

**Option B: Environment variable**
```bash
export OPENAI_API_KEY=sk-...
```

### 3. Start the Hive

**Option A: Using the start script**
```bash
./start.sh --build
```

**Option B: Using Docker Compose**
```bash
mvn clean package -DskipTests
docker-compose up --build
```

**Option C: Manual startup** (6 terminals)
```bash
# Each in a separate terminal:
cd crash-mcp-impact-analyst && mvn spring-boot:run      # :8081
cd crash-mcp-environment && mvn spring-boot:run         # :8082
cd crash-mcp-policy && mvn spring-boot:run              # :8083
cd crash-mcp-services && mvn spring-boot:run            # :8084
cd crash-mcp-communications && mvn spring-boot:run      # :8085
cd crash-orchestrator && mvn spring-boot:run            # :8080
```

### 4. Simulate an Accident

```bash
# Moderate accident
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200018&gForce=3.8&speedMph=34.5"

# Severe accident
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200019&gForce=6.2&speedMph=55"

# Minor fender bender
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200020&gForce=2.8&speedMph=15"
```

## How It Works

1. **Event Trigger** — Safe driver app detects g-force > 2.5 and sends telemetry
2. **Orchestrator Plans** — GOAP planner analyzes dependencies and creates execution plan
3. **Parallel Execution** — Impact, Environment, and Policy agents run simultaneously
4. **Dependent Execution** — Services and Communications wait for required data
5. **Report Compilation** — All results aggregated into comprehensive FNOL report

### Severity-Based Behavior

The system adapts its response based on accident severity:

| Severity | Services | Communications |
|----------|----------|----------------|
| **SEVERE** (g-force ≥ 5.0) | Prioritizes hospitals, immediate tow dispatch | Senior adjuster notified, roadside assistance dispatched |
| **MODERATE** (g-force ≥ 3.0) | Body shops + tow services | Standard adjuster assigned |
| **MINOR** (g-force < 3.0) | Body shop referrals only | SMS/push notification only |

## Project Structure

```
imc-crash/
├── crash-domain/                   # Shared domain objects (Java Records)
├── crash-orchestrator/             # Central orchestrator (:8080)
├── crash-mcp-impact-analyst/       # Impact analysis agent (:8081)
├── crash-mcp-environment/          # Environment context agent (:8082)
├── crash-mcp-policy/               # Policy lookup agent (:8083)
├── crash-mcp-services/             # Services finder agent (:8084)
├── crash-mcp-communications/       # Communications agent (:8085)
├── docker-compose.yml              # Container orchestration
├── start.sh / stop.sh              # Convenience scripts
└── BUILD.md                        # Detailed build documentation
```

## API Reference

### Process Accident Event

```bash
POST /api/accident
Content-Type: application/json

{
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
}
```

### Simulation Endpoint

```bash
POST /api/accident/simulate?policyId={id}&gForce={value}&speedMph={value}
```

### Health Check

```bash
GET /api/health                    # Orchestrator
GET http://localhost:8081/actuator/health  # Impact Analyst
# ... etc for ports 8082-8085
```

## Test Policies

Three pre-configured policies are available for testing:

| Policy ID | Description |
|-----------|-------------|
| `200018` | Standard policy |
| `200019` | Premium policy |
| `200020` | Basic policy |

## Documentation

For detailed information, see:
- [BUILD.md](BUILD.md) — Comprehensive build guide, configuration reference, and extension instructions

## Extending CRASH

### Add a New Tool

```java
@Tool(description = "Estimate vehicle repair cost")
public RepairEstimate estimateRepairCost(
    @ToolParam(description = "Severity level") String severity,
    @ToolParam(description = "Vehicle make") String make
) {
    // Implementation - automatically exposed via MCP
}
```

### Add a New Agent

1. Create module: `crash-mcp-<name>`
2. Implement tools with `@Tool` annotations
3. Add MCP client connection to orchestrator
4. Create orchestrator actions that use the new tools

## Future Enhancements

- Real API integrations (weather, places, SMS)
- Database persistence for claims
- Real-time claims dashboard UI
- Fraud detection agent
- Fleet manager agent
- Kubernetes deployment

## License

Apache 2.0

---

*CRASH - Claims Response Agent System Hive*
*A multi-agent FNOL demonstration by Insurance Mega Corp*
