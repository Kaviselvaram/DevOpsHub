package com.devopshub.deployment;

import com.devopshub.repository.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Genuine Deployment Execution & Verification Engine.
 * Coordinates deployment providers (Local Staging and Remote SSH/SCP),
 * validates artifacts, executes SHA-256 integrity verification,
 * audits deployment history, and supports rollback.
 */

public class DeploymentService {
    private static final Logger logger = LoggerFactory.getLogger(DeploymentService.class);

    private final Map<DeploymentTargetType, DeploymentProvider> providers = new HashMap<>();
    private final DeploymentRepository deploymentRepository;

    public DeploymentService() {
        this(new DeploymentRepository());
    }

    public DeploymentService(DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
        this.providers.put(DeploymentTargetType.LOCAL_STAGING, new LocalStagingDeploymentProvider());
        this.providers.put(DeploymentTargetType.SSH_REMOTE, new SshRemoteDeploymentProvider());
    }

    public DeploymentProvider getProvider(DeploymentTargetType type) {
        return providers.getOrDefault(type, providers.get(DeploymentTargetType.LOCAL_STAGING));
    }

    /**
     * Deploys artifact to local staging directory (backward compatible method).
     */
    public DeploymentResult deployArtifact(File sourceArtifact, File targetDirectory) {
        DeploymentConfig config = DeploymentConfig.localStaging(targetDirectory, DeploymentEnvironment.TEST);
        return deployArtifact(sourceArtifact, config, null, null);
    }

    /**
     * Deploys artifact with full environment and target configuration, recording persistent audit history.
     */
    public DeploymentResult deployArtifact(File sourceArtifact, DeploymentConfig config, String executionId, String pipelineId) {
        if (config == null) {
            config = DeploymentConfig.localStaging(new File("./deployments/staging"), DeploymentEnvironment.TEST);
        }

        DeploymentProvider provider = getProvider(config.getTargetType());
        DeploymentResult result = provider.deploy(sourceArtifact, config);

        // Record persistent deployment history
        if (deploymentRepository != null && executionId != null) {
            DeploymentRecord record = new DeploymentRecord(
                    UUID.randomUUID().toString(),
                    executionId,
                    pipelineId != null ? pipelineId : "unknown-pipeline",
                    config.getTargetType(),
                    config.getEnvironment(),
                    result.isSuccess() ? "SUCCESS" : "FAILED",
                    sourceArtifact != null ? sourceArtifact.getAbsolutePath() : "unknown",
                    result.getTargetPath() != null ? result.getTargetPath() : "none",
                    result.getSha256Checksum() != null ? result.getSha256Checksum() : "none",
                    result.getDurationSeconds(),
                    result.getDeployedAt(),
                    result.getMessage()
            );
            deploymentRepository.save(record);
        }

        return result;
    }

    /**
     * Executes a rollback operation on the specified target environment.
     */
    public DeploymentResult rollback(DeploymentConfig config, String executionId, String pipelineId) {
        if (config == null) {
            return DeploymentResult.failure("Rollback configuration cannot be null.");
        }
        DeploymentProvider provider = getProvider(config.getTargetType());
        if (!provider.supportsRollback()) {
            return DeploymentResult.failure("Provider " + config.getTargetType() + " does not support automated rollback.");
        }
        DeploymentResult res = provider.rollback(config);

        if (deploymentRepository != null && executionId != null) {
            DeploymentRecord record = new DeploymentRecord(
                    UUID.randomUUID().toString(),
                    executionId,
                    pipelineId != null ? pipelineId : "unknown-pipeline",
                    config.getTargetType(),
                    config.getEnvironment(),
                    res.isSuccess() ? "ROLLED_BACK" : "FAILED",
                    "rollback-backup",
                    res.getTargetPath() != null ? res.getTargetPath() : "none",
                    res.getSha256Checksum() != null ? res.getSha256Checksum() : "none",
                    res.getDurationSeconds(),
                    res.getDeployedAt(),
                    res.getMessage()
            );
            deploymentRepository.save(record);
        }
        return res;
    }

    public static String computeSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IOException("Failed to compute SHA-256: " + e.getMessage(), e);
        }
    }

    public static class DeploymentResult {
        private final boolean success;
        private final String message;
        private final String targetPath;
        private final long artifactSizeBytes;
        private final String sha256Checksum;
        private final Timestamp deployedAt;
        private final double durationSeconds;

        public DeploymentResult(boolean success, String message, String targetPath,
                                long artifactSizeBytes, String sha256Checksum,
                                Timestamp deployedAt, double durationSeconds) {
            this.success = success;
            this.message = message;
            this.targetPath = targetPath;
            this.artifactSizeBytes = artifactSizeBytes;
            this.sha256Checksum = sha256Checksum;
            this.deployedAt = deployedAt;
            this.durationSeconds = durationSeconds;
        }

        public static DeploymentResult failure(String errorMessage) {
            return new DeploymentResult(false, errorMessage, null, 0, null,
                    new Timestamp(System.currentTimeMillis()), 0.0);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getTargetPath() { return targetPath; }
        public long getArtifactSizeBytes() { return artifactSizeBytes; }
        public String getSha256Checksum() { return sha256Checksum; }
        public Timestamp getDeployedAt() { return deployedAt; }
        public double getDurationSeconds() { return durationSeconds; }
    }
}
