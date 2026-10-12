package com.devopshub.model;

public enum FailureCategory {
    ASSERTION_FAILURE("Assertion / Test Failure"),
    COMPILATION_ERROR("Compilation Error"),
    MISSING_DEPENDENCY("Missing Dependency / Artifact"),
    DATABASE_ERROR("Database Connection / SQL Error"),
    TIMEOUT("Execution Timeout"),
    PERMISSION_ERROR("Permission / Access Denied"),
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
