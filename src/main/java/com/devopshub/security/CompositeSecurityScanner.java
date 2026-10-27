package com.devopshub.security;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Composite security scanner executing all active security scanner engines
 * and consolidating results.
 */
public class CompositeSecurityScanner implements SecurityScanner {
    private static final String VERSION = "1.0.0";
    private final List<SecurityScanner> scanners = new ArrayList<>();

    public CompositeSecurityScanner() {
        this.scanners.add(new SecretScanner());
        this.scanners.add(new DependencyAuditScanner());
    }

    public CompositeSecurityScanner(List<SecurityScanner> scanners) {
        if (scanners != null) {
            this.scanners.addAll(scanners);
        }
    }

    @Override
    public SecurityScanResult scan(File workspaceDir, String executionId, String stageExecutionId) {
        List<SecurityFinding> allFindings = new ArrayList<>();
        StringBuilder errors = new StringBuilder();
        boolean anySuccess = false;

        for (SecurityScanner scanner : scanners) {
            SecurityScanResult res = scanner.scan(workspaceDir, executionId, stageExecutionId);
            if (res.isSuccess()) {
                anySuccess = true;
                allFindings.addAll(res.getFindings());
            } else {
                if (errors.length() > 0) errors.append("; ");
                errors.append(res.getScannerName()).append(": ").append(res.getErrorMessage());
            }
        }

        if (!anySuccess && errors.length() > 0) {
            return SecurityScanResult.failure("DevOpsHub Security Suite", VERSION, errors.toString());
        }

        return SecurityScanResult.success("DevOpsHub Security Suite", VERSION, allFindings);
    }
}
