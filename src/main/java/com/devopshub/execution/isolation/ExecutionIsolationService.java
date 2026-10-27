package com.devopshub.execution.isolation;

import com.devopshub.config.AppConfig;
import com.devopshub.model.Project;
import com.devopshub.model.TriggerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Service managing execution isolation policies, trust level classification,
 * and secure process boundary enforcement.
 */
public class ExecutionIsolationService {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionIsolationService.class);
    private static final ExecutionIsolationService INSTANCE = new ExecutionIsolationService();

    private ExecutionIsolationService() {}

    public static ExecutionIsolationService getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves the appropriate ExecutionTrustLevel for a project and trigger combination.
     * Webhook triggers and remote Git repositories default to UNTRUSTED_EXTERNAL unless
     * explicitly configured as trusted in project settings.
     */
    public ExecutionTrustLevel resolveTrustLevel(Project project, TriggerType triggerType) {
        // Any execution triggered remotely by webhooks must be treated as untrusted
        if (triggerType == TriggerType.WEBHOOK) {
            return ExecutionTrustLevel.UNTRUSTED_EXTERNAL;
        }

        if (project == null) {
            return ExecutionTrustLevel.UNTRUSTED_EXTERNAL;
        }

        String repoUrl = project.getRepositoryUrl();
        if (repoUrl == null || repoUrl.trim().isEmpty()) {
            return ExecutionTrustLevel.UNTRUSTED_EXTERNAL;
        }

        String trimmed = repoUrl.trim();
        // Local directory paths and local test schemes are considered local
        boolean isLocalPath = trimmed.startsWith("/") || trimmed.startsWith("file:/") || trimmed.startsWith("file://")
                || trimmed.startsWith("local://") || trimmed.startsWith("test://")
                || trimmed.matches("^[a-zA-Z]:[\\\\/].*") || new File(trimmed).isDirectory();
        if (isLocalPath) {
            return ExecutionTrustLevel.TRUSTED_LOCAL;
        }

        // Allow explicit override in config for developer testing of trusted remote repos.
        // MUST default to false for secure fail-closed posture.
        boolean allowRemoteAsTrusted = "true".equalsIgnoreCase(
                AppConfig.getInstance().get("ALLOW_REMOTE_REPOS_AS_TRUSTED", "false"));

        if (allowRemoteAsTrusted) {
            return ExecutionTrustLevel.TRUSTED_LOCAL;
        }

        return ExecutionTrustLevel.UNTRUSTED_EXTERNAL;
    }

    /**
     * Resolves an ExecutionPolicy for the given trust level.
     */
    public ExecutionPolicy resolvePolicy(ExecutionTrustLevel trustLevel) {
        return new ExecutionPolicy(trustLevel);
    }
}
