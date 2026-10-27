package com.devopshub.model;

public enum FailureCategory {
    ASSERTION_FAILURE("Assertion / Test Failure"),
    COMPILATION_ERROR("Compilation / Build Failure"),
    MISSING_DEPENDENCY("Dependency Installation Failure"),
    MISSING_BUILD_TOOL("Missing Build Tool / Executable"),
    REPOSITORY_CHECKOUT_FAILURE("Repository Checkout Failure"),
    AUTHENTICATION_FAILURE("Authentication Failure"),
    SECURITY_POLICY_VIOLATION("Security Policy Rejection"),
    SECURITY_SCANNER_FAILURE("Security Scanner Gate Failure"),
    ARTIFACT_GENERATION_FAILURE("Artifact Generation Failure"),
    ARTIFACT_VALIDATION_FAILURE("Artifact Validation Failure"),
    DEPLOYMENT_FAILURE("Deployment Failure"),
    TIMEOUT("Execution Timeout"),
    CANCELLATION("Execution Cancelled"),
    PERMISSION_ERROR("Workspace / Access Permission Denied"),
    CONTAINER_UNAVAILABLE("Container Runtime Unavailable"),
    DATABASE_ERROR("Database / Persistence Failure"),
    LOG_COLLECTION_FAILURE("Log Collection Failure"),
    CONFIGURATION_ERROR("Invalid Pipeline Configuration"),
    UNKNOWN("Unclassified Failure");

    private final String displayName;

    FailureCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static FailureCategory fromString(String val) {
        if (val == null) return UNKNOWN;
        try {
            return FailureCategory.valueOf(val.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
