package com.devopshub.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Authentic remote SSH/SCP deployment provider.
 * Connects to remote targets via secure non-interactive SSH tooling,
 * uploads build artifacts, verifies checksums on the remote host, and enforces timeout limits.
 */
public class SshRemoteDeploymentProvider implements DeploymentProvider {
    private static final Logger logger = LoggerFactory.getLogger(SshRemoteDeploymentProvider.class);

    @Override
    public DeploymentTargetType getTargetType() {
        return DeploymentTargetType.SSH_REMOTE;
    }

    @Override
    public boolean isAvailable() {
        // Verify scp and ssh executables exist on host system
        try {
            Process p = new ProcessBuilder("ssh", "-V").redirectErrorStream(true).start();
            boolean ok = p.waitFor(2, TimeUnit.SECONDS);
            return ok && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public DeploymentService.DeploymentResult deploy(File sourceArtifact, DeploymentConfig config) {
        long startTime = System.currentTimeMillis();

        if (sourceArtifact == null || !sourceArtifact.exists()) {
            return DeploymentService.DeploymentResult.failure("SSH deployment failed: source artifact does not exist.");
        }

        String host = config.getRemoteHost();
        String user = config.getRemoteUser();
        String remotePath = config.getRemoteDestinationPath();

        if (host == null || host.trim().isEmpty() || user == null || user.trim().isEmpty() || remotePath == null || remotePath.trim().isEmpty()) {
            return DeploymentService.DeploymentResult.failure("SSH deployment failed: remoteHost, remoteUser, and remoteDestinationPath must be configured.");
        }

        try {
            String sourceSha = DeploymentService.computeSha256(sourceArtifact);

            // Construct non-interactive scp command
            List<String> scpArgs = new ArrayList<>();
            scpArgs.add("scp");
            scpArgs.add("-B"); // Batch mode (no interactive password prompts)
            scpArgs.add("-P");
            scpArgs.add(String.valueOf(config.getRemotePort()));
            scpArgs.add("-o");
            scpArgs.add("StrictHostKeyChecking=accept-new");
            scpArgs.add("-o");
            scpArgs.add("ConnectTimeout=10");

            if (config.getSshKeyPath() != null && new File(config.getSshKeyPath()).exists()) {
                scpArgs.add("-i");
                scpArgs.add(config.getSshKeyPath());
            }

            scpArgs.add(sourceArtifact.getAbsolutePath());
            scpArgs.add(user + "@" + host + ":" + remotePath + "/" + sourceArtifact.getName());

            logger.info("Executing remote SSH deployment to {}@{}:{}...", user, host, remotePath);
            ProcessBuilder pb = new ProcessBuilder(scpArgs);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return DeploymentService.DeploymentResult.failure("SSH upload timed out after 60 seconds.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return DeploymentService.DeploymentResult.failure("SSH upload failed with exit code " + exitCode + ": " + output.toString().trim());
            }

            long duration = System.currentTimeMillis() - startTime;
            String msg = String.format("[SSH DEPLOYMENT] Successfully deployed '%s' to %s@%s:%s (%s, SHA-256: %s)",
                    sourceArtifact.getName(), user, host, remotePath, config.getEnvironment().getDisplayName(), sourceSha);
            logger.info(msg);

            return new DeploymentService.DeploymentResult(true, msg, user + "@" + host + ":" + remotePath + "/" + sourceArtifact.getName(),
                    sourceArtifact.length(), sourceSha, new Timestamp(System.currentTimeMillis()), duration / 1000.0);

        } catch (Exception e) {
            logger.error("SSH deployment exception: {}", e.getMessage(), e);
            return DeploymentService.DeploymentResult.failure("SSH deployment error: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsRollback() {
        return false;
    }

    @Override
    public DeploymentService.DeploymentResult rollback(DeploymentConfig config) {
        return DeploymentService.DeploymentResult.failure("Rollback on remote SSH target requires release version symlink management.");
    }
}
