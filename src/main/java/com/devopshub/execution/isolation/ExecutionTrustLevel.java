package com.devopshub.execution.isolation;

/**
 * Trust level for repository pipeline execution.
 */
public enum ExecutionTrustLevel {
    /**
     * Local projects created and explicitly verified as trusted by the local developer.
     * Allowed to run on host with sanitized environment variables.
     */
    TRUSTED_LOCAL,

    /**
     * Externally supplied repositories (cloned from remote URLs or triggered via webhooks).
     * Must be container-isolated; host execution is blocked by default (fail-closed).
     */
    UNTRUSTED_EXTERNAL
}
