package com.insurancemegacorp.crash.domain;

/**
 * Consolidated result from Level 1 parallel actions.
 * Both findServices and initiateComms depend only on InitialData,
 * so they can run in parallel.
 */
public record SecondaryData(
    NearbyServices services,
    CommunicationsStatus communications
) {}
