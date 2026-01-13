package com.insurancemegacorp.crash.communications.repository;

import com.insurancemegacorp.crash.communications.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for accessing messages stored during demo mode.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Find all messages for a specific recipient type (ADJUSTER or CUSTOMER).
     * Results ordered by sent_at descending (newest first).
     */
    List<Message> findByRecipientTypeOrderBySentAtDesc(String recipientType);

    /**
     * Find all messages for a specific claim reference.
     * Results ordered by sent_at descending (newest first).
     */
    List<Message> findByClaimReferenceOrderBySentAtDesc(String claimReference);

    /**
     * Find all messages for a specific recipient type and identifier (email or phone).
     * Results ordered by sent_at descending (newest first).
     */
    List<Message> findByRecipientTypeAndRecipientIdentifierOrderBySentAtDesc(
            String recipientType,
            String recipientIdentifier
    );

    /**
     * Find all messages for a specific customer by name.
     * Results ordered by sent_at descending (newest first).
     */
    List<Message> findByCustomerNameOrderBySentAtDesc(String customerName);

    /**
     * Find all messages for a specific policy ID.
     * Results ordered by sent_at descending (newest first).
     */
    List<Message> findByPolicyIdOrderBySentAtDesc(Integer policyId);

    /**
     * Delete all messages for a specific recipient type (ADJUSTER or CUSTOMER).
     */
    void deleteByRecipientType(String recipientType);
}
