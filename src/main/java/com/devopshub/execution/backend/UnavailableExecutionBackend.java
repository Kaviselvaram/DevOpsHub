package com.devopshub.execution.backend;

import com.devopshub.execution.LogCollector;
import com.devopshub.execution.isolation.ExecutionPolicy;
import com.devopshub.model.LogLevel;
import com.devopshub.model.LogStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * Safe fail-closed backend used when container isolation is required but unavailable.
 * Prohibits silent fallback to host execution.
 */
public class UnavailableExecutionBackend implements ExecutionBackend {
    private static final Logger logger = LoggerFactory.getLogger(UnavailableExecutionBackend.class);
    private final String reason;

    public UnavailableExecutionBackend(String reason) {
        this.reason = reason != null ? reason : "Container execution backend is unavailable.";
    }

    @Override
    public BackendType getBackendType() {
        return BackendType.UNAVAILABLE;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getAvailabilityDetails() {
        return reason;
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
        String msg = "Execution blocked (SEC-01): Untrusted workload requires container isolation, " +
                     "but container runtime is unavailable: " + reason + ". Fails closed.";
        logger.error(msg);
        logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR, msg);
        logCollector.flush();
        return 126;
    }

    @Override
    public void cancel() {
        // No-op
    }
}
