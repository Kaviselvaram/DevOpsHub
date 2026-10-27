package com.devopshub.execution.backend;

import com.devopshub.execution.LogCollector;
import com.devopshub.execution.isolation.ExecutionPolicy;

import java.io.File;
import java.util.Map;

/**
 * Common execution backend contract for running pipeline stage commands.
 * Provides abstraction across Docker container isolation and local host execution.
 */
public interface ExecutionBackend {

    /**
     * Identifies the execution backend type.
     */
    BackendType getBackendType();

    /**
     * Determines whether this backend runtime is currently discovered, healthy, and operational.
     */
    boolean isAvailable();

    /**
     * Returns human-readable diagnostic details describing backend status or failure reason.
     */
    String getAvailabilityDetails();

    /**
     * Executes a stage command inside this backend's isolation boundary.
     *
     * @param command Command string to execute
     * @param stageExecutionId ID of the stage execution record
     * @param stageName Name of the stage being executed
     * @param timeoutSeconds Execution timeout limit in seconds
     * @param workingDir Target workspace directory
     * @param environment Sanitized environment variables to pass
     * @param logCollector Collector for streaming stdout and stderr logs
     * @param policy Security policy governing this execution
     * @return Raw POSIX exit code of the executed command
     */
    int execute(String command,
                String stageExecutionId,
                String stageName,
                int timeoutSeconds,
                File workingDir,
                Map<String, String> environment,
                LogCollector logCollector,
                ExecutionPolicy policy);

    /**
     * Actively cancels any running process or container managed by this backend.
     */
    void cancel();
}
