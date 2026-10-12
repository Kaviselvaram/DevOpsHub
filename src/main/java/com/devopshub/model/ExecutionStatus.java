package com.devopshub.model;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED;

    public static ExecutionStatus fromString(String val) {
        if (val == null) return QUEUED;
        try {
            return ExecutionStatus.valueOf(val.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return QUEUED;
        }
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}
