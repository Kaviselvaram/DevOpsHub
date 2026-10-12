package com.devopshub.exception;

public class StageExecutionException extends DevOpsHubException {
    private final String stageId;
    private final int exitCode;

    public StageExecutionException(String stageId, String message, int exitCode) {
        super(message);
        this.stageId = stageId;
        this.exitCode = exitCode;
    }

    public StageExecutionException(String stageId, String message, int exitCode, Throwable cause) {
        super(message, cause);
        this.stageId = stageId;
        this.exitCode = exitCode;
    }

    public String getStageId() {
        return stageId;
    }

    public int getExitCode() {
        return exitCode;
    }
}
