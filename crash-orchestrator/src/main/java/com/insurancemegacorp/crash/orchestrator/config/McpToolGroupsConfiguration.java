package com.insurancemegacorp.crash.orchestrator.config;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.core.ToolGroupPermission;
import com.embabel.agent.tools.mcp.McpToolGroup;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * Configuration class that exposes MCP server tools as Embabel ToolGroups.
 *
 * This bridges Spring AI MCP clients with Embabel's tool resolution system,
 * allowing @Action methods to access MCP tools via toolGroups parameter.
 *
 * IMPORTANT: The second parameter to ToolGroupDescription.Companion.invoke(description, role)
 * sets the ROLE which must match the toolGroups value in @Action annotations.
 * For example, @Action(toolGroups = {"impact-analyst-tools"}) requires
 * ToolGroupDescription with role "impact-analyst-tools".
 */
@Configuration
public class McpToolGroupsConfiguration {

    static {
        System.out.println("🔧🔧🔧 McpToolGroupsConfiguration CLASS LOADED");
    }

    private static final Logger log = LoggerFactory.getLogger(McpToolGroupsConfiguration.class);

    private final List<McpSyncClient> mcpSyncClients;

    public McpToolGroupsConfiguration(List<McpSyncClient> mcpSyncClients) {
        log.warn("🔧🔧🔧 McpToolGroupsConfiguration constructor called with {} MCP clients",
                 mcpSyncClients != null ? mcpSyncClients.size() : 0);
        System.out.println("🔧🔧🔧 McpToolGroupsConfiguration constructor called with " +
                          (mcpSyncClients != null ? mcpSyncClients.size() : 0) + " MCP clients");
        this.mcpSyncClients = mcpSyncClients;
    }

    @Bean
    public ToolGroup impactAnalystToolGroup() {
        log.info("🔧 Creating impactAnalystToolGroup bean");
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Analyzes vehicle telemetry data to classify accident severity and impact type. " +
                "Provides tools to analyze g-force, accelerometer data, and determine if accident threshold is met.",
                "impact-analyst-tools"  // role - must match @Action toolGroups value
            ),
            "impact-analyst-tools",      // name
            "CRASH-MCP",                  // provider
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("analyzeImpact") ||
                       toolName.equals("isAccidentDetected");
            }
        );
    }

    @Bean
    public ToolGroup environmentToolGroup() {
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Gathers environmental context for accident locations including weather, " +
                "location details, and road conditions.",
                "environment-tools"
            ),
            "environment-tools",
            "CRASH-MCP",
            Set.of(ToolGroupPermission.INTERNET_ACCESS, ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("getWeather") ||
                       toolName.equals("reverseGeocode") ||
                       toolName.equals("getRoadConditions") ||
                       toolName.equals("getFullEnvironmentContext");
            }
        );
    }

    @Bean
    public ToolGroup policyToolGroup() {
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Retrieves insurance policy, driver, and vehicle information. " +
                "Provides access to coverage details, driver profiles, and vehicle data.",
                "policy-tools"
            ),
            "policy-tools",
            "CRASH-MCP",
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("lookupPolicy") ||
                       toolName.equals("getDriverProfile") ||
                       toolName.equals("getVehicleDetails") ||
                       toolName.equals("getFullPolicyInfo");
            }
        );
    }

    @Bean
    public ToolGroup servicesToolGroup() {
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Finds nearby services relevant to accident response including tow trucks, " +
                "body shops, medical facilities, and rental cars.",
                "services-tools"
            ),
            "services-tools",
            "CRASH-MCP",
            Set.of(ToolGroupPermission.INTERNET_ACCESS, ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("findBodyShops") ||
                       toolName.equals("findTowServices") ||
                       toolName.equals("findMedicalFacilities") ||
                       toolName.equals("findRentalCars") ||
                       toolName.equals("getAllNearbyServices");
            }
        );
    }

    @Bean
    public ToolGroup communicationsToolGroup() {
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Handles driver and adjuster communications including SMS, push notifications, " +
                "and roadside assistance dispatch.",
                "communications-tools"
            ),
            "communications-tools",
            "CRASH-MCP",
            Set.of(ToolGroupPermission.INTERNET_ACCESS, ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("sendSms") ||
                       toolName.equals("sendPushNotification") ||
                       toolName.equals("notifyAdjuster") ||
                       toolName.equals("dispatchRoadsideAssistance") ||
                       toolName.equals("logCommunicationEvent") ||
                       toolName.equals("getFullCommunicationsStatus");
            }
        );
    }
}
