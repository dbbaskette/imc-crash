# Cloud Foundry Deployment Guide

> **Status: Planned**
>
> This document outlines the Cloud Foundry deployment architecture. Implementation is planned but not yet complete.

## Overview

IMC-CRASH can be deployed to Cloud Foundry (including VMware Tanzu Application Service) as a set of microservices with managed backing services.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Cloud Foundry / TAS                                  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                         Applications                                    │ │
│  │                                                                         │ │
│  │   crash-sink             (2+ instances, scalable)                      │ │
│  │   crash-orchestrator     (1 instance, WebSocket)                       │ │
│  │   crash-impact-analyst   (1+ instances)                                │ │
│  │   crash-environment      (1+ instances)                                │ │
│  │   crash-policy           (1+ instances)                                │ │
│  │   crash-services         (1+ instances)                                │ │
│  │   crash-communications   (1+ instances)                                │ │
│  │   crash-ui               (1+ instances, static)                        │ │
│  │                                                                         │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                      Managed Services                                   │ │
│  │                                                                         │ │
│  │   PostgreSQL Service     (policy data, FNOL persistence, messages)     │ │
│  │   RabbitMQ Service       (telematics message broker)                   │ │
│  │                                                                         │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

External Services:
  - Google AI (Gemini) API — LLM for reasoning and narrative generation
  - OpenStreetMap Nominatim API — Reverse geocoding
  - Open-Meteo Weather API — Weather conditions
  - TomTom Places API — Nearby services (primary)
  - Geoapify Places API — Nearby services (fallback)
  - Twilio SMS API — Driver notifications
  - Gmail SMTP — FNOL email reports
```

## Service Dependencies

| Application | PostgreSQL | RabbitMQ | External APIs |
|-------------|:----------:|:--------:|---------------|
| crash-sink | - | Read | - |
| crash-orchestrator | Write | - | Google AI |
| crash-impact-analyst | - | - | Google AI |
| crash-environment | - | - | Nominatim, Open-Meteo |
| crash-policy | Read | - | - |
| crash-services | - | - | TomTom, Geoapify |
| crash-communications | Write | - | Twilio, Gmail |
| crash-ui | - | - | - |

## Port Assignments

| Application | Port | Notes |
|-------------|------|-------|
| crash-sink | 8086 | RabbitMQ consumer, forwards to orchestrator |
| crash-orchestrator | 8080 | GOAP engine, WebSocket broadcasts |
| crash-impact-analyst | 8081 | MCP server |
| crash-environment | 8082 | MCP server |
| crash-policy | 8083 | MCP server |
| crash-services | 8084 | MCP server |
| crash-communications | 8085 | MCP server + REST API for UI |
| crash-ui | 80 | Static React app (nginx) |

## Managed Services

| CF Service | Plan | Purpose |
|------------|------|---------|
| PostgreSQL | Small+ | Policy data, FNOL reports, demo messages |
| RabbitMQ | Standard | Telematics event broker (fanout exchange) |

## User-Provided Services

Create user-provided services for external API credentials:

```bash
# Google AI (Gemini)
cf cups google-ai -p '{"GOOGLE_API_KEY":"your-key"}'

# Twilio SMS
cf cups twilio -p '{"TWILIO_ACCOUNT_SID":"sid","TWILIO_AUTH_TOKEN":"token","TWILIO_FROM_NUMBER":"+1234567890"}'

# Gmail SMTP
cf cups gmail -p '{"GMAIL_USERNAME":"user@gmail.com","GMAIL_APP_PASSWORD":"app-password","GMAIL_ADJUSTER_EMAIL":"adjuster@company.com"}'

# TomTom Places API
cf cups tomtom -p '{"TOMTOM_API_KEY":"your-key"}'

# Geoapify Places API (fallback)
cf cups geoapify -p '{"GEOAPIFY_API_KEY":"your-key"}'
```

## Manifest (Planned)

```yaml
# manifest.yml
applications:
  # Lightweight RabbitMQ consumer - horizontally scalable
  - name: crash-sink
    path: crash-sink/target/crash-sink.jar
    memory: 512M
    instances: 2
    services:
      - crash-rabbitmq
    env:
      SPRING_PROFILES_ACTIVE: cloud
      ORCHESTRATOR_URL: https://crash-orchestrator.apps.example.com

  # GOAP orchestrator - single instance for WebSocket consistency
  - name: crash-orchestrator
    path: crash-orchestrator/target/crash-orchestrator.jar
    memory: 1G
    instances: 1
    services:
      - crash-postgres
      - google-ai
    env:
      SPRING_PROFILES_ACTIVE: cloud
      MCP_IMPACT_ANALYST_URL: https://crash-impact-analyst.apps.example.com
      MCP_ENVIRONMENT_URL: https://crash-environment.apps.example.com
      MCP_POLICY_URL: https://crash-policy.apps.example.com
      MCP_SERVICES_URL: https://crash-services.apps.example.com
      MCP_COMMUNICATIONS_URL: https://crash-communications.apps.example.com

  # Impact analysis MCP server
  - name: crash-impact-analyst
    path: crash-mcp-impact-analyst/target/crash-mcp-impact-analyst.jar
    memory: 512M
    instances: 1
    services:
      - google-ai

  # Environment context MCP server
  - name: crash-environment
    path: crash-mcp-environment/target/crash-mcp-environment.jar
    memory: 512M
    instances: 1

  # Policy lookup MCP server
  - name: crash-policy
    path: crash-mcp-policy/target/crash-mcp-policy.jar
    memory: 512M
    instances: 1
    services:
      - crash-postgres

  # Services finder MCP server
  - name: crash-services
    path: crash-mcp-services/target/crash-mcp-services.jar
    memory: 512M
    instances: 1
    services:
      - tomtom
      - geoapify

  # Communications MCP server
  - name: crash-communications
    path: crash-mcp-communications/target/crash-mcp-communications.jar
    memory: 512M
    instances: 1
    services:
      - crash-postgres
      - twilio
      - gmail
    env:
      DEMO_MODE: "false"

  # React UI (static build served by nginx buildpack)
  - name: crash-ui
    path: crash-ui/dist
    memory: 64M
    instances: 1
    buildpacks:
      - staticfile_buildpack
    env:
      VITE_API_URL: https://crash-communications.apps.example.com/api
      VITE_WS_URL: wss://crash-orchestrator.apps.example.com/ws
```

## WebSocket Considerations

The orchestrator uses WebSocket for real-time status broadcasts to the UI. In Cloud Foundry:

1. **Single Instance**: Keep orchestrator at 1 instance to avoid WebSocket routing issues
2. **Sticky Sessions**: If scaling, configure session affinity via route services
3. **Timeout**: Increase CF router timeout for long-lived WebSocket connections

```bash
# Increase timeout for WebSocket routes (if using route service)
cf set-env crash-orchestrator GOROUTER_TIMEOUT 300
```

## Build and Deploy

### Quick Start

```bash
# 1. Configure vars.yaml with your CF settings
cp vars.yaml.template vars.yaml
# Edit vars.yaml - set cf.apps_domain, cf.org, cf.space, etc.

# 2. Create managed services (one-time setup)
cf create-service postgresql small crash-postgres
cf create-service rabbitmq standard crash-rabbitmq

# 3. Create user-provided services for API keys (see above)
cf cups google-ai -p '{"GOOGLE_API_KEY":"..."}'
# ... etc

# 4. Build and deploy
./start.sh --cf --build
```

### Manual Deployment

```bash
# Build all Java modules
./mvnw clean package -DskipTests

# Build UI
cd crash-ui && npm ci && npm run build && cd ..

# Deploy using vars.yaml for variable substitution
cf push --vars-file vars.yaml
```

### vars.yaml Configuration

The `vars.yaml` file provides values for manifest variables:

```yaml
cf:
  postgres_service: "crash-postgres"
  rabbitmq_service: "crash-rabbitmq"

demo:
  mode: true  # Set to false for production
```

Routes are auto-generated by CF based on app name and default domain. Internal service communication uses container-to-container networking via `apps.internal` domain.

## Demo Mode vs Production

| Setting | Demo Mode | Production |
|---------|-----------|------------|
| `DEMO_MODE` | `true` | `false` |
| SMS/Email | Stored in DB, shown in UI | Sent via Twilio/Gmail |
| UI Source | REST API from communications | N/A (emails sent externally) |

For demos, set `DEMO_MODE=true` on crash-communications to intercept all messages and display them in the UI instead of sending externally.

## Health Endpoints

All Spring Boot applications expose `/actuator/health` for CF health checks. The UI exposes `/health` via nginx.

## TODO

- [x] Create actual `manifest.yml` file
- [x] Configure Staticfile buildpack for UI
- [x] Add `--cf` flag to start.sh for deployment
- [x] Add CF variables to vars.yaml.template
- [ ] Add `application-cloud.yml` profiles for all services
- [ ] Add java-cfenv for automatic service binding
- [ ] Test WebSocket through CF router
- [ ] Add blue-green deployment scripts
- [ ] Document scaling guidelines
- [ ] Add APM integration (Tanzu Observability, AppDynamics, etc.)

## Related Documentation

- [LOCAL-DEVELOPMENT.md](LOCAL-DEVELOPMENT.md) — Local development with Docker Compose
- [BUILD.md](docs/BUILD.md) — Build guide and configuration reference
- [AGENTIC-ARCHITECTURE.md](docs/AGENTIC-ARCHITECTURE.md) — System architecture details

---

*This document will be updated as Cloud Foundry deployment is implemented.*
