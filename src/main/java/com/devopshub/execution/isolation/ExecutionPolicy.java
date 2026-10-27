package com.devopshub.execution.isolation;

import com.devopshub.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Execution policy defining security boundaries, environment sanitization,
 * working directory confinement, and isolation rules for pipeline executions.
 */
public class ExecutionPolicy {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionPolicy.class);

    private final ExecutionTrustLevel trustLevel;
    private final boolean containerAvailable;
    private final boolean allowHostExecutionForTrusted;

    public ExecutionPolicy(ExecutionTrustLevel trustLevel) {
        this(trustLevel,
             com.devopshub.execution.backend.ExecutionBackendFactory.getInstance().isContainerAvailable(),
             "true".equalsIgnoreCase(AppConfig.getInstance().get("ALLOW_HOST_EXECUTION_FOR_TRUSTED", "true")));
    }

    public ExecutionPolicy(ExecutionTrustLevel trustLevel, boolean containerAvailable, boolean allowHostExecutionForTrusted) {
        this.trustLevel = trustLevel != null ? trustLevel : ExecutionTrustLevel.UNTRUSTED_EXTERNAL;
        this.containerAvailable = containerAvailable;
        this.allowHostExecutionForTrusted = allowHostExecutionForTrusted;
    }

    public ExecutionTrustLevel getTrustLevel() {
        return trustLevel;
    }

    public boolean isContainerAvailable() {
        return containerAvailable;
    }

    public boolean isAllowHostExecutionForTrusted() {
        return allowHostExecutionForTrusted;
    }

    public boolean canExecute() {
        if (trustLevel == ExecutionTrustLevel.TRUSTED_LOCAL) {
            return allowHostExecutionForTrusted || containerAvailable;
        }
        // Untrusted external code requires container isolation. If container is not available, fail closed.
        return containerAvailable;
    }

    public String getRejectionReason() {
        if (canExecute()) {
            return null;
        }
        if (trustLevel == ExecutionTrustLevel.UNTRUSTED_EXTERNAL && !containerAvailable) {
            return "Execution blocked by security policy (SEC-01): Untrusted or externally supplied repository execution " +
                   "requires container isolation, which is not available on this host. " +
                   "DevOpsHub fails closed to prevent executing untrusted code with host desktop user privileges.";
        }
        return "Execution blocked by security policy: Host execution is disabled.";
    }

    /**
     * Sanitizes the environment variables to be passed to a child process.
     * Prevents leaking host credentials, cloud tokens, database secrets, and DevOpsHub configuration.
     */
    public Map<String, String> sanitizeEnvironment(Map<String, String> additionalEnv) {
        Map<String, String> sanitized = new HashMap<>();

        // Whitelisted safe system environment variables required for standard toolchains (Maven, Gradle, npm, etc.)
        Set<String> whitelistedKeys = Set.of(
                "PATH", "TEMP", "TMP", "TMPDIR", "LANG", "LC_ALL",
                "JAVA_HOME", "NODE_PATH", "MAVEN_HOME", "M2_HOME", "GRADLE_HOME",
                "SYSTEMROOT", "SystemRoot", "WINDIR", "windir", "COMSPEC", "PATHEXT",
                "USER", "LOGNAME", "SHELL", "TERM"
        );

        for (String key : whitelistedKeys) {
            String val = System.getenv(key);
            if (val != null) {
                sanitized.put(key, val);
            }
        }

        // Ensure PATH contains standard executable locations appropriate for the host OS
        String currentPath = sanitized.getOrDefault("PATH", "");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String[] winPaths = {"C:\\Windows\\System32", "C:\\Windows"};
            for (String p : winPaths) {
                if (!currentPath.toLowerCase(Locale.ROOT).contains(p.toLowerCase(Locale.ROOT))) {
                    currentPath = currentPath.isEmpty() ? p : currentPath + File.pathSeparator + p;
                }
            }
        } else {
            String[] essentialPaths = {"/opt/homebrew/bin", "/opt/homebrew/sbin", "/usr/local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin"};
            for (String p : essentialPaths) {
                if (!currentPath.contains(p)) {
                    currentPath = currentPath.isEmpty() ? p : p + File.pathSeparator + currentPath;
                }
            }
        }
        sanitized.put("PATH", currentPath);

        // If untrusted, prevent writing directly to host user home (e.g. ~/.ssh, ~/.aws)
        if (trustLevel == ExecutionTrustLevel.UNTRUSTED_EXTERNAL) {
            String tempDir = System.getProperty("java.io.tmpdir", "/tmp");
            sanitized.put("HOME", tempDir);
            sanitized.put("USERPROFILE", tempDir);
        } else {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                sanitized.put("HOME", userHome);
                sanitized.put("USERPROFILE", userHome);
            }
        }

        // Apply provided additional environment variables, rejecting any sensitive patterns
        if (additionalEnv != null) {
            for (Map.Entry<String, String> entry : additionalEnv.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if (k == null || v == null) continue;

                // Check for sensitive variable names that must never be set or leaked
                if (isSensitiveVariableName(k)) {
                    logger.warn("Security policy blocked sensitive environment variable: {}", k);
                    continue;
                }
                sanitized.put(k, v);
            }
        }

        return sanitized;
    }

    /**
     * Validates that the working directory is within an authorized workspace and not
     * the DevOpsHub source repository root or host filesystem root.
     * Host process execution is NOT a sandbox; directory validation prevents host traversal.
     */
    public void validateWorkingDirectory(File workingDir, File baseWorkspaceDir) throws SecurityException {
        if (workingDir == null) {
            throw new SecurityException("Working directory cannot be null.");
        }
        try {
            String canonicalWork = workingDir.getCanonicalPath();
            String appRoot = new File(".").getCanonicalPath();

            // Guard against executing in DevOpsHub's own root application repository
            if (canonicalWork.equals(appRoot)) {
                throw new SecurityException("Security violation: Subprocess execution in the DevOpsHub root directory is forbidden.");
            }

            // Guard against root directory execution
            if (canonicalWork.equals("/") || canonicalWork.equalsIgnoreCase("C:\\") || canonicalWork.equalsIgnoreCase("C:/")) {
                throw new SecurityException("Security violation: Subprocess execution in the filesystem root is forbidden.");
            }

            if (baseWorkspaceDir != null && baseWorkspaceDir.exists()) {
                String canonicalBase = baseWorkspaceDir.getCanonicalPath();
                boolean withinBase = canonicalWork.startsWith(canonicalBase + File.separator) || canonicalWork.equals(canonicalBase);
                String authorizedWorkspaces = appRoot + File.separator + "workspaces";
                boolean withinAppWorkspaces = canonicalWork.startsWith(authorizedWorkspaces + File.separator) || canonicalWork.equals(authorizedWorkspaces);
                if (trustLevel == ExecutionTrustLevel.UNTRUSTED_EXTERNAL && !withinBase && !withinAppWorkspaces) {
                    throw new SecurityException("Security violation: Untrusted external workload cannot execute outside workspace root: " + canonicalWork);
                } else if (!withinBase && !withinAppWorkspaces) {
                    logger.warn("Working directory {} is outside designated workspace root {}", canonicalWork, canonicalBase);
                }
            }
        } catch (IOException e) {
            throw new SecurityException("Failed to canonicalize working directory path: " + e.getMessage());
        }
    }

    private boolean isSensitiveVariableName(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("TOKEN") || upper.contains("SECRET") || upper.contains("KEY") ||
               upper.contains("PASSWORD") || upper.contains("PASSWD") || upper.contains("AUTH") ||
               upper.contains("CREDENTIAL") || upper.startsWith("AWS_") || upper.startsWith("AZURE_") ||
               upper.startsWith("GCP_") || upper.startsWith("GOOGLE_") || upper.startsWith("GITHUB_") ||
               upper.startsWith("GITLAB_") || upper.startsWith("SSH_") || upper.startsWith("DB_") ||
               upper.startsWith("DATABASE_") || upper.startsWith("DEVOPSHUB_DB_");
    }
}
