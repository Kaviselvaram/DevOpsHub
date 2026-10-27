package com.devopshub.security;

import java.io.File;

/**
 * Contract for genuine security scanner engines.
 */
public interface SecurityScanner {
    /**
     * Executes security analysis on the target workspace directory.
     *
     * @param workspaceDir the project workspace root
     * @param executionId pipeline execution ID
     * @param stageExecutionId stage execution ID
     * @return the scan result with detected findings
     */
    SecurityScanResult scan(File workspaceDir, String executionId, String stageExecutionId);
}
