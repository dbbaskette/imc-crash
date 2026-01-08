package com.insurancemegacorp.crash.orchestrator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for FNOL report persistence.
 */
@Repository
public interface FnolRepository extends JpaRepository<FnolEntity, Long> {

    /**
     * Find a report by claim number.
     */
    Optional<FnolEntity> findByClaimNumber(String claimNumber);

    /**
     * Find all reports for a policy.
     */
    List<FnolEntity> findByPolicyIdOrderByEventTimeDesc(Integer policyId);

    /**
     * Find reports by severity.
     */
    List<FnolEntity> findBySeverityOrderByEventTimeDesc(String severity);

    /**
     * Find reports by status.
     */
    List<FnolEntity> findByStatusOrderByEventTimeDesc(String status);

    /**
     * Find reports within a time range.
     */
    List<FnolEntity> findByEventTimeBetweenOrderByEventTimeDesc(Instant start, Instant end);

    /**
     * Find recent reports (last N hours).
     */
    @Query("SELECT f FROM FnolEntity f WHERE f.eventTime > :since ORDER BY f.eventTime DESC")
    List<FnolEntity> findRecentReports(Instant since);

    /**
     * Count reports by severity.
     */
    long countBySeverity(String severity);

    /**
     * Count reports by status.
     */
    long countByStatus(String status);

    /**
     * Check if a claim already exists.
     */
    boolean existsByClaimNumber(String claimNumber);
}
