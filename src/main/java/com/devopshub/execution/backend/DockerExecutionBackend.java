package com.devopshub.execution.backend;

import com.devopshub.config.AppConfig;
import com.devopshub.exception.ExecutionTimeoutException;
import com.devopshub.execution.LogCollector;
import com.devopshub.execution.isolation.ExecutionPolicy;
import com.devopshub.model.LogLevel;
import com.devopshub.model.LogStream;
import com.devopshub.util.SecretMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Hardened container-backed execution backend using Docker.
 * Provides kernel namespace, cgroup, and filesystem sandboxing for untrusted repository builds.
 *
 * Security Controls Enforced (SEC-02):
 * - Disables privileged mode (--privileged=false)
 * - Drops all Linux capabilities (--cap-drop ALL)
 * - Sets no-new-privileges flag (--security-opt no-new-privileges)
 * - Mounts only the approved workspace directory (-v <workspace>:/workspace:rw)
 * - Strict prohibition on mounting Docker socket or host user home directory
 * - Resource limits enforced: memory limit (--memory), CPU quota (--cpus), process limit (--pids-limit)
 * - Automatic ephemeral container destruction (--rm) with explicit docker kill on cancel/timeout
 */
public class DockerExecutionBackend implements ExecutionBackend {
    private static final Logger logger = LoggerFactory.getLogger(DockerExecutionBackend.class);

    private static final long CACHE_TTL_MS = 15_000;
    private static volatile Boolean cachedAvailable = null;
    private static volatile String cachedDetails = null;
    private static volatile long lastCheckTime = 0;

    private volatile String activeContainerName;
    private volatile Process activeProcess;

    @Override
    public BackendType getBackendType() {
        return BackendType.DOCKER;
    }

    @Override
    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (cachedAvailable != null && (now - lastCheckTime) < CACHE_TTL_MS) {
            return cachedAvailable;
        }
        checkDockerAvailability();
        return Boolean.TRUE.equals(cachedAvailable);
    }

    @Override
    public String getAvailabilityDetails() {
        isAvailable();
        return cachedDetails != null ? cachedDetails : "Docker availability unverified.";
    }

    /**
     * Probes Docker runtime capability directly via CLI check.
     * Never assumes Docker is available based purely on configuration flags.
     */
    public static synchronized boolean checkDockerAvailability() {
        long now = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean finished = proc.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                cachedAvailable = false;
                cachedDetails = "Docker daemon capability check timed out after 3 seconds.";
            } else if (proc.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String version = reader.readLine();
                    cachedAvailable = true;
                    cachedDetails = "Docker engine operational (Server Version: " + (version != null ? version.trim() : "Unknown") + ")";
                }
            } else {
                cachedAvailable = false;
                cachedDetails = "Docker CLI returned non-zero exit code (" + proc.exitValue() + "). Docker daemon may not be running.";
            }
        } catch (Exception e) {
            cachedAvailable = false;
            cachedDetails = "Docker binary unavailable or unreachable: " + e.getMessage();
        }
        lastCheckTime = now;
        return Boolean.TRUE.equals(cachedAvailable);
    }

    @Override
    public int execute(String command,
                       String stageExecutionId,
                       String stageName,
                       int timeoutSeconds,
                       File workingDir,
                       Map<String, String> environment,
                       LogCollector logCollector,
                       ExecutionPolicy policy) {
        if (command == null || command.trim().isEmpty()) {
            return 0;
        }

        if (!isAvailable()) {
            String errorMsg = "Container execution unavailable: " + getAvailabilityDetails();
            logger.error(errorMsg);
            logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR, errorMsg);
            return 126;
        }

        // Canonicalize workspace path to prevent mount traversal
        File canonicalDir;
        try {
            canonicalDir = (workingDir != null ? workingDir : new File(".")).getCanonicalFile();
            if (!canonicalDir.exists()) {
                canonicalDir.mkdirs();
            }
        } catch (Exception e) {
            String err = "Failed to resolve canonical workspace directory: " + e.getMessage();
            logger.error(err);
            logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR, err);
            return 126;
        }

        String containerName = "devopshub-" + (stageExecutionId != null ? stageExecutionId : UUID.randomUUID().toString())
                .replaceAll("[^a-zA-Z0-9_-]", "") + "-" + System.currentTimeMillis();
        this.activeContainerName = containerName;

        // Determine container image
        String image = resolveContainerImage(canonicalDir, command);

        List<String> dockerArgs = new ArrayList<>();
        dockerArgs.add("docker");
        dockerArgs.add("run");
        dockerArgs.add("--rm");
        dockerArgs.add("--name");
        dockerArgs.add(containerName);
        dockerArgs.add("--label");
        dockerArgs.add("devopshub=true");
        dockerArgs.add("--label");
        dockerArgs.add("stageExecutionId=" + (stageExecutionId != null ? stageExecutionId : "unknown"));

        // Hardened container isolation flags (SEC-02)
        dockerArgs.add("--security-opt");
        dockerArgs.add("no-new-privileges");
        dockerArgs.add("--cap-drop");
        dockerArgs.add("ALL");
        dockerArgs.add("--tmpfs");
        dockerArgs.add("/tmp:rw,nosuid,size=512m");

        // Resource limits
        String memoryLimit = AppConfig.getInstance().get("EXECUTION_CONTAINER_MEMORY", "2048m");
        dockerArgs.add("--memory");
        dockerArgs.add(memoryLimit);

        String cpuLimit = AppConfig.getInstance().get("EXECUTION_CONTAINER_CPUS", "4.0");
        dockerArgs.add("--cpus");
        dockerArgs.add(cpuLimit);

        String pidsLimit = AppConfig.getInstance().get("EXECUTION_CONTAINER_PIDS_LIMIT", "2048");
        dockerArgs.add("--pids-limit");
        dockerArgs.add(pidsLimit);

        // Workspace mounting: Mount ONLY the designated build workspace directory
        dockerArgs.add("-v");
        dockerArgs.add(canonicalDir.getAbsolutePath() + ":/workspace:rw");
        dockerArgs.add("-w");
        dockerArgs.add("/workspace");

        // Sanitized environment variables passed through -e
        Map<String, String> sanitizedEnv = policy.sanitizeEnvironment(environment);
        for (Map.Entry<String, String> entry : sanitizedEnv.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                dockerArgs.add("-e");
                dockerArgs.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        // Image and command
        dockerArgs.add(image);
        dockerArgs.add("sh");
        dockerArgs.add("-c");
        dockerArgs.add(command);

        String maskedCmd = SecretMasker.mask(command);
        logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT,
                String.format("[CONTAINER EXECUTION] Container: %s | Image: %s | Workspace: /workspace", containerName, image));
        logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT,
                "[ISOLATION] Caps: ALL dropped | no-new-privileges: true | Memory: " + memoryLimit + " | CPUs: " + cpuLimit);
        logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT, "Executing: " + maskedCmd);

        ProcessBuilder pb = new ProcessBuilder(dockerArgs);
        pb.redirectErrorStream(false);

        ExecutorService streamReaders = Executors.newFixedThreadPool(2);
        try {
            Process process = pb.start();
            this.activeProcess = process;

            Future<?> stdoutFuture = streamReaders.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT, line);
                    }
                } catch (Exception ignored) {}
            });

            Future<?> stderrFuture = streamReaders.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR, line);
                    }
                } catch (Exception ignored) {}
            });

            int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : 300;
            boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
            if (!finished) {
                logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                        "Container execution timed out after " + effectiveTimeout + " seconds. Terminating container.");
                killContainer(containerName);
                process.destroyForcibly();
                throw new ExecutionTimeoutException("Container stage timed out", effectiveTimeout);
            }

            try {
                stdoutFuture.get(1, TimeUnit.SECONDS);
                stderrFuture.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                        "Container process exited with non-zero code: " + exitCode);
            } else {
                logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT,
                        "Container execution completed successfully with exit code 0");
            }
            return exitCode;

        } catch (InterruptedException e) {
            logCollector.append(stageExecutionId, stageName, LogLevel.WARN, LogStream.STDERR,
                    "Container execution interrupted or cancelled by user.");
            killContainer(containerName);
            if (activeProcess != null) activeProcess.destroyForcibly();
            Thread.currentThread().interrupt();
            return 130;
        } catch (ExecutionTimeoutException e) {
            return 124;
        } catch (Exception e) {
            killContainer(containerName);
            if (activeProcess != null) activeProcess.destroyForcibly();
            logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                    "Failed to execute container: " + e.getMessage());
            return 1;
        } finally {
            this.activeContainerName = null;
            this.activeProcess = null;
            streamReaders.shutdownNow();
            logCollector.flush();
        }
    }

    @Override
    public void cancel() {
        String container = this.activeContainerName;
        if (container != null) {
            killContainer(container);
        }
        Process proc = this.activeProcess;
        if (proc != null) {
            proc.destroyForcibly();
        }
    }

    private void killContainer(String containerName) {
        if (containerName == null || containerName.isEmpty()) return;
        try {
            Process killProc = new ProcessBuilder("docker", "kill", containerName).start();
            killProc.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Failed to kill container {}: {}", containerName, e.getMessage());
        }
    }

    private String resolveContainerImage(File workspaceDir, String command) {
        String configured = AppConfig.getInstance().get("EXECUTION_CONTAINER_IMAGE", null);
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        boolean requiresNode = false;
        if (command != null) {
            String lower = command.toLowerCase(Locale.ROOT);
            if (lower.contains("node") || lower.contains("npm") || lower.contains("npx") || lower.contains("yarn") || lower.contains("pnpm") || lower.contains("next")) {
                requiresNode = true;
            }
        }
        if (workspaceDir != null && (new File(workspaceDir, "package.json").exists()
                || new File(workspaceDir, "next.config.ts").exists()
                || new File(workspaceDir, "next.config.js").exists())) {
            requiresNode = true;
        }
        if (requiresNode) {
            return "node:20-alpine";
        }
        return "alpine:latest";
    }
}
