package com.devopshub.exception;

public class ExecutionTimeoutException extends DevOpsHubException {
    private final long timeoutSeconds;

    public ExecutionTimeoutException(String message, long timeoutSeconds) {
        super(message);
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
