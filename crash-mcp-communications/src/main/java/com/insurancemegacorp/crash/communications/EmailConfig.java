package com.insurancemegacorp.crash.communications;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Gmail SMTP email integration.
 * Reads credentials from application.yml or environment variables.
 */
@Configuration
@ConfigurationProperties(prefix = "gmail")
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    private String username;
    private String appPassword;
    private String adjusterEmail;
    private boolean enabled = true;

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            log.info("Gmail SMTP configured for: {}", username);
            log.info("Adjuster notifications will be sent to: {}", adjusterEmail);
        } else {
            log.warn("Gmail not configured - emails will be simulated. " +
                    "Set GMAIL_USERNAME, GMAIL_APP_PASSWORD, and GMAIL_ADJUSTER_EMAIL to enable.");
            enabled = false;
        }
    }

    public boolean isConfigured() {
        return username != null && !username.isBlank()
            && appPassword != null && !appPassword.isBlank()
            && adjusterEmail != null && !adjusterEmail.isBlank();
    }

    public boolean isEnabled() {
        return enabled && isConfigured();
    }

    // Getters and Setters

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAppPassword() {
        return appPassword;
    }

    public void setAppPassword(String appPassword) {
        this.appPassword = appPassword;
    }

    public String getAdjusterEmail() {
        return adjusterEmail;
    }

    public void setAdjusterEmail(String adjusterEmail) {
        this.adjusterEmail = adjusterEmail;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
