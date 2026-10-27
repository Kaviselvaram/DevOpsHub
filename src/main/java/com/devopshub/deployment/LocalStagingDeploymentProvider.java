package com.devopshub.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;

/**
 * Local filesystem artifact staging provider.
 * Explicitly labeled as a LOCAL test staging provider to prevent users from assuming local copies are remote deployments.
 * Supports automated rollback by maintaining versioned previous deployments.
 */
public class LocalStagingDeploymentProvider implements DeploymentProvider {
    private static final Logger logger = LoggerFactory.getLogger(LocalStagingDeploymentProvider.class);

    @Override
    public DeploymentTargetType getTargetType() {
        return DeploymentTargetType.LOCAL_STAGING;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public DeploymentService.DeploymentResult deploy(File sourceArtifact, DeploymentConfig config) {
        long startTime = System.currentTimeMillis();

        if (sourceArtifact == null || !sourceArtifact.exists()) {
            return DeploymentService.DeploymentResult.failure("Local staging failed: source artifact does not exist: "
                    + (sourceArtifact != null ? sourceArtifact.getAbsolutePath() : "null"));
        }

        if (sourceArtifact.length() == 0) {
            return DeploymentService.DeploymentResult.failure("Local staging failed: source artifact is empty (0 bytes).");
        }

        File targetDirectory = config.getTargetDirectory();
        if (targetDirectory == null) {
            targetDirectory = new File("./deployments/staging");
        }

        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }

        File destinationFile = new File(targetDirectory, sourceArtifact.getName());
        File backupFile = new File(targetDirectory, sourceArtifact.getName() + ".rollback-backup");

        try {
            // If previous deployment exists, preserve as rollback backup
            if (destinationFile.exists()) {
                Files.copy(destinationFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            String sourceSha = DeploymentService.computeSha256(sourceArtifact);

            Files.copy(sourceArtifact.toPath(), destinationFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

            if (!destinationFile.exists() || destinationFile.length() != sourceArtifact.length()) {
                return DeploymentService.DeploymentResult.failure("Local staging verification failed: size mismatch.");
            }

            String destSha = DeploymentService.computeSha256(destinationFile);
            if (!sourceSha.equalsIgnoreCase(destSha)) {
                return DeploymentService.DeploymentResult.failure("Local staging verification failed: SHA-256 checksum mismatch.");
            }

            long duration = System.currentTimeMillis() - startTime;
            String msg = String.format("[LOCAL STAGING] Deployed '%s' to '%s' (%s, Size: %dB, SHA-256: %s)",
                    sourceArtifact.getName(), destinationFile.getAbsolutePath(), config.getEnvironment().getDisplayName(),
                    destinationFile.length(), destSha);
            logger.info(msg);

            return new DeploymentService.DeploymentResult(true, msg, destinationFile.getAbsolutePath(),
                    destinationFile.length(), destSha, new Timestamp(System.currentTimeMillis()), duration / 1000.0);

        } catch (Exception e) {
            logger.error("Local staging deployment failed: {}", e.getMessage(), e);
            return DeploymentService.DeploymentResult.failure("Local staging error: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsRollback() {
        return true;
    }

    @Override
    public DeploymentService.DeploymentResult rollback(DeploymentConfig config) {
        long startTime = System.currentTimeMillis();
        File targetDirectory = config.getTargetDirectory() != null ? config.getTargetDirectory() : new File("./deployments/staging");
        File[] backups = targetDirectory.listFiles((d, name) -> name.endsWith(".rollback-backup"));

        if (backups == null || backups.length == 0) {
            return DeploymentService.DeploymentResult.failure("Rollback failed: No backup artifact available in " + targetDirectory.getAbsolutePath());
        }

        File backup = backups[0];
        String originalName = backup.getName().replace(".rollback-backup", "");
        File restored = new File(targetDirectory, originalName);

        try {
            Files.copy(backup.toPath(), restored.toPath(), StandardCopyOption.REPLACE_EXISTING);
            String sha = DeploymentService.computeSha256(restored);
            backup.delete();

            long duration = System.currentTimeMillis() - startTime;
            String msg = "Successfully rolled back to previous artifact: " + restored.getAbsolutePath() + " (SHA: " + sha + ")";
            logger.info(msg);
            return new DeploymentService.DeploymentResult(true, msg, restored.getAbsolutePath(),
                    restored.length(), sha, new Timestamp(System.currentTimeMillis()), duration / 1000.0);
        } catch (Exception e) {
            return DeploymentService.DeploymentResult.failure("Rollback exception: " + e.getMessage());
        }
    }
}
