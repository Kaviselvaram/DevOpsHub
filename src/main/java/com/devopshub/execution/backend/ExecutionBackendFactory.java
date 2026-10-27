package com.devopshub.execution.backend;

import com.devopshub.config.AppConfig;
import com.devopshub.execution.isolation.ExecutionPolicy;
import com.devopshub.execution.isolation.ExecutionTrustLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for resolving and validating execution backends based on trust level and runtime capabilities.
 * Guarantees that untrusted code NEVER silently falls back to host execution.
 */
public class ExecutionBackendFactory {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionBackendFactory.class);

    private static final ExecutionBackendFactory INSTANCE = new ExecutionBackendFactory();

    private final DockerExecutionBackend dockerBackend = new DockerExecutionBackend();
    private final HostExecutionBackend hostBackend = new HostExecutionBackend();

    private ExecutionBackendFactory() {}

    public static ExecutionBackendFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Accurately determines if a verified container runtime is operational.
     * Configuration cannot force this to true if the runtime daemon is unreachable.
     */
    public boolean isContainerAvailable() {
        String containerEnabled = AppConfig.getInstance().get("EXECUTION_CONTAINER_ENABLED", "true");
        if ("false".equalsIgnoreCase(containerEnabled)) {
            return false;
        }
        return dockerBackend.isAvailable();
    }

    /**
     * Resolves the appropriate ExecutionBackend for the given execution policy.
     * Enforces fail-closed posture for untrusted workloads.
     */
    public ExecutionBackend getBackend(ExecutionPolicy policy) {
        if (policy == null || !policy.canExecute()) {
            String reason = policy != null ? policy.getRejectionReason() : "Execution policy is null.";
            return new UnavailableExecutionBackend(reason);
        }

        ExecutionTrustLevel trustLevel = policy.getTrustLevel();

        if (trustLevel == ExecutionTrustLevel.UNTRUSTED_EXTERNAL) {
            if (policy.isContainerAvailable() && isContainerAvailable()) {
                logger.info("Selected Docker container backend for UNTRUSTED_EXTERNAL workload.");
                return dockerBackend;
            } else {
                String detail = !policy.isContainerAvailable()
                        ? "Container execution disabled by policy: " + policy.getRejectionReason()
                        : dockerBackend.getAvailabilityDetails();
                logger.warn("Container runtime required for UNTRUSTED_EXTERNAL workload is unavailable: {}", detail);
                return new UnavailableExecutionBackend(detail);
            }
        }

        // TRUSTED_LOCAL
        if (policy.isAllowHostExecutionForTrusted()) {
            return hostBackend;
        } else if (isContainerAvailable()) {
            return dockerBackend;
        } else {
            return new UnavailableExecutionBackend("Host execution is disabled and container runtime is unavailable.");
        }
    }

    public DockerExecutionBackend getDockerBackend() {
        return dockerBackend;
    }

    public HostExecutionBackend getHostBackend() {
        return hostBackend;
    }
}
