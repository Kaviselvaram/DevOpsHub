package com.devopshub.exception;

public class DatabaseException extends DevOpsHubException {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
