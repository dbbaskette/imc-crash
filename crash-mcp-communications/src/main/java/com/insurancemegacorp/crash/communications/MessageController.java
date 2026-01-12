package com.insurancemegacorp.crash.communications;

import com.insurancemegacorp.crash.communications.model.Message;
import com.insurancemegacorp.crash.communications.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for retrieving messages stored during demo mode.
 * Provides endpoints for the UI to fetch adjuster and customer messages.
 */
@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")  // Allow UI to access from different port
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final MessageRepository messageRepository;

    public MessageController(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Get all messages for adjusters (FNOL emails, notifications).
     * Ordered by most recent first.
     */
    @GetMapping("/adjuster")
    public List<Message> getAdjusterMessages() {
        log.debug("Fetching all adjuster messages");
        return messageRepository.findByRecipientTypeOrderBySentAtDesc("ADJUSTER");
    }

    /**
     * Get all customer messages, optionally filtered by customer name.
     * Includes both emails and SMS.
     */
    @GetMapping("/customer")
    public List<Message> getCustomerMessages(
            @RequestParam(required = false) String customerName) {
        if (customerName != null && !customerName.isBlank()) {
            log.debug("Fetching messages for customer: {}", customerName);
            return messageRepository.findByCustomerNameOrderBySentAtDesc(customerName);
        } else {
            log.debug("Fetching all customer messages");
            return messageRepository.findByRecipientTypeOrderBySentAtDesc("CUSTOMER");
        }
    }

    /**
     * Get all messages for a specific claim reference.
     */
    @GetMapping("/claim/{claimReference}")
    public List<Message> getClaimMessages(@PathVariable String claimReference) {
        log.debug("Fetching messages for claim: {}", claimReference);
        return messageRepository.findByClaimReferenceOrderBySentAtDesc(claimReference);
    }

    /**
     * Get all messages for a specific policy ID.
     */
    @GetMapping("/policy/{policyId}")
    public List<Message> getPolicyMessages(@PathVariable Integer policyId) {
        log.debug("Fetching messages for policy: {}", policyId);
        return messageRepository.findByPolicyIdOrderBySentAtDesc(policyId);
    }

    /**
     * Get all messages (for debugging/testing).
     */
    @GetMapping
    public List<Message> getAllMessages() {
        log.debug("Fetching all messages");
        return messageRepository.findAll();
    }

    /**
     * Delete all messages (for demo reset).
     */
    @DeleteMapping
    public void deleteAllMessages() {
        log.info("Deleting all messages");
        messageRepository.deleteAll();
    }
}
