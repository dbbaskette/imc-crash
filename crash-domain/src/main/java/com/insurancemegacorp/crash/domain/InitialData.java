package com.insurancemegacorp.crash.domain;

/**
 * Consolidated result from Level 0 parallel actions.
 * Groups ImpactAnalysis, EnvironmentContext, and PolicyInfo which
 * are gathered in parallel since they have no interdependencies.
 */
public record InitialData(
    ImpactAnalysis impact,
    EnvironmentContext environment,
    PolicyInfo policy
) {}
