package com.devopshub.exception;

public class PipelineValidationException extends DevOpsHubException {
    public PipelineValidationException(String message) {
        super(message);
    }

    public PipelineValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
