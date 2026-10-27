package com.devopshub.deployment;

/**
 * Deployment environment classification.
 * Prevents accidental production deployment and distinguishes test/staging from production.
 */
public enum DeploymentEnvironment {
    TEST("Test (Ephemeral)"),
    STAGING("Staging (Pre-Production)"),
    PRODUCTION("Production (Live)");

    private final String displayName;

    DeploymentEnvironment(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
