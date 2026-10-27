package com.devopshub.execution.event;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.UUID;

/**
 * First-class lifecycle event capturing granular transitions and execution facts.
 */
public class ExecutionEvent {
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    private String id;
    private long sequenceNumber;
    private String executionId;
    private String pipelineId;
    private String stageExecutionId;
    private String stageName;
    private int stageOrder;
    private ExecutionEventType eventType;
    private Timestamp timestamp;
    private String statusTransition;
    private String message;
    private Double durationSeconds;
    private Integer exitCode;
    private boolean authoritative;

    public ExecutionEvent() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.authoritative = true;
    }

    public ExecutionEvent(long sequenceNumber, String executionId, String pipelineId,
                          ExecutionEventType eventType, String message) {
        this(UUID.randomUUID().toString(), sequenceNumber, executionId, pipelineId,
                null, null, 0, eventType, new Timestamp(System.currentTimeMillis()),
                null, message, null, null, true);
    }

    public ExecutionEvent(String id, long sequenceNumber, String executionId, String pipelineId,
                          String stageExecutionId, String stageName, int stageOrder,
                          ExecutionEventType eventType, Timestamp timestamp,
                          String statusTransition, String message, Double durationSeconds,
                          Integer exitCode, boolean authoritative) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.sequenceNumber = sequenceNumber;
        this.executionId = executionId;
        this.pipelineId = pipelineId;
        this.stageExecutionId = stageExecutionId;
        this.stageName = stageName;
        this.stageOrder = stageOrder;
        this.eventType = eventType;
        this.timestamp = timestamp != null ? timestamp : new Timestamp(System.currentTimeMillis());
        this.statusTransition = statusTransition;
        this.message = message;
        this.durationSeconds = durationSeconds;
        this.exitCode = exitCode;
        this.authoritative = authoritative;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStageExecutionId() { return stageExecutionId; }
    public void setStageExecutionId(String stageExecutionId) { this.stageExecutionId = stageExecutionId; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public int getStageOrder() { return stageOrder; }
    public void setStageOrder(int stageOrder) { this.stageOrder = stageOrder; }

    public ExecutionEventType getEventType() { return eventType; }
    public void setEventType(ExecutionEventType eventType) { this.eventType = eventType; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public String getStatusTransition() { return statusTransition; }
    public void setStatusTransition(String statusTransition) { this.statusTransition = statusTransition; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }

    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }

    public boolean isAuthoritative() { return authoritative; }
    public void setAuthoritative(boolean authoritative) { this.authoritative = authoritative; }

    public String getFormattedTime() {
        return timestamp != null ? TIME_FMT.format(timestamp) : "--:--:--";
    }

    @Override
    public String toString() {
        return String.format("[%s] #%d %s: %s", getFormattedTime(), sequenceNumber, eventType, message);
    }
}
