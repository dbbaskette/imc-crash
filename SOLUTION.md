# Solution: Embabel + Spring AI MCP Integration Issue

## Problem Summary

The CRASH system has an integration gap between:
- **Embabel 0.3.1** (GOAP agent framework)
- **Spring AI MCP 1.1.0** (Model Context Protocol for tool calling)

### What's Broken

1. MCP servers (impact-analyst, environment, etc.) have `@Tool` methods but they're not being discovered
2. Spring AI MCP clients connect to servers but report "Available tools: []" (empty)
3. Embabel's `Ai.createObject()` calls LLM without access to MCP tools
4. LLM hallucinates responses instead of calling real MCP tools
5. Result: All accidents classified as FRONTAL regardless of sensor data

### Evidence

From OpenAI API logs:
- Model used: `gpt-4.1-mini-2025-04-14` (fallback from non-existent gpt-5-nano)
- Prompt: "Use the Environment Agent tools... Call get_full_environment_context..."
- Response: **Hallucinated JSON** (made-up addresses, weather, etc.)
- **No tool calls executed**

From crash-sink logs:
```
Server response ... tools=ToolCapabilities[listChanged=true] ...
Available tools:
<EMPTY>
```

MCP servers say they have tools, but none are listed!

## Root Cause

**MCP tools were not registered as Embabel ToolGroups.**

Embabel DOES integrate with Spring AI MCP clients, but requires explicit ToolGroup configuration:
1. Spring AI MCP clients connect successfully and discover tools ✅
2. But MCP tools must be exposed as `ToolGroup` beans via `@Configuration` class ❌ (missing)
3. @Action methods must specify `toolGroups` parameter to access tools ❌ (missing)

Without ToolGroup registration, Embabel's ToolGroupResolver only sees built-in tools (like math).

When `CrashAgent.analyzeImpact()` calls `ai.withAutoLlm().createObject()`:
- ❌ NO MCP tools available (not registered as ToolGroups)
- ✅ Only the prompt text
- Result: LLM hallucinates ImpactAnalysis object by inferring from prompt data

## Solution

**Register MCP tools as Embabel ToolGroups (IMPLEMENTED)**

Created `McpToolGroupsConfiguration` class that:
1. Receives `List<McpSyncClient>` via Spring constructor injection
2. Creates `@Bean` for each MCP server's tools wrapped in `McpToolGroup`
3. Filters tools by name to expose only relevant tools per group
4. Assigns descriptive metadata and permissions

Updated `CrashAgent` @Action methods to specify `toolGroups` parameter matching the ToolGroup bean names.

**Implementation:**
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
                "Analyzes vehicle telemetry data to classify accident severity...",
                "impact-analyst"
            ),
            "Impact Analyst MCP Server",
            "impact-analyst-tools",  // Must match toolGroups in @Action
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            toolCallback -> {
                String toolName = toolCallback.getToolDefinition().name();
                return toolName.equals("analyzeImpact") ||
                       toolName.equals("isAccidentDetected");
            }
        );
    }
    // ... 4 more ToolGroups for other MCP servers
}
```

**Agent Usage:**
```java
@Action(
    description = "Analyze accident telemetry to determine severity and impact type",
    toolGroups = {"impact-analyst-tools"}  // References ToolGroup bean
)
public ImpactAnalysis analyzeImpact(AccidentEvent event, Ai ai) {
    return ai.withAutoLlm().createObject("""
        Use the Impact Analyst tools to analyze this accident.
        Call the analyze_impact tool with...
        """, ImpactAnalysis.class);
}
```

Now when `ai.createObject()` is called, the LLM receives the MCP tools and can execute real tool calls instead of hallucinating.

## Implementation Status

**✅ COMPLETED:**
1. Created `McpToolGroupsConfiguration` with 5 ToolGroup beans
2. Updated all @Action methods in CrashAgent with `toolGroups` parameter
3. Verified crash-mcp-impact-analyst is functioning Streamable HTTP MCP server

**⏳ NEXT STEPS:**
1. Rebuild and restart crash-rabbitmq-sink service
2. Verify ToolGroupResolver logs show 6 tool groups (math + 5 MCP groups)
3. Test ROLLOVER detection works correctly with real MCP tool calls
4. Run comprehensive test suite with simulate-accident.sh for all 9 accident types

## Testing Plan

Once fixed, run:
```bash
./test-accident-detection.sh  # 27 tests (9 types × 3 severities)
```

Expected results:
- ROLLOVER (Z-axis dominant) → ImpactType.ROLLOVER
- T_BONE (Y-axis dominant) → ImpactType.SIDE
- REAR_ENDED (positive X) → ImpactType.REAR
- HEAD_ON (extreme negative X) → ImpactType.FRONTAL
- etc.

## Files Affected

- `crash-rabbitmq-sink/src/main/java/com/insurancemegacorp/crashsink/CrashAgent.java`
- `crash-rabbitmq-sink/src/main/resources/application.yml`
- All 5 MCP server modules (if fixing @Tool discovery)
