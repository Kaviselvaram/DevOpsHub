package com.devopshub.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Stage {
    private String id;
    private String pipelineId;
    private String name;
    private StageType stageType;
    private int stageOrder;
    private String command;
    private int timeoutSeconds;
    private boolean enabled;
    private String parallelGroup;
    private Timestamp createdAt;
    
    // Dependencies (Stage IDs this stage depends upon)
    private List<String> dependsOnStageIds = new ArrayList<>();

    public Stage() {
        this.id = UUID.randomUUID().toString();
        this.stageType = StageType.BUILD;
        this.timeoutSeconds = 300;
        this.enabled = true;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public Stage(String id, String pipelineId, String name, StageType stageType, int stageOrder, String command, int timeoutSeconds, boolean enabled, String parallelGroup) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.pipelineId = pipelineId;
        this.name = name;
        this.stageType = stageType != null ? stageType : StageType.BUILD;
        this.stageOrder = stageOrder;
        this.command = command != null ? command : "mvn compile -B";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 300;
        this.enabled = enabled;
        this.parallelGroup = parallelGroup;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public StageType getStageType() { return stageType; }
    public void setStageType(StageType stageType) { this.stageType = stageType; }

    public int getStageOrder() { return stageOrder; }
    public void setStageOrder(int stageOrder) { this.stageOrder = stageOrder; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getParallelGroup() { return parallelGroup; }
    public void setParallelGroup(String parallelGroup) { this.parallelGroup = parallelGroup; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public List<String> getDependsOnStageIds() { return dependsOnStageIds; }
    public void setDependsOnStageIds(List<String> dependsOnStageIds) { this.dependsOnStageIds = dependsOnStageIds; }

    @Override
    public String toString() {
        return name + " (" + stageType + ")";
    }
}
