package com.insurancemegacorp.crash.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for broadcasting real-time status updates to the UI via WebSocket.
 * Sends agent execution status and customer detection events.
 */
@Service
public class AccidentStatusService {

    private static final Logger log = LoggerFactory.getLogger(AccidentStatusService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public AccidentStatusService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcast agent status update to UI.
     * Sends to /topic/agent-status WebSocket topic.
     *
     * @param agentName Name of the agent (e.g., "impact-analyst", "environment", "policy")
     * @param action Action being performed (e.g., "analyzeImpact", "gatherEnvironment")
     * @param status Status of the action ("STARTED", "COMPLETED", "FAILED")
     */
    public void broadcastAgentStatus(String agentName, String action, String status) {
        Map<String, Object> message = new HashMap<>();
        message.put("agent", agentName);
        message.put("action", action);
        message.put("status", status);
        message.put("timestamp", Instant.now().toString());

        log.info("Broadcasting agent status: agent={}, action={}, status={}", agentName, action, status);

        try {
            messagingTemplate.convertAndSend("/topic/agent-status", message);
            log.info("Broadcast sent to /topic/agent-status");
        } catch (Exception e) {
            log.error("Failed to broadcast agent status: {}", e.getMessage());
        }
    }

    /**
     * Broadcast customer detected event to UI.
     * Sends to /topic/customer-detected WebSocket topic.
     * Used to populate the customer sidebar in real-time.
     *
     * @param claimReference Claim reference number
     * @param policyId Policy ID
     * @param customerName Customer's full name
     * @param phone Customer's phone number
     * @param email Customer's email address
     */
    public void broadcastCustomerDetected(
            String claimReference,
            int policyId,
            String customerName,
            String phone,
            String email) {
        
        Map<String, Object> message = new HashMap<>();
        message.put("claimReference", claimReference);
        message.put("policyId", policyId);
        message.put("customerName", customerName);
        message.put("phone", phone);
        message.put("email", email);
        message.put("timestamp", Instant.now().toString());

        log.info("Broadcasting customer detected: claimRef={}, customer={}", claimReference, customerName);

        try {
            messagingTemplate.convertAndSend("/topic/customer-detected", message);
            log.info("Broadcast sent to /topic/customer-detected");
        } catch (Exception e) {
            log.error("Failed to broadcast customer detected: {}", e.getMessage());
        }
    }

    /**
     * Broadcast generic message to a specific topic.
     * 
     * @param topic WebSocket topic (e.g., "/topic/custom-event")
     * @param message Message payload
     */
    public void broadcast(String topic, Object message) {
        log.debug("Broadcasting to topic: {}", topic);
        
        try {
            messagingTemplate.convertAndSend(topic, message);
        } catch (Exception e) {
            log.error("Failed to broadcast to {}: {}", topic, e.getMessage());
        }
    }
}
