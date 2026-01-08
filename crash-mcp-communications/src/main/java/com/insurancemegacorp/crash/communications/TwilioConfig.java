package com.insurancemegacorp.crash.communications;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Twilio SMS integration.
 * Reads credentials from application.yml or environment variables.
 */
@Configuration
@ConfigurationProperties(prefix = "twilio")
public class TwilioConfig {

    private static final Logger log = LoggerFactory.getLogger(TwilioConfig.class);

    private String accountSid;
    private String authToken;
    private String fromNumber;
    private String testToNumber;  // Optional override for all SMS recipients
    private boolean enabled = true;

    @PostConstruct
    public void initTwilio() {
        if (isConfigured()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio initialized successfully with account SID: {}...",
                    accountSid.substring(0, Math.min(8, accountSid.length())));
            if (hasTestToNumber()) {
                log.info("Test mode enabled - all SMS will be sent to: {}", testToNumber);
            }
        } else {
            log.warn("Twilio not configured - SMS will be simulated. " +
                    "Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_FROM_NUMBER to enable.");
            enabled = false;
        }
    }

    public boolean isConfigured() {
        return accountSid != null && !accountSid.isBlank()
            && authToken != null && !authToken.isBlank()
            && fromNumber != null && !fromNumber.isBlank();
    }

    public boolean isEnabled() {
        return enabled && isConfigured();
    }

    // Getters and Setters

    public String getAccountSid() {
        return accountSid;
    }

    public void setAccountSid(String accountSid) {
        this.accountSid = accountSid;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTestToNumber() {
        return testToNumber;
    }

    public void setTestToNumber(String testToNumber) {
        this.testToNumber = testToNumber;
    }

    public boolean hasTestToNumber() {
        return testToNumber != null && !testToNumber.isBlank();
    }
}
