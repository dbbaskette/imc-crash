package com.insurancemegacorp.crash.communications;

import com.insurancemegacorp.crash.communications.model.Message;
import com.insurancemegacorp.crash.communications.repository.MessageRepository;
import com.insurancemegacorp.crash.domain.CommunicationsStatus;
import com.insurancemegacorp.crash.domain.CommunicationsStatus.CommunicationLog;
import com.insurancemegacorp.crash.domain.CommunicationsStatus.DriverOutreach;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MCP Tools for handling driver and adjuster communications.
 * Integrates with Twilio for real SMS delivery and Gmail for email when configured.
 */
@Service
public class CommunicationsService {

    private static final Logger log = LoggerFactory.getLogger(CommunicationsService.class);
    private static final DateTimeFormatter EMAIL_DATE_FORMAT =
        DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a z").withZone(ZoneId.systemDefault());

    private final TwilioConfig twilioConfig;
    private final EmailConfig emailConfig;
    private final JavaMailSender mailSender;
    private final MessageRepository messageRepository;

    @Value("${demo.mode:false}")
    private boolean demoMode;

    // Track communication history per claim
    private final ConcurrentMap<String, List<CommunicationLog>> communicationLogs = new ConcurrentHashMap<>();

    public CommunicationsService(
            TwilioConfig twilioConfig,
            EmailConfig emailConfig,
            JavaMailSender mailSender,
            MessageRepository messageRepository) {
        this.twilioConfig = twilioConfig;
        this.emailConfig = emailConfig;
        this.mailSender = mailSender;
        this.messageRepository = messageRepository;
    }

    /**
     * Send an SMS to the driver checking on their welfare.
     * Uses Twilio when configured, otherwise simulates the send.
     */
    @McpTool(description = "Send an SMS message to the driver. " +
                        "Returns confirmation of delivery status.")
    public SmsResult sendSms(
            @McpToolParam(description = "Driver's phone number")
            String phoneNumber,

            @McpToolParam(description = "Message content")
            String message,

            @McpToolParam(description = "Claim reference for tracking")
            String claimReference,
            
            @McpToolParam(description = "Customer name (optional, for demo mode)", required = false)
            String customerName
    ) {
        Instant sentAt = Instant.now();
        boolean delivered;
        String messageId;
        String status;

        // Demo mode: save to database instead of sending
        if (demoMode) {
            log.info("DEMO MODE: Storing SMS to {} in database instead of sending", phoneNumber);
            
            Message dbMessage = Message.builder()
                    .messageType("SMS")
                    .recipientType("CUSTOMER")
                    .recipientIdentifier(phoneNumber)
                    .claimReference(claimReference)
                    .subject(null)
                    .body(message)
                    .sentAt(sentAt)
                    .customerName(customerName)
                    .build();
            
            messageRepository.save(dbMessage);
            
            messageId = "DEMO-SMS-" + System.currentTimeMillis();
            status = "STORED_IN_DB";
            delivered = true;
        }
        else if (twilioConfig.isEnabled()) {
            // Send real SMS via Twilio
            // Use test number override if configured (for testing with fake customer numbers)
            String effectiveToNumber = twilioConfig.hasTestToNumber()
                ? twilioConfig.getTestToNumber()
                : phoneNumber;

            if (twilioConfig.hasTestToNumber()) {
                log.info("Test mode: redirecting SMS from {} to {}", phoneNumber, effectiveToNumber);
            }

            try {
                com.twilio.rest.api.v2010.account.Message twilioMessage =
                        com.twilio.rest.api.v2010.account.Message.creator(
                    new PhoneNumber(effectiveToNumber),
                    new PhoneNumber(twilioConfig.getFromNumber()),
                    message
                ).create();

                messageId = twilioMessage.getSid();
                status = twilioMessage.getStatus().toString();
                delivered = !status.equals("failed") && !status.equals("undelivered");

                log.info("Twilio SMS sent: sid={}, status={}, to={}",
                        messageId, status, phoneNumber);

            } catch (Exception e) {
                log.error("Twilio SMS failed to {}: {}", phoneNumber, e.getMessage());
                messageId = "FAILED-" + System.currentTimeMillis();
                status = "FAILED";
                delivered = false;
            }
        } else {
            // Simulate SMS sending when Twilio not configured
            log.info("Simulating SMS to {}: {}", phoneNumber, truncate(message, 50));
            messageId = "SIM-" + System.currentTimeMillis();
            status = "SIMULATED";
            delivered = true;
        }

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
            delivered || status.equals("SIMULATED"),
            sentAt,
            messageId,
            status
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
     * Sends an email to the adjuster when Gmail is configured.
     */
    @McpTool(description = "Notify a claims adjuster about a new or updated claim. " +
                          "This will send an email to the adjuster with the claim details.")
    public AdjusterNotification notifyAdjuster(
            @McpToolParam(description = "Claim number")
            String claimNumber,

            @McpToolParam(description = "Accident severity: MINOR, MODERATE, SEVERE")
            String severity,

            @McpToolParam(description = "Full incident report or summary")
            String summary
    ) {
        Instant notifiedAt = Instant.now();

        // Assign adjuster based on severity
        String assignedAdjuster = severity.equals("SEVERE")
            ? "Sarah Martinez (Senior Adjuster)"
            : "Mike Johnson (Adjuster)";

        String priority = severity.equals("SEVERE") ? "HIGH" :
                         severity.equals("MODERATE") ? "MEDIUM" : "LOW";

        // Send email to adjuster if configured
        boolean emailSent = false;
        String subject = String.format("[%s PRIORITY] FNOL Report - %s", priority, claimNumber);
        String htmlBody = buildAdjusterNotificationHtml(claimNumber, severity, priority,
                                                         assignedAdjuster, summary, notifiedAt);
        
        // Demo mode: save to database instead of sending
        if (demoMode) {
            log.info("DEMO MODE: Storing adjuster notification for {} in database instead of sending", claimNumber);
            
            Message dbMessage = Message.builder()
                    .messageType("EMAIL")
                    .recipientType("ADJUSTER")
                    .recipientIdentifier(emailConfig.getAdjusterEmail())
                    .claimReference(claimNumber)
                    .subject(subject)
                    .body(htmlBody)
                    .sentAt(notifiedAt)
                    .build();
            
            messageRepository.save(dbMessage);
            emailSent = true;
        }
        else if (emailConfig.isEnabled()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(emailConfig.getUsername());
                helper.setTo(emailConfig.getAdjusterEmail());
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                mailSender.send(message);
                emailSent = true;
                log.info("Adjuster notification email sent to {} for claim {}",
                        emailConfig.getAdjusterEmail(), claimNumber);
            } catch (Exception e) {
                log.error("Failed to send adjuster notification email for {}: {}", claimNumber, e.getMessage());
            }
        } else {
            log.info("Email not configured - simulating adjuster notification for claim {}", claimNumber);
        }

        logCommunication(claimNumber, new CommunicationLog(
            notifiedAt,
            emailSent ? "EMAIL" : "INTERNAL",
            "OUTBOUND",
            assignedAdjuster,
            "Claim assigned: " + priority + " priority" + (emailSent ? " (email sent)" : ""),
            true
        ));

        return new AdjusterNotification(
            true,
            assignedAdjuster,
            priority,
            notifiedAt
        );
    }

    private String buildAdjusterNotificationHtml(String claimNumber, String severity, String priority,
                                                  String assignedAdjuster, String summary, Instant timestamp) {
        String severityColor = switch (severity.toUpperCase()) {
            case "SEVERE" -> "#dc3545";
            case "MODERATE" -> "#ffc107";
            default -> "#28a745";
        };

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .header { background: #1a237e; color: white; padding: 20px; }
                    .priority { display: inline-block; padding: 5px 15px; border-radius: 4px; color: white; font-weight: bold; }
                    .content { padding: 20px; }
                    .meta { background: #f5f5f5; padding: 15px; border-radius: 4px; margin: 15px 0; }
                    .report { background: #fff; border: 1px solid #ddd; padding: 20px; border-radius: 4px; white-space: pre-wrap; font-family: monospace; }
                    .footer { background: #f5f5f5; padding: 15px; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>FNOL Claim Notification</h1>
                    <span class="priority" style="background: %s;">%s PRIORITY</span>
                </div>
                <div class="content">
                    <div class="meta">
                        <p><strong>Claim Number:</strong> %s</p>
                        <p><strong>Severity:</strong> %s</p>
                        <p><strong>Assigned To:</strong> %s</p>
                        <p><strong>Notification Time:</strong> %s</p>
                    </div>
                    <h2>Incident Details</h2>
                    <div class="report">%s</div>
                </div>
                <div class="footer">
                    <p>This notification was automatically generated by the CRASH system.</p>
                    <p>Insurance Megacorp - Automated Claims Processing</p>
                </div>
            </body>
            </html>
            """,
            severityColor, priority,
            claimNumber, severity, assignedAdjuster,
            EMAIL_DATE_FORMAT.format(timestamp),
            summary.replace("\n", "<br>").replace("\\n", "<br>")
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
     * Send FNOL report email to adjuster.
     * Uses Gmail SMTP when configured, otherwise simulates the send.
     */
    @McpTool(description = "Send FNOL report email to claims adjuster with full accident details.")
    public EmailResult sendFnolEmail(
            @McpToolParam(description = "Claim reference number")
            String claimReference,

            @McpToolParam(description = "Driver's full name")
            String driverName,

            @McpToolParam(description = "Policy number")
            String policyNumber,

            @McpToolParam(description = "Accident severity: MINOR, MODERATE, SEVERE")
            String severity,

            @McpToolParam(description = "Full FNOL report content")
            String reportContent
    ) {
        Instant sentAt = Instant.now();
        boolean sent;
        String messageId;
        String status;

        String subject = String.format("[%s] FNOL Report - Claim %s - %s",
                severity, claimReference, driverName);

        String htmlBody = buildFnolEmailHtml(claimReference, driverName, policyNumber, severity, reportContent, sentAt);

        // Demo mode: save to database instead of sending
        if (demoMode) {
            log.info("DEMO MODE: Storing FNOL email for {} in database instead of sending", claimReference);
            
            Message dbMessage = Message.builder()
                    .messageType("EMAIL")
                    .recipientType("ADJUSTER")
                    .recipientIdentifier(emailConfig.getAdjusterEmail())
                    .claimReference(claimReference)
                    .subject(subject)
                    .body(htmlBody)
                    .sentAt(sentAt)
                    .customerName(driverName)
                    .build();
            
            messageRepository.save(dbMessage);
            
            messageId = "DEMO-EMAIL-" + System.currentTimeMillis();
            status = "STORED_IN_DB";
            sent = true;
        }
        else if (emailConfig.isEnabled()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                helper.setFrom(emailConfig.getUsername());
                helper.setTo(emailConfig.getAdjusterEmail());
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                mailSender.send(message);

                messageId = "EMAIL-" + System.currentTimeMillis();
                status = "SENT";
                sent = true;

                log.info("FNOL email sent to {} for claim {}", emailConfig.getAdjusterEmail(), claimReference);

            } catch (Exception e) {
                log.error("Failed to send FNOL email for claim {}: {}", claimReference, e.getMessage(), e);
                messageId = "FAILED-" + System.currentTimeMillis();
                status = "FAILED: " + e.getMessage();
                sent = false;
            }
        } else {
            log.info("Simulating FNOL email for claim {} to adjuster", claimReference);
            log.debug("Email subject: {}", subject);
            messageId = "SIM-" + System.currentTimeMillis();
            status = "SIMULATED";
            sent = true;
        }

        logCommunication(claimReference, new CommunicationLog(
            sentAt,
            "EMAIL",
            "OUTBOUND",
            emailConfig.isEnabled() ? emailConfig.getAdjusterEmail() : "adjuster@simulated.com",
            "FNOL Report: " + truncate(subject, 50),
            sent
        ));

        return new EmailResult(
            sent || status.equals("SIMULATED"),
            sentAt,
            messageId,
            status,
            emailConfig.isEnabled() ? emailConfig.getAdjusterEmail() : "simulated"
        );
    }

    private String buildFnolEmailHtml(String claimReference, String driverName, String policyNumber,
                                       String severity, String reportContent, Instant timestamp) {
        String severityColor = switch (severity.toUpperCase()) {
            case "SEVERE" -> "#dc3545";
            case "MODERATE" -> "#ffc107";
            default -> "#28a745";
        };

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .header { background: #1a237e; color: white; padding: 20px; }
                    .severity { display: inline-block; padding: 5px 15px; border-radius: 4px; color: white; font-weight: bold; }
                    .content { padding: 20px; }
                    .meta { background: #f5f5f5; padding: 15px; border-radius: 4px; margin: 15px 0; }
                    .report { background: #fff; border: 1px solid #ddd; padding: 20px; border-radius: 4px; white-space: pre-wrap; }
                    .footer { background: #f5f5f5; padding: 15px; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>First Notice of Loss Report</h1>
                    <span class="severity" style="background: %s;">%s SEVERITY</span>
                </div>
                <div class="content">
                    <div class="meta">
                        <p><strong>Claim Reference:</strong> %s</p>
                        <p><strong>Driver:</strong> %s</p>
                        <p><strong>Policy:</strong> %s</p>
                        <p><strong>Report Generated:</strong> %s</p>
                    </div>
                    <h2>Incident Report</h2>
                    <div class="report">%s</div>
                </div>
                <div class="footer">
                    <p>This report was automatically generated by the CRASH (Claims Response Agent System Hive) system.</p>
                    <p>Insurance Megacorp - Automated Claims Processing</p>
                </div>
            </body>
            </html>
            """,
            severityColor, severity.toUpperCase(),
            claimReference, driverName, policyNumber,
            EMAIL_DATE_FORMAT.format(timestamp),
            reportContent.replace("\n", "<br>")
        );
    }

    /**
     * Send follow-up email to customer with claim details and service recommendations.
     * Uses Gmail SMTP when configured, otherwise simulates the send.
     */
    @McpTool(description = "Send follow-up email to customer with claim details, next steps, and recommended services " +
                          "(body shops, tow services, rentals, etc). Call this AFTER gathering all service information.")
    public EmailResult sendCustomerFollowupEmail(
            @McpToolParam(description = "Claim reference number")
            String claimReference,

            @McpToolParam(description = "Customer's full name")
            String customerName,
            
            @McpToolParam(description = "Customer's email address")
            String customerEmail,

            @McpToolParam(description = "Policy number")
            String policyNumber,

            @McpToolParam(description = "Accident severity: MINOR, MODERATE, SEVERE")
            String severity,

            @McpToolParam(description = "Assigned adjuster name and contact info")
            String adjusterInfo,

            @McpToolParam(description = "Body shops section - formatted list of recommended body shops near customer home")
            String bodyShopsInfo,

            @McpToolParam(description = "Tow services section - formatted list if vehicle needs towing")
            String towServicesInfo,

            @McpToolParam(description = "Rental car section - formatted list of nearby rental locations")
            String rentalCarsInfo,

            @McpToolParam(description = "Medical facilities section - formatted list if injuries reported")
            String medicalInfo,

            @McpToolParam(description = "Additional next steps or instructions for the customer")
            String nextSteps
    ) {
        Instant sentAt = Instant.now();
        boolean sent;
        String messageId;
        String status;

        String subject = String.format("Insurance Megacorp - Your Claim %s - Next Steps", claimReference);

        String htmlBody = buildCustomerFollowupEmailHtml(claimReference, customerName, policyNumber, severity,
                adjusterInfo, bodyShopsInfo, towServicesInfo, rentalCarsInfo, medicalInfo, nextSteps, sentAt);

        // Use the customer's email from policy data for demo mode, or configured email for production
        String recipientEmail = demoMode ? customerEmail : 
                               (emailConfig.hasCustomerEmail() ? emailConfig.getCustomerEmail() : customerEmail);

        // Demo mode: save to database instead of sending
        if (demoMode) {
            log.info("DEMO MODE: Storing customer follow-up email for {} ({}) in database instead of sending", 
                    customerName, recipientEmail);
            
            Message dbMessage = Message.builder()
                    .messageType("EMAIL")
                    .recipientType("CUSTOMER")
                    .recipientIdentifier(recipientEmail)
                    .claimReference(claimReference)
                    .subject(subject)
                    .body(htmlBody)
                    .sentAt(sentAt)
                    .customerName(customerName)
                    .build();
            
            messageRepository.save(dbMessage);
            
            messageId = "DEMO-CUST-EMAIL-" + System.currentTimeMillis();
            status = "STORED_IN_DB";
            sent = true;
        }
        else if (emailConfig.isEnabled() && recipientEmail != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                helper.setFrom(emailConfig.getUsername());
                helper.setTo(recipientEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                mailSender.send(message);

                messageId = "CUST-EMAIL-" + System.currentTimeMillis();
                status = "SENT";
                sent = true;

                log.info("Customer follow-up email sent to {} for claim {}", recipientEmail, claimReference);

            } catch (Exception e) {
                log.error("Failed to send customer follow-up email for claim {}: {}", claimReference, e.getMessage(), e);
                messageId = "FAILED-" + System.currentTimeMillis();
                status = "FAILED: " + e.getMessage();
                sent = false;
            }
        } else {
            log.info("Simulating customer follow-up email for claim {} (customer email: {})",
                    claimReference, recipientEmail != null ? recipientEmail : "not configured");
            log.debug("Email subject: {}", subject);
            messageId = "SIM-" + System.currentTimeMillis();
            status = "SIMULATED";
            sent = true;
        }

        logCommunication(claimReference, new CommunicationLog(
            sentAt,
            "EMAIL",
            "OUTBOUND",
            recipientEmail != null ? recipientEmail : "customer@simulated.com",
            "Customer follow-up: " + truncate(subject, 50),
            sent
        ));

        return new EmailResult(
            sent || status.equals("SIMULATED"),
            sentAt,
            messageId,
            status,
            recipientEmail != null ? recipientEmail : "simulated"
        );
    }

    private String buildCustomerFollowupEmailHtml(String claimReference, String customerName, String policyNumber,
                                                   String severity, String adjusterInfo, String bodyShopsInfo,
                                                   String towServicesInfo, String rentalCarsInfo, String medicalInfo,
                                                   String nextSteps, Instant timestamp) {
        StringBuilder sectionsHtml = new StringBuilder();

        // Add service sections only if they have content
        if (medicalInfo != null && !medicalInfo.isBlank()) {
            sectionsHtml.append(buildServiceSection("Medical Facilities", medicalInfo, "#dc3545"));
        }
        if (towServicesInfo != null && !towServicesInfo.isBlank()) {
            sectionsHtml.append(buildServiceSection("Tow Services", towServicesInfo, "#ff9800"));
        }
        if (bodyShopsInfo != null && !bodyShopsInfo.isBlank()) {
            sectionsHtml.append(buildServiceSection("Recommended Body Shops", bodyShopsInfo, "#2196f3"));
        }
        if (rentalCarsInfo != null && !rentalCarsInfo.isBlank()) {
            sectionsHtml.append(buildServiceSection("Rental Car Locations", rentalCarsInfo, "#4caf50"));
        }

        String severityColor = switch (severity.toUpperCase()) {
            case "SEVERE" -> "#dc3545";
            case "MODERATE" -> "#ffc107";
            default -> "#28a745";
        };

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
                    .header { background: #1a237e; color: white; padding: 20px; text-align: center; }
                    .header h1 { margin: 0; font-size: 24px; }
                    .content { padding: 20px; }
                    .greeting { font-size: 18px; margin-bottom: 20px; }
                    .claim-box { background: #f5f5f5; padding: 15px; border-radius: 8px; margin: 15px 0; }
                    .claim-box p { margin: 5px 0; }
                    .severity-badge { display: inline-block; padding: 4px 12px; border-radius: 4px; color: white; font-weight: bold; font-size: 12px; }
                    .section { margin: 20px 0; border-left: 4px solid #ddd; padding-left: 15px; }
                    .section h3 { margin: 0 0 10px 0; color: #333; }
                    .section-content { white-space: pre-wrap; font-size: 14px; }
                    .adjuster-box { background: #e3f2fd; padding: 15px; border-radius: 8px; margin: 20px 0; }
                    .next-steps { background: #fff3e0; padding: 15px; border-radius: 8px; margin: 20px 0; }
                    .next-steps h3 { color: #e65100; margin-top: 0; }
                    .footer { background: #f5f5f5; padding: 15px; font-size: 12px; color: #666; text-align: center; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Insurance Megacorp</h1>
                    <p>Claims Support Team</p>
                </div>
                <div class="content">
                    <p class="greeting">Dear %s,</p>
                    <p>We hope you're safe following the incident. Your claim has been automatically created and an adjuster has been assigned to assist you.</p>

                    <div class="claim-box">
                        <p><strong>Claim Reference:</strong> %s</p>
                        <p><strong>Policy Number:</strong> %s</p>
                        <p><strong>Incident Severity:</strong> <span class="severity-badge" style="background: %s;">%s</span></p>
                        <p><strong>Report Time:</strong> %s</p>
                    </div>

                    <div class="adjuster-box">
                        <h3>Your Claims Adjuster</h3>
                        <p>%s</p>
                        <p>Your adjuster will be in touch within 24 hours to discuss your claim.</p>
                    </div>

                    %s

                    <div class="next-steps">
                        <h3>Next Steps</h3>
                        <div class="section-content">%s</div>
                    </div>

                    <p>If you have any immediate questions, please don't hesitate to contact us.</p>
                    <p>Best regards,<br>Insurance Megacorp Claims Team</p>
                </div>
                <div class="footer">
                    <p>This email was automatically generated by our CRASH (Claims Response Agent System Hive) system.</p>
                    <p>Insurance Megacorp | Claims Department | 1-800-555-CLAIM</p>
                </div>
            </body>
            </html>
            """,
            customerName,
            claimReference, policyNumber, severityColor, severity.toUpperCase(),
            EMAIL_DATE_FORMAT.format(timestamp),
            adjusterInfo != null ? adjusterInfo : "An adjuster will be assigned shortly.",
            sectionsHtml.toString(),
            nextSteps != null ? nextSteps.replace("\n", "<br>") : "Your adjuster will contact you with next steps."
        );
    }

    private String buildServiceSection(String title, String content, String borderColor) {
        return String.format("""
            <div class="section" style="border-left-color: %s;">
                <h3>%s</h3>
                <div class="section-content">%s</div>
            </div>
            """, borderColor, title, content.replace("\n", "<br>"));
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
            "Your claim has been started automatically. " +
            "Check your email for full details and next steps. - Insurance Megacorp",
            driverName.split(" ")[0]
        );
        
        // Send wellness check
        SmsResult smsResult = sendSms(driverPhone, smsContent, claimReference, driverName);
        
        // Send push notification
        PushResult pushResult = sendPushNotification(
            claimReference.hashCode() % 10000,
            "Are you okay?",
            "We detected a possible accident. Tap to confirm you're safe.",
            claimReference
        );
        
        // Assign adjuster (email will be sent later with full FNOL report)
        String assignedAdjuster = severity.equals("SEVERE")
            ? "Sarah Martinez (Senior Adjuster)"
            : "Mike Johnson (Adjuster)";
        String priority = severity.equals("SEVERE") ? "HIGH" :
                         severity.equals("MODERATE") ? "MEDIUM" : "LOW";

        // Log adjuster assignment (no email yet - that comes with the full report)
        logCommunication(claimReference, new CommunicationLog(
            Instant.now(),
            "INTERNAL",
            "OUTBOUND",
            assignedAdjuster,
            "Claim assigned: " + priority + " priority (awaiting FNOL report)",
            true
        ));
        boolean adjusterNotified = true;
        
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

    public record EmailResult(
        boolean sent,
        Instant sentAt,
        String messageId,
        String status,
        String recipient
    ) {}
}
