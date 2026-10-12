package com.devopshub.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Pipeline {
    private String id;
    private String projectId;
    private String name;
    private String description;
    private String executionMode; // SEQUENTIAL or PARALLEL_FORK
    private boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Associated stages and project name
    private String projectName;
    private List<Stage> stages = new ArrayList<>();
    private int executionCount;
    private String lastExecutionStatus;

    public Pipeline() {
        this.id = UUID.randomUUID().toString();
        this.executionMode = "SEQUENTIAL";
        this.active = true;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public Pipeline(String id, String projectId, String name, String description, String executionMode, boolean active) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.executionMode = executionMode != null ? executionMode : "SEQUENTIAL";
        this.active = active;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public List<Stage> getStages() { return stages; }
    public void setStages(List<Stage> stages) { this.stages = stages; }

    public int getExecutionCount() { return executionCount; }
    public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }

    public String getLastExecutionStatus() { return lastExecutionStatus; }
    public void setLastExecutionStatus(String lastExecutionStatus) { this.lastExecutionStatus = lastExecutionStatus; }

    @Override
    public String toString() {
        return name;
    }
}
