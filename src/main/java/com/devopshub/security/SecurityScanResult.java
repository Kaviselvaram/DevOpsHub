package com.devopshub.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a security scanner invocation.
 */
public class SecurityScanResult {
    private final boolean success;
    private final String scannerName;
    private final String version;
    private final List<SecurityFinding> findings;
    private final String errorMessage;

    public SecurityScanResult(boolean success, String scannerName, String version, List<SecurityFinding> findings, String errorMessage) {
        this.success = success;
        this.scannerName = scannerName;
        this.version = version;
        this.findings = findings != null ? new ArrayList<>(findings) : new ArrayList<>();
        this.errorMessage = errorMessage;
    }

    public static SecurityScanResult success(String scannerName, String version, List<SecurityFinding> findings) {
        return new SecurityScanResult(true, scannerName, version, findings, null);
    }

    public static SecurityScanResult failure(String scannerName, String version, String errorMessage) {
        return new SecurityScanResult(false, scannerName, version, Collections.emptyList(), errorMessage);
    }

    public boolean isSuccess() { return success; }
    public String getScannerName() { return scannerName; }
    public String getVersion() { return version; }
    public List<SecurityFinding> getFindings() { return Collections.unmodifiableList(findings); }
    public String getErrorMessage() { return errorMessage; }

    public long getCountBySeverity(SecuritySeverity severity) {
        return findings.stream().filter(f -> f.getSeverity() == severity).count();
    }

    public boolean hasCriticalOrHigh() {
        return findings.stream().anyMatch(f -> f.getSeverity() == SecuritySeverity.CRITICAL || f.getSeverity() == SecuritySeverity.HIGH);
    }

    public String generateSummary() {
        long crit = getCountBySeverity(SecuritySeverity.CRITICAL);
        long high = getCountBySeverity(SecuritySeverity.HIGH);
        long med = getCountBySeverity(SecuritySeverity.MEDIUM);
        long low = getCountBySeverity(SecuritySeverity.LOW);
        long info = getCountBySeverity(SecuritySeverity.INFO);

        return String.format("%s (v%s) completed: %d total findings (Critical: %d, High: %d, Medium: %d, Low: %d, Info: %d)",
                scannerName, version, findings.size(), crit, high, med, low, info);
    }
}
