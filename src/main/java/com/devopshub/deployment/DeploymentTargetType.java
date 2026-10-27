package com.devopshub.deployment;

/**
 * Enumeration of deployment target providers.
 * Strictly separates local artifact staging from remote deployment.
 */
public enum DeploymentTargetType {
    /**
     * Local filesystem staging. Intended for local verification and test targets.
     * Never described as remote production deployment.
     */
    LOCAL_STAGING("Local Artifact Staging (Test/Verification)"),

    /**
     * Remote deployment via SSH / SCP with cryptographic verification.
     */
    SSH_REMOTE("Remote Server Deployment (SSH/SCP)"),

    /**
     * Container image build and push to container registry (Docker Hub, ECR, GCR).
     */
    CONTAINER_REGISTRY("Container Registry Push");

    private final String displayName;

    DeploymentTargetType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
