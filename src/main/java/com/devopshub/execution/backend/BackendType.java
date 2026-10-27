package com.devopshub.execution.backend;

/**
 * Enumeration of supported execution backend types in DevOpsHub.
 */
public enum BackendType {
    /**
     * Hardened container execution via Docker runtime with namespace and resource sandboxing.
     * Required for untrusted external repositories.
     */
    DOCKER,

    /**
     * Host process execution restricted to explicitly trusted local projects.
     * Enforces working directory confinement and environment sanitization.
     */
    HOST,

    /**
     * Explicit unavailable state when required container runtime is absent or unhealthy.
     * Fails closed to prevent unauthorized host execution.
     */
    UNAVAILABLE
}
