package com.devopshub.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Execution {
    private String id;
    private String pipelineId;
    private String projectId;
    private int executionNumber;
    private ExecutionStatus status;
    private TriggerType triggerType;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private double durationSeconds;
    private Timestamp createdAt;

    // Associated UI metadata and verified build evidence
    private String pipelineName;
    private String projectName;
    private String projectType;
    private String projectRepositoryUrl;
    private String commitHash;
    private String artifactPath;
    private String currentPhase;
    private String backendDescription;
    private List<StageExecution> stageExecutions = new ArrayList<>();
    private FailureDiagnosis failureDiagnosis;

    public Execution() {
        this.id = UUID.randomUUID().toString();
        this.status = ExecutionStatus.QUEUED;
        this.triggerType = TriggerType.MANUAL;
        this.durationSeconds = 0.0;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public Execution(String id, String pipelineId, String projectId, int executionNumber, ExecutionStatus status, TriggerType triggerType) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.pipelineId = pipelineId;
        this.projectId = projectId;
        this.executionNumber = executionNumber;
        this.status = status != null ? status : ExecutionStatus.QUEUED;
        this.triggerType = triggerType != null ? triggerType : TriggerType.MANUAL;
        this.durationSeconds = 0.0;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public int getExecutionNumber() { return executionNumber; }
    public void setExecutionNumber(int executionNumber) { this.executionNumber = executionNumber; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) {
        if (status == null) return;
        if (this.status != null && this.status.isTerminal() && !status.isTerminal()) {
            return;
        }
        this.status = status;
    }

    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }

    public Timestamp getStartedAt() { return startedAt; }
    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt; }

    public Timestamp getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Timestamp finishedAt) { this.finishedAt = finishedAt; }

    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getPipelineName() { return pipelineName; }
    public void setPipelineName(String pipelineName) { this.pipelineName = pipelineName; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public List<StageExecution> getStageExecutions() { return stageExecutions; }
    public void setStageExecutions(List<StageExecution> stageExecutions) { this.stageExecutions = stageExecutions; }

    public FailureDiagnosis getFailureDiagnosis() { return failureDiagnosis; }
    public void setFailureDiagnosis(FailureDiagnosis failureDiagnosis) { this.failureDiagnosis = failureDiagnosis; }

    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }

    public String getProjectRepositoryUrl() { return projectRepositoryUrl; }
    public void setProjectRepositoryUrl(String projectRepositoryUrl) { this.projectRepositoryUrl = projectRepositoryUrl; }

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public String getArtifactPath() { return artifactPath; }
    public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }

    public String getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }

    public String getBackendDescription() { return backendDescription; }
    public void setBackendDescription(String backendDescription) { this.backendDescription = backendDescription; }

    public int getCompletedStageCount() {
        if (stageExecutions == null) return 0;
        int count = 0;
        for (StageExecution se : stageExecutions) {
            if (se.getStatus() == StageExecutionStatus.SUCCESS) {
                count++;
            }
        }
        return count;
    }

    public int getTotalStageCount() {
        return stageExecutions != null ? stageExecutions.size() : 0;
    }

    @Override
    public String toString() {
        return "Build #" + executionNumber + " (" + status + ")";
    }
}
