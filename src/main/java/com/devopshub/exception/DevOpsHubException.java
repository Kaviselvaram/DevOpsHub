package com.devopshub.exception;

/**
 * Base domain exception for DevOpsHub.
 */
public class DevOpsHubException extends RuntimeException {
    public DevOpsHubException(String message) {
        super(message);
    }

    public DevOpsHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
