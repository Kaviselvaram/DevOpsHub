package com.devopshub.model;

import java.sql.Timestamp;
import java.util.UUID;

public class Project {
    private String id;
    private String name;
    private String description;
    private String repositoryUrl;
    private String defaultBranch;
    private String projectType;
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    // Aggregated UI fields
    private int pipelineCount;
    private String latestExecutionStatus;

    public Project() {
        this.id = UUID.randomUUID().toString();
        this.defaultBranch = "main";
        this.projectType = "Java";
        this.status = "ACTIVE";
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public Project(String id, String name, String description, String repositoryUrl, String defaultBranch, String projectType, String status) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.repositoryUrl = repositoryUrl;
        this.defaultBranch = defaultBranch != null ? defaultBranch : "main";
        this.projectType = projectType != null ? projectType : "Java";
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }

    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public int getPipelineCount() { return pipelineCount; }
    public void setPipelineCount(int pipelineCount) { this.pipelineCount = pipelineCount; }

    public String getLatestExecutionStatus() { return latestExecutionStatus; }
    public void setLatestExecutionStatus(String latestExecutionStatus) { this.latestExecutionStatus = latestExecutionStatus; }

    @Override
    public String toString() {
        return name;
    }
}
