package com.devopshub.model;

import java.sql.Timestamp;
import java.util.UUID;

public class StageExecution {
    private String id;
    private String executionId;
    private String stageId;
    private String stageName;
    private StageType stageType;
    private String command;
    private StageExecutionStatus status;
    private int exitCode;
    private boolean exitCodeSet;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private double durationSeconds;
    private String errorMessage;

    public StageExecution() {
        this.id = UUID.randomUUID().toString();
        this.status = StageExecutionStatus.QUEUED;
        this.exitCode = 0;
        this.exitCodeSet = false;
        this.durationSeconds = 0.0;
    }

    public StageExecution(String id, String executionId, String stageId, String stageName, StageType stageType) {
        this(id, executionId, stageId, stageName, stageType, null);
    }

    public StageExecution(String id, String executionId, String stageId, String stageName, StageType stageType, String command) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.executionId = executionId;
        this.stageId = stageId;
        this.stageName = stageName;
        this.stageType = stageType;
        this.command = command;
        this.status = StageExecutionStatus.QUEUED;
        this.exitCode = 0;
        this.exitCodeSet = false;
        this.durationSeconds = 0.0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getStageId() { return stageId; }
    public void setStageId(String stageId) { this.stageId = stageId; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public StageType getStageType() { return stageType; }
    public void setStageType(StageType stageType) { this.stageType = stageType; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public StageExecutionStatus getStatus() { return status; }
    public void setStatus(StageExecutionStatus status) {
        if (status == null) return;
        if (this.status != null && this.status.isTerminal() && !status.isTerminal()) {
            return;
        }
        this.status = status;
    }

    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
        this.exitCodeSet = true;
    }

    public boolean isExitCodeSet() { return exitCodeSet; }
    public void setExitCodeSet(boolean set) { this.exitCodeSet = set; }

    public boolean hasProcessExecuted() {
        return exitCodeSet && (status == StageExecutionStatus.SUCCESS || status == StageExecutionStatus.FAILED || status == StageExecutionStatus.CANCELLED);
    }

    public Timestamp getStartedAt() { return startedAt; }
    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt; }

    public Timestamp getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Timestamp finishedAt) { this.finishedAt = finishedAt; }

    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
