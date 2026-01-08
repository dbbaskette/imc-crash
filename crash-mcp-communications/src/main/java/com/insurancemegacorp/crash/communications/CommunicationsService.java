package com.insurancemegacorp.crash.communications;

import com.insurancemegacorp.crash.domain.CommunicationsStatus;
import com.insurancemegacorp.crash.domain.CommunicationsStatus.CommunicationLog;
import com.insurancemegacorp.crash.domain.CommunicationsStatus.DriverOutreach;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MCP Tools for handling driver and adjuster communications.
 * In a real implementation, these would integrate with Twilio, SendGrid, etc.
 */
@Service
public class CommunicationsService {

    // Track communication history per claim
    private final ConcurrentMap<String, List<CommunicationLog>> communicationLogs = new ConcurrentHashMap<>();

    /**
     * Send an SMS to the driver checking on their welfare.
     */
    @McpTool(description = "Send an SMS message to the driver. " +
                        "Returns confirmation of delivery status.")
    public SmsResult sendSms(
            @McpToolParam(description = "Driver's phone number") 
            String phoneNumber,
            
            @McpToolParam(description = "Message content") 
            String message,
            
            @McpToolParam(description = "Claim reference for tracking") 
            String claimReference
    ) {
        // Simulate SMS sending - in production, call Twilio API
        Instant sentAt = Instant.now();
        boolean delivered = true; // Simulated success
        
        // Log the communication
        logCommunication(claimReference, new CommunicationLog(
            sentAt,
            "SMS",
            "OUTBOUND",
            phoneNumber,
            "SMS sent: " + truncate(message, 50),
            delivered
        ));
        
        return new SmsResult(
            true,
            sentAt,
            "MSG-" + System.currentTimeMillis(),
            delivered ? "DELIVERED" : "PENDING"
        );
    }

    /**
     * Send a push notification to the driver's mobile app.
     */
    @McpTool(description = "Send a push notification through the safe driver app.")
    public PushResult sendPushNotification(
            @McpToolParam(description = "Driver ID for push routing") 
            int driverId,
            
            @McpToolParam(description = "Notification title") 
            String title,
            
            @McpToolParam(description = "Notification body") 
            String body,
            
            @McpToolParam(description = "Claim reference for tracking") 
            String claimReference
    ) {
        Instant sentAt = Instant.now();
        
        logCommunication(claimReference, new CommunicationLog(
            sentAt,
            "PUSH",
            "OUTBOUND",
            "driver:" + driverId,
            title + " - " + truncate(body, 40),
            true
        ));
        
        return new PushResult(
            true,
            sentAt,
            "PUSH-" + System.currentTimeMillis()
        );
    }

    /**
     * Notify a claims adjuster about a new claim.
     */
    @McpTool(description = "Notify a claims adjuster about a new or updated claim.")
    public AdjusterNotification notifyAdjuster(
            @McpToolParam(description = "Claim number") 
            String claimNumber,
            
            @McpToolParam(description = "Accident severity: MINOR, MODERATE, SEVERE") 
            String severity,
            
            @McpToolParam(description = "Brief summary of the incident") 
            String summary
    ) {
        Instant notifiedAt = Instant.now();
        
        // Assign adjuster based on severity
        String assignedAdjuster = severity.equals("SEVERE") 
            ? "Sarah Martinez (Senior Adjuster)"
            : "Mike Johnson (Adjuster)";
        
        String priority = severity.equals("SEVERE") ? "HIGH" : 
                         severity.equals("MODERATE") ? "MEDIUM" : "LOW";
        
        logCommunication(claimNumber, new CommunicationLog(
            notifiedAt,
            "INTERNAL",
            "OUTBOUND",
            assignedAdjuster,
            "Claim assigned: " + priority + " priority - " + truncate(summary, 40),
            true
        ));
        
        return new AdjusterNotification(
            true,
            assignedAdjuster,
            priority,
            notifiedAt
        );
    }

    /**
     * Dispatch roadside assistance.
     */
    @McpTool(description = "Dispatch roadside assistance to the accident location.")
    public RoadsideDispatch dispatchRoadsideAssistance(
            @McpToolParam(description = "Latitude of accident") 
            double latitude,
            
            @McpToolParam(description = "Longitude of accident") 
            double longitude,
            
            @McpToolParam(description = "Driver's phone number for callback") 
            String driverPhone,
            
            @McpToolParam(description = "Claim reference") 
            String claimReference
    ) {
        Instant dispatchedAt = Instant.now();
        int estimatedArrival = 15 + (int)(Math.random() * 20); // 15-35 minutes
        
        logCommunication(claimReference, new CommunicationLog(
            dispatchedAt,
            "DISPATCH",
            "OUTBOUND",
            "Roadside Assistance",
            String.format("Dispatched to %.4f, %.4f - ETA %d min", 
                         latitude, longitude, estimatedArrival),
            true
        ));
        
        return new RoadsideDispatch(
            true,
            "RSA-" + System.currentTimeMillis(),
            dispatchedAt,
            estimatedArrival
        );
    }

    /**
     * Log a communication event.
     */
    @McpTool(description = "Log a communication event for audit trail.")
    public boolean logCommunicationEvent(
            @McpToolParam(description = "Claim reference") 
            String claimReference,
            
            @McpToolParam(description = "Type: SMS, PUSH, EMAIL, CALL, INTERNAL") 
            String type,
            
            @McpToolParam(description = "Direction: OUTBOUND or INBOUND") 
            String direction,
            
            @McpToolParam(description = "Other party (phone, email, or name)") 
            String party,
            
            @McpToolParam(description = "Summary of communication") 
            String summary
    ) {
        logCommunication(claimReference, new CommunicationLog(
            Instant.now(),
            type,
            direction,
            party,
            summary,
            true
        ));
        return true;
    }

    /**
     * Get full communication status for a claim.
     */
    @McpTool(description = "Get the complete communication status for a claim, " +
                        "including driver outreach status and all logged communications.")
    public CommunicationsStatus getFullCommunicationsStatus(
            @McpToolParam(description = "Claim reference") 
            String claimReference,
            
            @McpToolParam(description = "Driver name") 
            String driverName,
            
            @McpToolParam(description = "Driver phone number") 
            String driverPhone,
            
            @McpToolParam(description = "Accident severity: MINOR, MODERATE, SEVERE") 
            String severity
    ) {
        // Generate standard wellness check message
        String smsContent = String.format(
            "Hi %s, we detected a possible accident. Are you okay? " +
            "Reply YES if safe, or call 911 if you need emergency help. " +
            "Your claim has been started automatically. - SafeDrive Insurance",
            driverName.split(" ")[0]
        );
        
        // Send wellness check
        SmsResult smsResult = sendSms(driverPhone, smsContent, claimReference);
        
        // Send push notification
        PushResult pushResult = sendPushNotification(
            claimReference.hashCode() % 10000,
            "Are you okay?",
            "We detected a possible accident. Tap to confirm you're safe.",
            claimReference
        );
        
        // Notify adjuster for moderate+ severity
        boolean adjusterNotified = false;
        String assignedAdjuster = null;
        if (!severity.equals("MINOR")) {
            AdjusterNotification adjusterResult = notifyAdjuster(
                claimReference, 
                severity,
                "Auto-detected accident via telemetry"
            );
            adjusterNotified = adjusterResult.sent();
            assignedAdjuster = adjusterResult.assignedAdjuster();
        }
        
        // Dispatch roadside for severe
        boolean roadsideDispatched = false;
        if (severity.equals("SEVERE")) {
            dispatchRoadsideAssistance(0, 0, driverPhone, claimReference);
            roadsideDispatched = true;
        }
        
        DriverOutreach outreach = new DriverOutreach(
            smsResult.sent(),
            smsResult.sentAt(),
            smsContent,
            pushResult.sent(),
            "PENDING",
            null,
            null
        );
        
        List<CommunicationLog> logs = communicationLogs.getOrDefault(
            claimReference, new ArrayList<>()
        );
        
        return new CommunicationsStatus(
            outreach,
            adjusterNotified,
            assignedAdjuster,
            roadsideDispatched,
            logs
        );
    }

    // Helper methods
    
    private void logCommunication(String claimReference, CommunicationLog log) {
        communicationLogs.computeIfAbsent(claimReference, k -> new ArrayList<>()).add(log);
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // Result records

    public record SmsResult(
        boolean sent,
        Instant sentAt,
        String messageId,
        String status
    ) {}

    public record PushResult(
        boolean sent,
        Instant sentAt,
        String notificationId
    ) {}

    public record AdjusterNotification(
        boolean sent,
        String assignedAdjuster,
        String priority,
        Instant notifiedAt
    ) {}

    public record RoadsideDispatch(
        boolean dispatched,
        String dispatchId,
        Instant dispatchedAt,
        int estimatedArrivalMinutes
    ) {}
}
