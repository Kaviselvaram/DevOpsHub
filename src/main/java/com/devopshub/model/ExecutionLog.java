package com.devopshub.model;

import java.sql.Timestamp;

public class ExecutionLog {
    private long id;
    private String executionId;
    private String stageExecutionId;
    private String stageName;
    private LogLevel logLevel;
    private LogStream stream;
    private String message;
    private Timestamp timestamp;

    public ExecutionLog() {
        this.logLevel = LogLevel.INFO;
        this.stream = LogStream.STDOUT;
        this.timestamp = new Timestamp(System.currentTimeMillis());
    }

    public ExecutionLog(String executionId, String stageExecutionId, String stageName, LogLevel logLevel, LogStream stream, String message) {
        this.executionId = executionId;
        this.stageExecutionId = stageExecutionId;
        this.stageName = stageName;
        this.logLevel = logLevel != null ? logLevel : LogLevel.INFO;
        this.stream = stream != null ? stream : LogStream.STDOUT;
        this.message = message;
        this.timestamp = new Timestamp(System.currentTimeMillis());
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getStageExecutionId() { return stageExecutionId; }
    public void setStageExecutionId(String stageExecutionId) { this.stageExecutionId = stageExecutionId; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public LogLevel getLogLevel() { return logLevel; }
    public void setLogLevel(LogLevel logLevel) { this.logLevel = logLevel; }

    public LogStream getStream() { return stream; }
    public void setStream(LogStream stream) { this.stream = stream; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "[" + timestamp + "] [" + logLevel + "] [" + stageName + "] " + message;
    }
}
