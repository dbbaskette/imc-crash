package com.insurancemegacorp.crash.orchestrator;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking crash processing statistics.
 * Uses atomic counters for thread-safe increment operations.
 */
@Service
public class StatsService {

    private final AtomicLong accidentsReported = new AtomicLong(0);
    private final AtomicLong fnolProcessed = new AtomicLong(0);
    private final AtomicLong smsSent = new AtomicLong(0);
    private final AtomicLong emailsSent = new AtomicLong(0);

    /**
     * Increment the accidents reported counter.
     * Called when crash-sink submits an accident to the orchestrator.
     */
    public void incrementAccidentsReported() {
        accidentsReported.incrementAndGet();
    }

    /**
     * Increment the FNOL processed counter.
     * Called when an FNOL report is successfully persisted.
     */
    public void incrementFnolProcessed() {
        fnolProcessed.incrementAndGet();
    }

    /**
     * Increment the SMS sent counter.
     * Called when an SMS is successfully sent via communications service.
     */
    public void incrementSmsSent() {
        smsSent.incrementAndGet();
    }

    /**
     * Increment the emails sent counter.
     * Called when an email is successfully sent via communications service.
     */
    public void incrementEmailsSent() {
        emailsSent.incrementAndGet();
    }

    /**
     * Get the current count of accidents reported.
     */
    public long getAccidentsReported() {
        return accidentsReported.get();
    }

    /**
     * Get the current count of FNOL reports processed.
     */
    public long getFnolProcessed() {
        return fnolProcessed.get();
    }

    /**
     * Get the current count of SMS messages sent.
     */
    public long getSmsSent() {
        return smsSent.get();
    }

    /**
     * Get the current count of emails sent.
     */
    public long getEmailsSent() {
        return emailsSent.get();
    }
}
