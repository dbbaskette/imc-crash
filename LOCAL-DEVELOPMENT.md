# Local Development Guide

This guide covers running IMC-CRASH locally using Docker Compose with the telematics simulator for testing and development.

## Prerequisites

- **Java 21+**
  ```bash
  java -version
  ```

- **Maven 3.9+**
  ```bash
  mvn -version
  ```

- **Docker & Docker Compose**
  ```bash
  docker --version
  docker-compose --version
  ```

- **Google AI API Key** (for Gemini LLM)
  - Get one at https://aistudio.google.com/apikey

## Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd imc-crash
mvn clean install
```

### 2. Configure Variables

```bash
cp vars.yaml.template vars.yaml
```

Edit `vars.yaml` with your credentials:

```yaml
google:
  api_key: "AIza..."           # Required - Google AI API key

twilio:
  account_sid: "AC..."         # Optional - for real SMS
  auth_token: "..."
  from_number: "+1..."
  test_to_number: "+1..."      # Redirect all SMS to test number

gmail:
  username: "your@gmail.com"   # Optional - for real email
  app_password: "..."          # Gmail App Password (not regular password)
```

### 3. Start the System

**Option A: Using the start script (recommended)**
```bash
./start.sh --build
```

**Option B: Using Docker Compose directly**
```bash
mvn clean package -DskipTests
docker-compose up --build
```

### 4. Initialize the Database

The database is automatically initialized by Docker Compose, but you can manually reload:

```bash
./db/setup-db.sh
```

### 5. Simulate Accidents

**Using curl:**
```bash
# Moderate accident
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200001&gForce=3.8&speedMph=34.5"

# Severe accident
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200003&gForce=6.2&speedMph=55"

# Minor fender bender
curl -X POST "http://localhost:8080/api/accident/simulate?policyId=200007&gForce=2.8&speedMph=15"
```

**Using the simulation script:**
```bash
./simulate-accident.sh
```

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Orchestrator | 8080 | Main API, accident processing |
| Orchestrator Sink | 8086 | RabbitMQ consumer |
| Impact Analyst | 8081 | MCP server |
| Environment | 8082 | MCP server |
| Policy | 8083 | MCP server |
| Services | 8084 | MCP server |
| Communications | 8085 | MCP server |
| Telematics Generator | 8087 | Driver simulator + WebSocket dashboard |
| RabbitMQ | 5672 | AMQP |
| RabbitMQ Management | 15672 | Web UI (guest/guest) |
| PostgreSQL | 5432 | Database |

## Telematics Generator

The telematics generator simulates 15 drivers on realistic Atlanta routes. Access the dashboard at:

**http://localhost:8087**

Features:
- Real-time driver map with GPS positions
- Click drivers to trigger crash events
- Adjustable crash frequency
- WebSocket live updates

## Test Policies

15 pre-configured policies matching the telematics generator:

| Policy ID | Driver | Vehicle | Deductible |
|-----------|--------|---------|------------|
| `200001` | Sarah Chen | 2023 Chevrolet Equinox | $500 |
| `200002` | Emily Carter | 2022 Nissan Rogue | $500 |
| `200003` | Benjamin Rivera | 2023 Tesla Model 3 | $1,000 |
| `200004` | Michael Harris | 2021 Audi Q5 | $500 |
| `200005` | David Lee | 2022 Ford Explorer | $250 |
| `200006` | Jessica Thompson | 2023 Ford F-150 | $500 |
| `200007` | Andrew Martinez | 2023 Toyota Camry | $500 |
| `200008` | Ashley Wilson | 2023 Honda CR-V | $750 |
| `200009` | Christopher Garcia | 2023 Kia Telluride | $500 |
| `200010` | Amanda Rodriguez | 2023 Hyundai Palisade | $500 |
| `200011` | Daniel Johnson | 2023 Jeep Grand Cherokee | $500 |
| `200012` | Lauren Brown | 2022 BMW X5 | $1,000 |
| `200013` | Matthew Davis | 2023 Mazda CX-5 | $500 |
| `200014` | Stephanie Miller | 2023 Lexus RX 350 | $500 |
| `200015` | Ryan Anderson | 2023 Subaru Outback | $500 |

All policies include full coverage (Comprehensive, Collision, Liability, Medical, Uninsured Motorist) with Roadside Assistance and Rental Coverage.

## Manual Startup (Development)

For debugging or development, run services individually:

**Terminal 1 - Infrastructure:**
```bash
docker-compose up postgres rabbitmq
```

**Terminal 2 - Impact Analyst:**
```bash
cd crash-mcp-impact-analyst && mvn spring-boot:run
```

**Terminal 3 - Environment:**
```bash
cd crash-mcp-environment && mvn spring-boot:run
```

**Terminal 4 - Policy:**
```bash
cd crash-mcp-policy && mvn spring-boot:run
```

**Terminal 5 - Services:**
```bash
cd crash-mcp-services && mvn spring-boot:run
```

**Terminal 6 - Communications:**
```bash
cd crash-mcp-communications && mvn spring-boot:run
```

**Terminal 7 - Orchestrator:**
```bash
cd crash-orchestrator && mvn spring-boot:run
```

## Configuration Reference

### vars.yaml Options

| Section | Key | Description | Required |
|---------|-----|-------------|----------|
| `google` | `api_key` | Google AI API key for Gemini | Yes |
| `twilio` | `account_sid` | Twilio Account SID | No |
| `twilio` | `auth_token` | Twilio Auth Token | No |
| `twilio` | `from_number` | Twilio phone number | No |
| `twilio` | `test_to_number` | Override recipient for testing | No |
| `gmail` | `username` | Gmail address | No |
| `gmail` | `app_password` | Gmail App Password | No |

### Environment Variables

These are set automatically by `start.sh` from `vars.yaml`:

| Variable | Description |
|----------|-------------|
| `GOOGLE_API_KEY` | Google AI API key |
| `TWILIO_ACCOUNT_SID` | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | Twilio Auth Token |
| `TWILIO_FROM_NUMBER` | Twilio phone number |
| `TWILIO_TEST_TO_NUMBER` | Test override number |
| `GMAIL_USERNAME` | Gmail address |
| `GMAIL_APP_PASSWORD` | Gmail App Password |

## Viewing Logs

**All services:**
```bash
docker-compose logs -f
```

**Specific service:**
```bash
docker-compose logs -f orchestrator
docker-compose logs -f policy
```

## Health Checks

```bash
# Orchestrator
curl http://localhost:8080/api/health

# Individual MCP servers
curl http://localhost:8081/actuator/health  # Impact Analyst
curl http://localhost:8082/actuator/health  # Environment
curl http://localhost:8083/actuator/health  # Policy
curl http://localhost:8084/actuator/health  # Services
curl http://localhost:8085/actuator/health  # Communications
```

## Troubleshooting

### MCP Server Not Responding

1. Check if the server is running:
   ```bash
   docker-compose ps
   ```

2. Check logs:
   ```bash
   docker-compose logs impact-analyst
   ```

### Database Connection Issues

1. Verify PostgreSQL is running:
   ```bash
   docker-compose ps postgres
   ```

2. Manually connect:
   ```bash
   psql -h localhost -p 5432 -U crash -d crash
   ```

### RabbitMQ Issues

1. Check management UI: http://localhost:15672 (guest/guest)

2. Verify exchanges and queues are created

### SMS Not Sending

1. Check Twilio credentials in `vars.yaml`
2. Use `test_to_number` to redirect to your phone
3. Check Communications service logs

### Email Not Sending

1. Verify Gmail App Password (not regular password)
2. Enable "Less secure app access" or use App Password
3. Check Communications service logs

## Stopping the System

```bash
docker-compose down
```

To also remove volumes (database data):
```bash
docker-compose down -v
```

---

*See [BUILD.md](BUILD.md) for extending the system and [AGENTIC-ARCHITECTURE.md](AGENTIC-ARCHITECTURE.md) for architecture details.*
