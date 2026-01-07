# CRASH Integration Plan: RabbitMQ FNOL Sink

## Overview

Transform the CRASH orchestrator into a Spring Cloud Stream sink that consumes vehicle accident events from RabbitMQ, processes them through the Embabel agent pipeline, and outputs FNOL reports to both a database and an output queue.

---

## Phase 1: Project Setup ✅ COMPLETE

- [x] **1.1** Create new module directory `crash-rabbitmq-sink/`
- [x] **1.2** Create `crash-rabbitmq-sink/pom.xml` with Spring Cloud Stream + RabbitMQ dependencies
- [x] **1.3** Add `crash-rabbitmq-sink` to parent `pom.xml` modules list
- [x] **1.4** Create basic application class `CrashSinkApplication.java`
- [x] **1.5** Create `application.yml` with local and cloud profiles
- [x] **1.6** Verify module builds with `mvn clean compile`

---

## Phase 2: Message Ingestion ✅ COMPLETE

- [x] **2.1** Create `TelemetryMessage.java` record matching 35-field flat JSON schema
- [x] **2.2** Create `TelemetryToAccidentMapper.java` to transform TelemetryMessage → AccidentEvent
- [x] **2.3** Create `CrashSink.java` with Spring Cloud Stream `Function<String, String>` consumer
- [x] **2.4** Configure RabbitMQ input binding for tap queue
- [x] **2.5** Test message deserialization with sample JSON

---

## Phase 3: FNOL Processing Integration ✅ COMPLETE

### 3.A: Agent Framework Setup ✅ COMPLETE
- [x] **3.A.1** Copy `CrashAgent.java` from orchestrator to crash-rabbitmq-sink
- [x] **3.A.2** Configure MCP client for Streamable HTTP transport in `application.yml`
- [x] **3.A.3** Wire up `AgentPlatform` bean (auto-configured by Embabel starter)
- [x] **3.A.4** Create `FnolService.java` to call CrashAgent from CrashSink
- [x] **3.A.5** Test basic agent invocation - **SUCCESS** (28 seconds for full FNOL)

### 3.B: Impact Analyst Agent (:8081) ✅ VERIFIED — 🔧 REAL LOGIC
- [x] **3.B.1** Impact Analyst MCP server accessible via Streamable HTTP
- [x] **3.B.2** `analyzeImpact` action tested with MODERATE accident
- [x] **3.B.3** `ImpactAnalysis` result verified (severity=MODERATE)
- [x] **3.B.4** Error handling via Embabel framework
- **Implementation:** Real algorithmic analysis from accelerometer/g-force data (configurable thresholds)

### 3.C: Environment Agent (:8082) ✅ COMPLETE — 🌐 REAL API + 🎭 SIMULATED
- [x] **3.C.1** Verify Environment MCP server is accessible from sink
- [x] **3.C.2** Integrate Open-Meteo API for real weather data (no API key required)
- [x] **3.C.3** Verify `EnvironmentContext` result (weather, location, road conditions)
- [x] **3.C.4** Handle error cases with fallback weather data
- **Implementation:**
  - 🌐 **REAL:** Weather via Open-Meteo API (free, no key required)
  - 🎭 **SIMULATED:** Geocoding (random addresses), Road conditions (inferred from weather)
  - **Production:** Could integrate Nominatim for geocoding, DOT APIs for road conditions

### 3.D: Policy Agent (:8083) ✅ VERIFIED — 🎭 SIMULATED
- [x] **3.D.1** Policy MCP server accessible via Streamable HTTP
- [x] **3.D.2** `lookupPolicy` action tested - returned PolicyInfo with driver info
- [x] **3.D.3** `PolicyInfo` result verified (driver, coverage details)
- [x] **3.D.4** Error handling via Embabel framework
- **Implementation:** Simulated in-memory database (3 hardcoded policies for demo)
- **Production:** Would connect to insurance company's policy management system

### 3.E: Services Agent (:8084) ✅ VERIFIED — 🎭 SIMULATED
- [x] **3.E.1** Services MCP server accessible via Streamable HTTP
- [x] **3.E.2** `findServices` action tested with severity=MODERATE
- [x] **3.E.3** `NearbyServices` result verified
- [x] **3.E.4** Error handling via Embabel framework
- **Implementation:** Simulated static data (hardcoded body shops, tow services, hospitals)
- **Production:** Would connect to Google Places API or similar

### 3.F: Communications Agent (:8085) ✅ VERIFIED — 🎭 SIMULATED
- [x] **3.F.1** Communications MCP server accessible via Streamable HTTP
- [x] **3.F.2** `initiateComms` action tested - claim generated
- [x] **3.F.3** `CommunicationsStatus` result verified
- [x] **3.F.4** Error handling via Embabel framework
- **Implementation:** Simulated SMS/push/adjuster notifications (returns success, logs to memory)
- **Production:** Would connect to Twilio, SendGrid, internal notification systems

### 3.G: Full FNOL Pipeline ✅ COMPLETE
- [x] **3.G.1** `compileReport` tested with all agent results
- [x] **3.G.2** Complete `FNOLReport` generated successfully
- [x] **3.G.3** End-to-end test: REST API → FNOL report
- [x] **3.G.4** Add Jackson JavaTimeModule for java.time serialization
- [x] **3.G.5** Test with multiple severity levels

**Test Results:**
- Minor accident (g-force 3.8): CLM-2026-753916, SEVERE, ~30s processing
- Moderate accident (g-force 4.2): CLM-2026-509362, MODERATE, ~90s processing
- Severe accident (g-force 5.5): CLM-2026-839254, SEVERE, ~2min processing
- All 5 MCP agents successfully invoked via Streamable HTTP
- GOAP planning working correctly with parallel action execution

---

## Phase 4: Database Persistence

- [ ] **4.1** Create `schema.sql` with `fnol_reports` table DDL
- [ ] **4.2** Create `FnolEntity.java` JPA entity
- [ ] **4.3** Create `FnolRepository.java` Spring Data repository
- [ ] **4.4** Add database persistence to CrashSink after FNOL generation
- [ ] **4.5** Configure PostgreSQL datasource in `application.yml`
- [ ] **4.6** Test database write with sample FNOL

---

## Phase 5: Output Queue

- [ ] **5.1** Configure output binding `fnolOutput-out-0` to `fnol_reports` exchange
- [ ] **5.2** Add routing key expression based on severity (`fnol.MINOR`, `fnol.MODERATE`, `fnol.SEVERE`)
- [ ] **5.3** Publish FNOLReport JSON to output queue after processing
- [ ] **5.4** Test end-to-end: input message → FNOL → DB + output queue

---

## Phase 6: Docker Integration ✅ COMPLETE

- [x] **6.1** Create `crash-rabbitmq-sink/Dockerfile`
- [x] **6.2** Update `docker-compose.yml` to add:
  - RabbitMQ service (ports 5672, 15672)
  - PostgreSQL service (port 5432)
  - crash-sink service with dependencies
- [x] **6.3** Configure environment variables for RabbitMQ, PostgreSQL, OpenAI, MCP URLs
- [x] **6.4** Test full Docker Compose stack locally - **9 containers running**
- [x] **6.5** Verify message flow via REST API
- [x] **6.6** Create `simulate-accident.sh` script for local testing

---

## Phase 7: Cloud Foundry / SCDF Integration

- [ ] **7.1** Add SCDF app registration for `imc-crash-sink`
- [ ] **7.2** Update `telemetry-streams.yml`:
  - Replace `vehicle-events-to-log` stream definition
  - Add `vehicle-events-to-crash` stream: `:telemetry-to-processor.imc-telemetry-processor > imc-crash-sink`
- [ ] **7.3** Add deployment properties for crash sink (memory, services, health checks)
- [ ] **7.4** Configure TAS service bindings (RabbitMQ, PostgreSQL, OpenAI credentials)
- [ ] **7.5** Deploy and test in Cloud Foundry environment

---

## Phase 8: Monitoring & Cleanup

- [ ] **8.1** Add Prometheus metrics (messages processed, FNOL generated, errors)
- [x] **8.2** Configure actuator endpoints for health/metrics
- [ ] **8.3** Update README.md with new architecture diagram
- [ ] **8.4** Commit and push changes to GitHub
- [ ] **8.5** Remove old log sink stream definition (if confirmed working)

---

## Architecture Diagrams

### Current Architecture
```
telematics_exchange (fanout)
    ├── telemetry-to-hdfs: → imc-hdfs-sink (archival)
    ├── telemetry-to-processor: → imc-telemetry-processor → vehicle_events
    │                                    ↓ (tap)
    │                           vehicle-events-to-log → log ← TO BE REPLACED
    └── vehicle-events-to-jdbc: vehicle_events → imc-jdbc-consumer → PostgreSQL
```

### Target Architecture
```
telematics_exchange (fanout)
    ├── telemetry-to-hdfs: → imc-hdfs-sink (archival)
    ├── telemetry-to-processor: → imc-telemetry-processor → vehicle_events
    │                                    ↓ (tap)
    │                           vehicle-events-to-crash → imc-crash-sink
    │                                    ↓
    │                           ┌───────┴────────┐
    │                           ↓                ↓
    │                      PostgreSQL      fnol_reports (queue)
    │                     (fnol_reports)        ↓
    │                                     Claims Systems
    └── vehicle-events-to-jdbc: vehicle_events → imc-jdbc-consumer → PostgreSQL
```

### Current Docker Stack (9 containers)
```
┌─────────────────────────────────────────────────────────────────┐
│                     Docker Compose Stack                         │
├─────────────────────────────────────────────────────────────────┤
│  orchestrator:8080    ──┬── MCP Streamable HTTP ──┬── impact-analyst:8081    │
│                         │                          ├── environment:8082       │
│  crash-sink:8086     ──┘                          ├── policy:8083            │
│                                                    ├── services-agent:8084   │
│                                                    └── communications:8085   │
├─────────────────────────────────────────────────────────────────┤
│  rabbitmq:5672/15672          postgres:5432                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Files Reference

### Files Created ✅
| File | Purpose | Status |
|------|---------|--------|
| `crash-rabbitmq-sink/pom.xml` | Module dependencies | ✅ Done |
| `crash-rabbitmq-sink/Dockerfile` | Docker build | ✅ Done |
| `crash-rabbitmq-sink/src/.../CrashSinkApplication.java` | Main class | ✅ Done |
| `crash-rabbitmq-sink/src/.../CrashSink.java` | Stream consumer | ✅ Done |
| `crash-rabbitmq-sink/src/.../CrashAgent.java` | Embabel agent | ✅ Done |
| `crash-rabbitmq-sink/src/.../FnolService.java` | FNOL orchestration | ✅ Done |
| `crash-rabbitmq-sink/src/.../TelemetryMessage.java` | Input message record | ✅ Done |
| `crash-rabbitmq-sink/src/.../TelemetryToAccidentMapper.java` | Message transformer | ✅ Done |
| `crash-rabbitmq-sink/src/resources/application.yml` | Configuration | ✅ Done |
| `simulate-accident.sh` | Test script for RabbitMQ | ✅ Done |
| `AGENTIC-ARCHITECTURE.md` | 101/201 architecture docs | ✅ Done |

### Files To Create (Phase 4)
| File | Purpose | Status |
|------|---------|--------|
| `crash-rabbitmq-sink/src/.../FnolEntity.java` | JPA entity | ⏳ Pending |
| `crash-rabbitmq-sink/src/.../FnolRepository.java` | Data repository | ⏳ Pending |
| `crash-rabbitmq-sink/src/resources/schema.sql` | DB schema | ⏳ Pending |

### Files Modified ✅
| File | Change | Status |
|------|--------|--------|
| `imc-crash/pom.xml` | Add crash-rabbitmq-sink module | ✅ Done |
| `imc-crash/docker-compose.yml` | Add RabbitMQ, PostgreSQL, crash-sink | ✅ Done |
| `crash-mcp-*/application.yml` | Migrated to Streamable HTTP | ✅ Done |
| `crash-orchestrator/application.yml` | Migrated to Streamable HTTP | ✅ Done |

### Files To Modify (Phase 7)
| File | Change | Status |
|------|--------|--------|
| `imc-vehicle-events/.../telemetry-streams.yml` | Replace log sink with crash sink | ⏳ Pending |

---

## Telemetry Message → AccidentEvent Mapping

| Telemetry Field | AccidentEvent Field |
|----------------|---------------------|
| policy_id | policyId |
| vehicle_id | vehicleId |
| driver_id | driverId |
| vin | vin |
| event_time | eventTime |
| speed_mph | speedMph |
| speed_limit_mph | speedLimitMph |
| g_force | gForce |
| gps_latitude | latitude |
| gps_longitude | longitude |
| current_street | currentStreet |
| accelerometer_x | accelerometerX |
| accelerometer_y | accelerometerY |
| accelerometer_z | accelerometerZ |
| gyroscope_x | gyroscopeX |
| gyroscope_y | gyroscopeY |
| gyroscope_z | gyroscopeZ |
| device_battery_level | deviceBatteryLevel |
| device_signal_strength | deviceSignalStrength |

---

## Agent Implementation Status

| Agent | Port | Implementation | Notes |
|-------|------|----------------|-------|
| **Impact Analyst** | 8081 | 🔧 **REAL LOGIC** | Algorithmic severity classification from g-force/accelerometer data |
| **Environment** | 8082 | 🌐 **REAL** + 🎭 **SIMULATED** | Real: Open-Meteo weather API. Simulated: geocoding, road conditions |
| **Policy** | 8083 | 🎭 **SIMULATED** | In-memory hardcoded policies. Production: policy management system |
| **Services** | 8084 | 🎭 **SIMULATED** | Static service data. Production: Google Places API |
| **Communications** | 8085 | 🎭 **SIMULATED** | Mock SMS/push/notifications. Production: Twilio, SendGrid |

**Legend:**
- 🔧 **REAL LOGIC** — Actual business logic implementation
- 🌐 **REAL API** — Calls external APIs for live data
- 🎭 **SIMULATED** — Demo/mock data for development

---

## Progress Summary

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 1: Project Setup | ✅ Complete | Module created and builds |
| Phase 2: Message Ingestion | ✅ Complete | Telemetry parsing working |
| Phase 3: FNOL Processing | ✅ Complete | All 5 MCP agents working via Streamable HTTP |
| Phase 4: Database Persistence | ⏳ Next | Store FNOL reports in PostgreSQL |
| Phase 5: Output Queue | ⏳ Pending | Publish to downstream systems |
| Phase 6: Docker Integration | ✅ Complete | 9 containers running |
| Phase 7: Cloud Foundry | ⏳ Pending | SCDF deployment |
| Phase 8: Monitoring | 🔄 Partial | Actuator configured, metrics pending |
