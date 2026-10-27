package com.devopshub.execution.backend;

import com.devopshub.config.AppConfig;
import com.devopshub.exception.ExecutionTimeoutException;
import com.devopshub.execution.LogCollector;
import com.devopshub.execution.isolation.ExecutionPolicy;
import com.devopshub.execution.isolation.ExecutionTrustLevel;
import com.devopshub.model.LogLevel;
import com.devopshub.model.LogStream;
import com.devopshub.util.SecretMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Host-level process execution backend.
 * Strictly restricted to explicitly verified TRUSTED_LOCAL projects.
 * Enforces working directory confinement, environment sanitization, process-tree termination,
 * and secret masking.
 */
public class HostExecutionBackend implements ExecutionBackend {
    private static final Logger logger = LoggerFactory.getLogger(HostExecutionBackend.class);

    private volatile Process currentProcess;

    @Override
    public BackendType getBackendType() {
        return BackendType.HOST;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getAvailabilityDetails() {
        return "Host execution engine operational (restricted to verified TRUSTED_LOCAL projects).";
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

        // Host execution is forbidden for untrusted external workloads
        if (policy.getTrustLevel() == ExecutionTrustLevel.UNTRUSTED_EXTERNAL) {
            String rejection = "Security Policy Violation (SEC-01): Untrusted repository cannot execute directly on the host machine. Container isolation is required.";
            logger.error("Host execution rejected: {}", rejection);
            logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR, rejection);
            return 126;
        }

        if (!policy.canExecute()) {
            String rejection = policy.getRejectionReason();
            logger.error("Execution rejected by security policy: {}", rejection);
            logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR, rejection);
            return 126;
        }

        File baseWorkspace = new File(AppConfig.getInstance().getWorkspaceDir());
        try {
            policy.validateWorkingDirectory(workingDir, baseWorkspace);
        } catch (SecurityException se) {
            logger.error("Working directory security violation: {}", se.getMessage());
            logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                    "Security violation: " + se.getMessage());
            return 126;
        }

        ProcessBuilder pb;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("/bin/sh", "-c", command);
        }

        pb.directory(workingDir != null ? workingDir : new File("."));
        pb.environment().clear();
        Map<String, String> sanitizedEnv = policy.sanitizeEnvironment(environment);
        pb.environment().putAll(sanitizedEnv);
        pb.redirectErrorStream(false);

        String maskedCommand = SecretMasker.mask(command);
        logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT,
                "[HOST EXECUTION] Trust Level: " + policy.getTrustLevel() + " | Directory: " + workingDir);
        logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT, "Executing: " + maskedCommand);

        ExecutorService streamReaders = Executors.newFixedThreadPool(2);
        try {
            Process process = pb.start();
            this.currentProcess = process;

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
                        "Stage execution timed out after " + effectiveTimeout + " seconds.");
                terminateProcessTree(process);
                throw new ExecutionTimeoutException("Stage timed out after " + effectiveTimeout + "s", effectiveTimeout);
            }

            try {
                stdoutFuture.get(1, TimeUnit.SECONDS);
                stderrFuture.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                        "Process exited with non-zero code: " + exitCode);
            } else {
                logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT,
                        "Process completed successfully with exit code 0");
            }
            return exitCode;

        } catch (InterruptedException e) {
            logCollector.append(stageExecutionId, stageName, LogLevel.WARN, LogStream.STDERR,
                    "Stage execution interrupted or cancelled by user.");
            terminateProcessTree(currentProcess);
            Thread.currentThread().interrupt();
            return 130;
        } catch (ExecutionTimeoutException e) {
            return 124;
        } catch (Exception e) {
            terminateProcessTree(currentProcess);
            logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                    "Failed to execute process: " + e.getMessage());
            return 1;
        } finally {
            this.currentProcess = null;
            streamReaders.shutdownNow();
            logCollector.flush();
        }
    }

    @Override
    public void cancel() {
        terminateProcessTree(currentProcess);
    }

    private void terminateProcessTree(Process process) {
        if (process == null) return;
        try {
            process.descendants().forEach(handle -> {
                try {
                    handle.destroyForcibly();
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        try {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            process.destroyForcibly();
        }
    }
}
