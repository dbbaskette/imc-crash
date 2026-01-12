package com.insurancemegacorp.crash.communications.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing a message (email or SMS) stored during demo mode.
 * When DEMO_MODE is enabled, messages are saved to the database instead of
 * being sent via Gmail or Twilio.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType; // EMAIL, SMS, PUSH

    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType; // ADJUSTER, CUSTOMER

    @Column(name = "recipient_identifier", length = 255)
    private String recipientIdentifier; // email address or phone number

    @Column(name = "claim_reference", length = 100)
    private String claimReference;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "policy_id")
    private Integer policyId;

    @Column(name = "created_at")
    private Instant createdAt;

    // Constructors
    public Message() {
        this.createdAt = Instant.now();
    }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Message message = new Message();

        public Builder messageType(String messageType) {
            message.messageType = messageType;
            return this;
        }

        public Builder recipientType(String recipientType) {
            message.recipientType = recipientType;
            return this;
        }

        public Builder recipientIdentifier(String recipientIdentifier) {
            message.recipientIdentifier = recipientIdentifier;
            return this;
        }

        public Builder claimReference(String claimReference) {
            message.claimReference = claimReference;
            return this;
        }

        public Builder subject(String subject) {
            message.subject = subject;
            return this;
        }

        public Builder body(String body) {
            message.body = body;
            return this;
        }

        public Builder sentAt(Instant sentAt) {
            message.sentAt = sentAt;
            return this;
        }

        public Builder customerName(String customerName) {
            message.customerName = customerName;
            return this;
        }

        public Builder policyId(Integer policyId) {
            message.policyId = policyId;
            return this;
        }

        public Message build() {
            return message;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(String recipientType) {
        this.recipientType = recipientType;
    }

    public String getRecipientIdentifier() {
        return recipientIdentifier;
    }

    public void setRecipientIdentifier(String recipientIdentifier) {
        this.recipientIdentifier = recipientIdentifier;
    }

    public String getClaimReference() {
        return claimReference;
    }

    public void setClaimReference(String claimReference) {
        this.claimReference = claimReference;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Integer getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Integer policyId) {
        this.policyId = policyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
