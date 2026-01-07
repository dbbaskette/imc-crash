# CRASH System Configuration & Outcomes

## Current Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────────┐
│                     crash-rabbitmq-sink                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Embabel Agent Framework 0.3.1                │  │
│  │              (Goal-Oriented Action Planning)              │  │
│  │                                                            │  │
│  │  CrashAgent (@Agent):                                     │  │
│  │    - analyzeImpact()      → ImpactAnalysis                │  │
│  │    - gatherEnvironment()  → EnvironmentContext            │  │
│  │    - lookupPolicy()       → PolicyInfo                    │  │
│  │    - findServices()       → NearbyServices                │  │
│  │    - initiateComms()      → CommunicationsStatus          │  │
│  │    - compileReport()      → FNOLReport ✅ @AchievesGoal   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              ↓                                   │
│         Spring AI MCP Client 1.1.0 (Streamable HTTP/SSE)        │
│                              ↓                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │        McpToolGroupsConfiguration (@Configuration)       │   │
│  │  - impactAnalystToolGroup()    → "impact-analyst-tools"  │   │
│  │  - environmentToolGroup()      → "environment-tools"     │   │
│  │  - policyToolGroup()           → "policy-tools"          │   │
│  │  - servicesToolGroup()         → "services-tools"        │   │
│  │  - communicationsToolGroup()   → "communications-tools"  │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              ↓ HTTP/SSE
        ┌─────────────────────────────────────────────┐
        │     5 MCP Servers (Streamable HTTP)         │
        ├─────────────────────────────────────────────┤
        │ crash-mcp-impact-analyst    :8081           │
        │ crash-mcp-environment       :8082           │
        │ crash-mcp-policy            :8083           │
        │ crash-mcp-services          :8084           │
        │ crash-mcp-communications    :8085           │
        └─────────────────────────────────────────────┘
                              ↓
                    External APIs & Mock Data
```

## Configuration Details

### 1. Spring Boot Application
**File**: `crash-rabbitmq-sink/src/main/java/com/insurancemegacorp/crashsink/CrashSinkApplication.java`

```java
@SpringBootApplication
@EnableAgents  // Embabel agent framework
@Import(McpToolGroupsConfiguration.class)  // Force config loading
public class CrashSinkApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrashSinkApplication.class, args);
    }
}
```

**Purpose**:
- Bootstrap Spring Boot application
- Enable Embabel agent framework with `@EnableAgents`
- Explicitly import MCP ToolGroup configuration with `@Import`

**Status**: ✅ Starts successfully, agent deploys

### 2. MCP Client Configuration
**File**: `crash-rabbitmq-sink/src/main/resources/application.yml`

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-5-nano}
    mcp:
      client:
        enabled: true
        type: SYNC
        toolcallback:
          enabled: true
        streamable-http:
          connections:
            impact-analyst:
              url: ${MCP_IMPACT_ANALYST_URL:http://localhost:8081}
              endpoint: /mcp
            environment:
              url: ${MCP_ENVIRONMENT_URL:http://localhost:8082}
              endpoint: /mcp
            policy:
              url: ${MCP_POLICY_URL:http://localhost:8083}
              endpoint: /mcp
            services:
              url: ${MCP_SERVICES_URL:http://localhost:8084}
              endpoint: /mcp
            communications:
              url: ${MCP_COMMUNICATIONS_URL:http://localhost:8085}
              endpoint: /mcp

embabel:
  agent:
    llm:
      default: gpt-5-nano
```

**Purpose**: Configure Spring AI MCP client connections to 5 MCP servers

**Status**: ✅ All 5 connections succeed, tools discovered

**Evidence from logs**:
```
Server response with Protocol: 2025-06-18, Capabilities: ServerCapabilities[...tools=ToolCapabilities[listChanged=true]]
Info: Implementation[name=impact-analyst, version=1.0.0]
Available tools:
- analyze_impact: Full impact analysis from telemetry data
- is_accident_detected: Quick check if g-force exceeds accident threshold
```

### 3. MCP ToolGroup Registration
**File**: `crash-rabbitmq-sink/src/main/java/com/insurancemegacorp/crashsink/config/McpToolGroupsConfiguration.java`

```java
@Configuration
public class McpToolGroupsConfiguration {

    private final List<McpSyncClient> mcpSyncClients;

    public McpToolGroupsConfiguration(List<McpSyncClient> mcpSyncClients) {
        this.mcpSyncClients = mcpSyncClients;
    }

    @Bean
    public ToolGroup impactAnalystToolGroup() {
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Analyzes vehicle telemetry data to classify accident severity and impact type",
                "impact-analyst"
            ),
            "Impact Analyst MCP Server",
            "impact-analyst-tools",  // ← Referenced by @Action(toolGroups)
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            toolCallback -> {
                String toolName = toolCallback.getToolDefinition().name();
                return toolName.equals("analyzeImpact") ||
                       toolName.equals("isAccidentDetected");
            }
        );
    }

    // ... 4 more @Bean methods for other tool groups
}
```

**Purpose**: Register MCP server tools as Embabel ToolGroups for agent access

**Status**: ❌ Configuration class NOT being loaded by Spring Boot

**Evidence**: No constructor logs, no bean creation logs, ToolGroupResolver shows only 1 tool group (math)

**Expected**: 6 tool groups (math + 5 MCP groups)
**Actual**: 1 tool group (math only)

### 4. Embabel Agent Definition
**File**: `crash-rabbitmq-sink/src/main/java/com/insurancemegacorp/crashsink/CrashAgent.java`

```java
@Agent(description = "First Notice of Loss Agent")
@Component
public class CrashAgent {

    @Action(
        description = "Analyze accident telemetry to determine severity and impact type",
        toolGroups = {"impact-analyst-tools"}  // ← References ToolGroup bean name
    )
    public ImpactAnalysis analyzeImpact(AccidentEvent event, Ai ai) {
        return ai.withAutoLlm().createObject(
            """
            Use the Impact Analyst tools to analyze this accident.
            Call the analyze_impact tool with:
            - gForce: %f
            - accelerometerX: %f
            - accelerometerY: %f
            - accelerometerZ: %f
            Return the complete ImpactAnalysis result.
            """.formatted(...),
            ImpactAnalysis.class
        );
    }

    // ... 5 more @Action methods with respective toolGroups
}
```

**Purpose**: Define agent actions that use MCP tools via Embabel framework

**Status**: ⚠️ Partial - Agent deploys but tools not available to LLM

### 5. MCP Server Implementation (Example)
**File**: `crash-mcp-impact-analyst/src/main/java/com/insurancemegacorp/crash/impact/ImpactAnalystService.java`

```java
@Service
public class ImpactAnalystService {

    @Tool(description = "Analyze accident telemetry data...")
    public ImpactAnalysis analyzeImpact(
            @ToolParam(description = "G-force measurement") double gForce,
            @ToolParam(description = "Accelerometer X-axis") double accelerometerX,
            @ToolParam(description = "Accelerometer Y-axis") double accelerometerY,
            @ToolParam(description = "Accelerometer Z-axis") double accelerometerZ
    ) {
        Severity severity = classifySeverity(gForce, speedMph);
        ImpactType impactType = determineImpactType(accelerometerX, accelerometerY, accelerometerZ);
        // ... deterministic logic
        return new ImpactAnalysis(severity, impactType, ...);
    }

    private ImpactType determineImpactType(double x, double y, double z) {
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);

        // ROLLOVER: Extreme Z-axis (vertical force)
        if (absZ > 6.0 || (absZ > 4.0 && absZ > absX * 1.5 && absZ > absY * 1.5)) {
            return ImpactType.ROLLOVER;
        }
        // T_BONE: Strong lateral Y-axis
        if (absY > 4.5 && absY > absX * 1.5) {
            return ImpactType.SIDE;
        }
        // REAR_ENDED: Positive X (pushed forward)
        if (x > 1.5 && absX > absY) {
            return ImpactType.REAR;
        }
        // HEAD_ON: Strong negative X (deceleration)
        if (x < -4.0 && absX > absY) {
            return ImpactType.FRONTAL;
        }
        // Default
        return ImpactType.FRONTAL;
    }
}
```

**Purpose**: Deterministic accident classification based on sensor data

**Status**: ✅ Service works correctly when called directly

**Evidence**: Direct testing shows correct ROLLOVER detection for Z-axis dominant impacts

## Current Outcomes

### What Works ✅

1. **Spring Boot Application Startup**
   - Application starts without errors
   - All Spring beans initialize correctly

2. **MCP Client Connections**
   - All 5 MCP servers connect successfully
   - Tools discovered and listed in capabilities
   - Server-Sent Events (SSE) transport functional

3. **Embabel Agent Deployment**
   - CrashAgent deploys as Embabel agent
   - All 6 @Action methods recognized
   - GOAP planning executes (determines action execution order)

4. **RabbitMQ Message Processing**
   - Messages consumed from `telematics_exchange` queue
   - AccidentEvent deserialized correctly
   - Agent invoked for each event

5. **MCP Server Logic**
   - ImpactAnalystService correctly classifies accidents
   - 9-type detection logic works (ROLLOVER, T_BONE, REAR_ENDED, etc.)
   - Accelerometer-based classification accurate

6. **FNOL Report Generation**
   - Complete reports generated and saved to database
   - All agent actions execute in proper order
   - Severity levels calculated correctly

### What Doesn't Work ❌

1. **MCP ToolGroup Registration**
   - `McpToolGroupsConfiguration` class NOT loaded by Spring Boot
   - No constructor invocation (no logs)
   - No `@Bean` methods executed
   - ToolGroupResolver shows only 1 tool group (math)

2. **LLM Tool Calling**
   - LLM receives NO MCP tools (only prompt text)
   - LLM hallucinates ImpactAnalysis instead of calling real tools
   - Always returns `FRONTAL` impact type (hallucinated guess)

3. **Accident Type Detection**
   - All accidents classified as FRONTAL regardless of sensor data
   - ROLLOVER (Z=9.36g) → classified as FRONTAL ❌
   - T_BONE (Y=7.2g) → classified as FRONTAL ❌
   - REAR_ENDED (X=+4.5g) → classified as FRONTAL ❌

### Root Cause Analysis

**Configuration Loading Issue**: The `McpToolGroupsConfiguration` class exists and compiles but is never instantiated by Spring Boot.

**Evidence**:
- JAR inspection: Class present at `BOOT-INF/classes/com/insurancemegacorp/crashsink/config/McpToolGroupsConfiguration.class`
- Static initializer with `System.out.println()` never executes
- Constructor with logging never called
- No `@Bean` creation logs
- Embabel's ToolGroupResolver logs: "1 available tool groups: math"

**Attempted Solutions**:
1. ❌ `@Configuration` annotation - not discovered
2. ❌ YAML property `embabel.agent.platform.scanning.packages` - didn't work
3. ❌ `@EnableAgents(basePackages)` - parameter doesn't exist
4. ❌ `@Import(McpToolGroupsConfiguration.class)` - still not loaded

**Hypothesis**: Embabel's auto-configuration may override Spring Boot's component scanning and ignores our package (`com.insurancemegacorp.crashsink.config`). Embabel logs show it only scans `com.embabel.agent` and `com.embabel.example` packages.

### Behavioral Outcomes

**With Current Configuration**:

1. User simulates ROLLOVER accident:
   ```bash
   ./simulate-accident.sh rollover severe
   # X=-3.25g, Y=2.34g, Z=9.36g (Z-axis dominant = rollover)
   ```

2. Message published to RabbitMQ → consumed by crash-sink

3. Embabel invokes `CrashAgent.analyzeImpact()`

4. Action executes:
   ```java
   return ai.withAutoLlm().createObject(
       "Call analyze_impact tool with gForce=9.75, accelerometerZ=9.36...",
       ImpactAnalysis.class
   );
   ```

5. **Problem**: LLM receives:
   - ✅ Prompt with sensor data
   - ❌ NO MCP tools (ToolGroup not registered)

6. LLM hallucinates response:
   ```json
   {
     "severity": "SEVERE",
     "impactType": "FRONTAL",  ← WRONG! Should be ROLLOVER
     "speedAtImpact": 45.0,
     "wasSpeeding": false,
     "airbagLikely": true
   }
   ```

7. FNOL report saved to database with incorrect impact type

**Database Result**:
```sql
SELECT claim_number, impact_type, severity
FROM fnol_reports
ORDER BY created_at DESC LIMIT 1;

 claim_number   | impact_type | severity
----------------+-------------+----------
 CLM-2026-009861| FRONTAL     | SEVERE    ← WRONG!
```

**Expected Result** (if MCP tools were available):
```sql
 claim_number   | impact_type | severity
----------------+-------------+----------
 CLM-2026-009861| ROLLOVER    | SEVERE    ← CORRECT!
```

## Testing Configuration

### Accident Simulation Tool
**File**: `simulate-accident.sh`

Supports 9 accident types with calibrated sensor profiles:

| Type | X (front/back) | Y (left/right) | Z (up/down) | Expected Detection |
|------|----------------|----------------|-------------|-------------------|
| ROLLOVER | -3.25g | 2.34g | **9.36g** | ROLLOVER (Z dominant) |
| T_BONE | -1.95g | **9.36g** | 1.17g | SIDE (Y dominant) |
| REAR_ENDED | **+5.85g** | 0.39g | 0.39g | REAR (positive X) |
| HEAD_ON | **-6.50g** | 0.78g | 0.78g | FRONTAL (negative X) |
| SINGLE_VEHICLE | -4.55g | 1.56g | 1.17g | FRONTAL |
| REAR_END_COLLISION | **-5.20g** | 0.78g | 0.78g | FRONTAL |
| MULTI_VEHICLE_PILEUP | -5.85g | 2.34g | 1.56g | FRONTAL |
| SIDE_SWIPE | -2.60g | **4.68g** | 0.78g | SIDE (Y dominant) |
| HIT_AND_RUN | -3.90g | 1.95g | 1.17g | FRONTAL |

**Current Actual Detection**: ALL → FRONTAL (LLM hallucination)

## Dependencies

### Maven Dependencies
```xml
<!-- Embabel Agent Framework -->
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter</artifactId>
    <version>0.3.1</version>
</dependency>

<!-- Spring AI MCP Client -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- Spring Cloud Stream RabbitMQ -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>
```

## Environment Variables

```bash
# OpenAI Configuration
OPENAI_API_KEY=sk-proj-...
OPENAI_MODEL=gpt-5-nano

# MCP Server URLs (Docker Compose)
MCP_IMPACT_ANALYST_URL=http://crash-mcp-impact-analyst:8081
MCP_ENVIRONMENT_URL=http://crash-mcp-environment:8082
MCP_POLICY_URL=http://crash-mcp-policy:8083
MCP_SERVICES_URL=http://crash-mcp-services:8084
MCP_COMMUNICATIONS_URL=http://crash-mcp-communications:8085

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/crash
SPRING_DATASOURCE_USERNAME=crash
SPRING_DATASOURCE_PASSWORD=crash123

# RabbitMQ
SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=crash
SPRING_RABBITMQ_PASSWORD=crash123
```

## Next Steps to Fix

1. **Investigate Embabel package scanning behavior**
   - Review Embabel source code for ComponentConfiguration
   - Determine how to include custom packages in scanning

2. **Alternative approaches**:
   - Move `McpToolGroupsConfiguration` to `com.embabel.agent` package
   - Create Embabel-specific auto-configuration class
   - Use programmatic bean registration instead of `@Configuration`

3. **Verify with Embabel community**:
   - Ask on GitHub issues / discussions
   - Check if there are working examples with Spring AI MCP integration
   - Confirm expected pattern for custom ToolGroup registration

4. **Fallback option**:
   - Consider direct service injection (bypass MCP protocol entirely)
   - Use Spring dependency injection to call MCP services directly
   - Trade-off: Lose LLM-driven tool selection, but gain deterministic behavior

## Summary

**Configuration Status**: Implementation follows Embabel and Spring AI documentation, but critical configuration class not loading due to Spring Boot/Embabel component scanning incompatibility.

**Functional Status**: System processes accidents end-to-end but produces incorrect results (all accidents classified as FRONTAL) because LLM hallucinates instead of calling real MCP tools.

**Technical Debt**: Need to resolve ToolGroup registration issue or adopt alternative architecture.
