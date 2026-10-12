package com.devopshub.model;

public enum StageExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
    CANCELLED;

    public static StageExecutionStatus fromString(String val) {
        if (val == null) return QUEUED;
        try {
            return StageExecutionStatus.valueOf(val.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return QUEUED;
        }
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == CANCELLED;
    }
}
