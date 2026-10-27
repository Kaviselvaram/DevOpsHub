package com.devopshub.execution;

import com.devopshub.execution.backend.ExecutionBackend;
import com.devopshub.execution.backend.ExecutionBackendFactory;
import com.devopshub.execution.isolation.ExecutionPolicy;
import com.devopshub.execution.isolation.ExecutionTrustLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * Safely executes stage commands by delegating to the appropriate isolation backend
 * (Docker container or restricted host execution) based on execution policy.
 */
public class ProcessExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ProcessExecutor.class);

    private final File workingDirectory;
    private final ExecutionPolicy executionPolicy;
    private final ExecutionBackend backend;

    public ProcessExecutor(File workingDirectory) {
        this(workingDirectory, new ExecutionPolicy(ExecutionTrustLevel.UNTRUSTED_EXTERNAL));
    }

    public ProcessExecutor(File workingDirectory, ExecutionPolicy executionPolicy) {
        this.workingDirectory = workingDirectory != null ? workingDirectory : new File(".");
        this.executionPolicy = executionPolicy != null ? executionPolicy :
                new ExecutionPolicy(ExecutionTrustLevel.UNTRUSTED_EXTERNAL);
        this.backend = ExecutionBackendFactory.getInstance().getBackend(this.executionPolicy);
        if (!this.workingDirectory.exists()) {
            this.workingDirectory.mkdirs();
        }
    }

    public ExecutionPolicy getExecutionPolicy() {
        return executionPolicy;
    }

    public ExecutionBackend getBackend() {
        return backend;
    }

    public int execute(String command, String stageExecutionId, String stageName, int timeoutSeconds, LogCollector logCollector) {
        return execute(command, stageExecutionId, stageName, timeoutSeconds, logCollector, null);
    }

    public int execute(String command, String stageExecutionId, String stageName, int timeoutSeconds, LogCollector logCollector, Map<String, String> environmentVariables) {
        return backend.execute(command, stageExecutionId, stageName, timeoutSeconds, workingDirectory, environmentVariables, logCollector, executionPolicy);
    }

    public void cancel() {
        if (backend != null) {
            backend.cancel();
        }
    }
}
