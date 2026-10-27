package com.devopshub.deployment;

import java.sql.Timestamp;

/**
 * Durable record capturing the parameters, cryptographic integrity, and outcome of an artifact deployment.
 */
public class DeploymentRecord {
    private String id;
    private String executionId;
    private String pipelineId;
    private DeploymentTargetType targetType;
    private DeploymentEnvironment environment;
    private String status; // SUCCESS, FAILED, ROLLED_BACK
    private String sourceArtifact;
    private String targetDestination;
    private String sha256Checksum;
    private double durationSeconds;
    private Timestamp deployedAt;
    private String details;

    public DeploymentRecord() {}

    public DeploymentRecord(String id, String executionId, String pipelineId, DeploymentTargetType targetType,
                            DeploymentEnvironment environment, String status, String sourceArtifact,
                            String targetDestination, String sha256Checksum, double durationSeconds,
                            Timestamp deployedAt, String details) {
        this.id = id;
        this.executionId = executionId;
        this.pipelineId = pipelineId;
        this.targetType = targetType;
        this.environment = environment;
        this.status = status;
        this.sourceArtifact = sourceArtifact;
        this.targetDestination = targetDestination;
        this.sha256Checksum = sha256Checksum;
        this.durationSeconds = durationSeconds;
        this.deployedAt = deployedAt;
        this.details = details;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public DeploymentTargetType getTargetType() { return targetType; }
    public void setTargetType(DeploymentTargetType targetType) { this.targetType = targetType; }

    public DeploymentEnvironment getEnvironment() { return environment; }
    public void setEnvironment(DeploymentEnvironment environment) { this.environment = environment; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(String sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public String getTargetDestination() { return targetDestination; }
    public void setTargetDestination(String targetDestination) { this.targetDestination = targetDestination; }

    public String getSha256Checksum() { return sha256Checksum; }
    public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }

    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }

    public Timestamp getDeployedAt() { return deployedAt; }
    public void setDeployedAt(Timestamp deployedAt) { this.deployedAt = deployedAt; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
