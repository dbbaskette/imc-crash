# Cloud Foundry Deployment Guide

> **Status: Planned**
>
> This document is a placeholder for Cloud Foundry deployment instructions. The implementation is planned but not yet complete.

## Overview

IMC-CRASH can be deployed to Cloud Foundry (including VMware Tanzu Application Service) as a set of microservices with managed backing services.

## Planned Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Cloud Foundry / TAS                             │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Applications                              │   │
│  │                                                              │   │
│  │   crash-orchestrator    (1+ instances)                      │   │
│  │   crash-impact-analyst  (1+ instances)                      │   │
│  │   crash-environment     (1+ instances)                      │   │
│  │   crash-policy          (1+ instances)                      │   │
│  │   crash-services        (1+ instances)                      │   │
│  │   crash-communications  (1+ instances)                      │   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   Managed Services                           │   │
│  │                                                              │   │
│  │   PostgreSQL Service     (policy data + FNOL persistence)   │   │
│  │   RabbitMQ Service       (telematics message broker)        │   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

External Services:
  - Google AI (Gemini) API
  - OpenStreetMap Nominatim API
  - Open-Meteo Weather API
  - Twilio SMS API
  - Gmail SMTP
```

## Planned Services

| CF Service | Purpose |
|------------|---------|
| PostgreSQL | Policy data, FNOL reports |
| RabbitMQ | Telematics event messaging |

## Planned User-Provided Services

| Service | Environment Variables |
|---------|----------------------|
| google-ai | `GOOGLE_API_KEY` |
| twilio | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` |
| gmail | `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD` |

## Manifest Structure (Planned)

```yaml
# manifest.yml (placeholder)
applications:
  - name: crash-orchestrator
    path: crash-orchestrator/target/crash-orchestrator.jar
    memory: 1G
    instances: 1
    services:
      - crash-postgres
      - crash-rabbitmq
      - google-ai
      - gmail
    env:
      SPRING_PROFILES_ACTIVE: cloud

  - name: crash-impact-analyst
    path: crash-mcp-impact-analyst/target/crash-mcp-impact-analyst.jar
    memory: 512M
    services:
      - google-ai

  - name: crash-environment
    path: crash-mcp-environment/target/crash-mcp-environment.jar
    memory: 512M
    services:
      - google-ai

  - name: crash-policy
    path: crash-mcp-policy/target/crash-mcp-policy.jar
    memory: 512M
    services:
      - crash-postgres

  - name: crash-services
    path: crash-mcp-services/target/crash-mcp-services.jar
    memory: 512M

  - name: crash-communications
    path: crash-mcp-communications/target/crash-mcp-communications.jar
    memory: 512M
    services:
      - twilio
      - gmail
```

## TODO

- [ ] Create `manifest.yml` for all services
- [ ] Add Spring Cloud Connectors / java-cfenv for service binding
- [ ] Configure service discovery for MCP endpoints
- [ ] Add health endpoints for CF health checks
- [ ] Create deployment scripts
- [ ] Add scaling configuration
- [ ] Document blue-green deployment process
- [ ] Add logging/metrics integration (PCF Metrics, App Dynamics, etc.)

## Contributing

If you'd like to help implement Cloud Foundry support, please:

1. Review the current Docker Compose configuration
2. Understand the service dependencies
3. Propose changes via pull request

## Related Documentation

- [LOCAL-DEVELOPMENT.md](LOCAL-DEVELOPMENT.md) — Local development with Docker Compose
- [BUILD.md](BUILD.md) — Build guide and configuration reference
- [AGENTIC-ARCHITECTURE.md](AGENTIC-ARCHITECTURE.md) — System architecture details

---

*This document will be updated as Cloud Foundry deployment is implemented.*
