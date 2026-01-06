package com.insurancemegacorp.crash.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.List;

/**
 * Status of communications handled by the Communications agent.
 * Tracks driver outreach, responses, and escalations.
 */
@JsonClassDescription("Status of driver and adjuster communications")
public record CommunicationsStatus(
    @JsonPropertyDescription("Driver outreach details")
    DriverOutreach driverOutreach,

    @JsonPropertyDescription("Whether claims adjuster has been notified")
    boolean adjusterNotified,

    @JsonPropertyDescription("Assigned adjuster name if any")
    String assignedAdjuster,

    @JsonPropertyDescription("Whether roadside assistance has been dispatched")
    boolean roadsideDispatched,

    @JsonPropertyDescription("History of all communications")
    List<CommunicationLog> communicationLog
) {
    @JsonClassDescription("Details of driver outreach attempts")
    public record DriverOutreach(
        @JsonPropertyDescription("Whether SMS was sent")
        boolean smsSent,

        @JsonPropertyDescription("Timestamp when SMS was sent")
        Instant smsTimestamp,

        @JsonPropertyDescription("Content of the SMS message")
        String smsContent,

        @JsonPropertyDescription("Whether push notification was sent")
        boolean pushSent,

        @JsonPropertyDescription("Driver response status: PENDING, CONFIRMED_OK, CONFIRMED_INJURED, NO_RESPONSE")
        String responseStatus,

        @JsonPropertyDescription("Driver's response message if any")
        String driverResponse,

        @JsonPropertyDescription("Timestamp of driver response")
        Instant responseTimestamp
    ) {}

    @JsonClassDescription("A single communication log entry")
    public record CommunicationLog(
        @JsonPropertyDescription("Timestamp of the communication")
        Instant timestamp,

        @JsonPropertyDescription("Type: SMS, PUSH, EMAIL, CALL, INTERNAL")
        String type,

        @JsonPropertyDescription("Direction: OUTBOUND or INBOUND")
        String direction,

        @JsonPropertyDescription("Recipient or sender")
        String party,

        @JsonPropertyDescription("Summary of the communication")
        String summary,

        @JsonPropertyDescription("Whether delivery was confirmed")
        boolean delivered
    ) {}
}
