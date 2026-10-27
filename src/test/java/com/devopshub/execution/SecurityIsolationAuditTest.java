package com.devopshub.execution;

import com.devopshub.execution.isolation.ExecutionPolicy;
import com.devopshub.execution.isolation.ExecutionTrustLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SEC-01: Execution Isolation & Security Boundary Tests")
class SecurityIsolationAuditTest {

    @Test
    @DisplayName("Sanitized environment must strip host secrets, tokens, AWS keys, and database passwords")
    void testEnvironmentSanitizationStripsSensitiveVariables() {
        ExecutionPolicy policy = new ExecutionPolicy(ExecutionTrustLevel.TRUSTED_LOCAL);

        Map<String, String> userEnv = new HashMap<>();
        userEnv.put("SAFE_CUSTOM_VAR", "my-safe-value");
        userEnv.put("AWS_SECRET_ACCESS_KEY", "AKIAIOSFODNN7EXAMPLE");
        userEnv.put("GITHUB_TOKEN", "ghp_1234567890abcdef");
        userEnv.put("DATABASE_PASSWORD", "supersecret123");
        userEnv.put("DEVOPSHUB_DB_PASSWORD", "root_secret");
        userEnv.put("SSH_AUTH_SOCK", "/private/tmp/com.apple.launchd/Listeners");

        Map<String, String> sanitized = policy.sanitizeEnvironment(userEnv);

        assertTrue(sanitized.containsKey("SAFE_CUSTOM_VAR"), "Safe user variables should be preserved");
        assertEquals("my-safe-value", sanitized.get("SAFE_CUSTOM_VAR"));

        assertFalse(sanitized.containsKey("AWS_SECRET_ACCESS_KEY"), "AWS secrets must be stripped");
        assertFalse(sanitized.containsKey("GITHUB_TOKEN"), "GitHub tokens must be stripped");
        assertFalse(sanitized.containsKey("DATABASE_PASSWORD"), "Database passwords must be stripped");
        assertFalse(sanitized.containsKey("DEVOPSHUB_DB_PASSWORD"), "DevOpsHub DB secrets must be stripped");
        assertFalse(sanitized.containsKey("SSH_AUTH_SOCK"), "SSH authentication socket must be stripped");
    }

    @Test
    @DisplayName("Untrusted repository execution without container sandbox must fail closed")
    void testUntrustedExecutionFailsClosedWithoutContainer() {
        ExecutionPolicy policy = new ExecutionPolicy(ExecutionTrustLevel.UNTRUSTED_EXTERNAL, false, true);

        assertFalse(policy.canExecute(), "Untrusted external execution without container must fail closed");
        assertNotNull(policy.getRejectionReason());
        assertTrue(policy.getRejectionReason().contains("SEC-01"));
    }

    @Test
    @DisplayName("Working directory validation forbids execution in application root or filesystem root")
    void testWorkingDirectoryValidationBlocksForbiddenRoots(@TempDir File tempDir) {
        ExecutionPolicy policy = new ExecutionPolicy(ExecutionTrustLevel.TRUSTED_LOCAL);

        // Forbidden app root
        File appRoot = new File(".");
        assertThrows(SecurityException.class, () -> policy.validateWorkingDirectory(appRoot, tempDir),
                "Executing in DevOpsHub application root repository must be forbidden");

        // Forbidden filesystem root
        File fsRoot = new File("/");
        assertThrows(SecurityException.class, () -> policy.validateWorkingDirectory(fsRoot, tempDir),
                "Executing in filesystem root must be forbidden");

        // Allowed workspace temp directory
        File projectWorkspace = new File(tempDir, "projects/proj-123");
        projectWorkspace.mkdirs();
        assertDoesNotThrow(() -> policy.validateWorkingDirectory(projectWorkspace, tempDir),
                "Executing in designated project workspace directory must be allowed");
    }

    @Test
    @DisplayName("ProcessExecutor enforces execution policy and returns exit code 126 when policy rejects execution")
    void testProcessExecutorRejectionReturnsCode126(@TempDir File tempDir) {
        ExecutionPolicy rejectingPolicy = new ExecutionPolicy(ExecutionTrustLevel.UNTRUSTED_EXTERNAL, false, false);
        ProcessExecutor executor = new ProcessExecutor(tempDir, rejectingPolicy);
        LogCollector logCollector = new LogCollector();

        int exitCode = executor.execute("echo 'untrusted script'", "stage-1", "UntrustedStage", 10, logCollector);

        assertEquals(126, exitCode, "Rejected execution must return POSIX exit code 126");
    }

    @Test
    @DisplayName("Remote repository URLs must resolve to UNTRUSTED_EXTERNAL by default (SEC-POLICY-01)")
    void testRemoteRepositoryResolvesToUntrustedByDefault() {
        com.devopshub.model.Project remoteProject = new com.devopshub.model.Project(
                "proj-remote", "Remote Proj", "Remote", "https://github.com/untrusted/repo.git", "main", "Java", "ACTIVE");

        ExecutionTrustLevel level = com.devopshub.execution.isolation.ExecutionIsolationService.getInstance()
                .resolveTrustLevel(remoteProject, com.devopshub.model.TriggerType.MANUAL);

        assertEquals(ExecutionTrustLevel.UNTRUSTED_EXTERNAL, level,
                "Remote Git repository triggered manually must fail closed to UNTRUSTED_EXTERNAL by default");

        // Null project must also fail closed
        ExecutionTrustLevel nullProjLevel = com.devopshub.execution.isolation.ExecutionIsolationService.getInstance()
                .resolveTrustLevel(null, com.devopshub.model.TriggerType.MANUAL);
        assertEquals(ExecutionTrustLevel.UNTRUSTED_EXTERNAL, nullProjLevel,
                "Null project context must fail closed to UNTRUSTED_EXTERNAL");
    }

    @Test
    @DisplayName("Untrusted working directory outside designated workspace must be rejected")
    void testUntrustedWorkingDirectoryOutsideWorkspaceRejected(@TempDir File tempWorkspace, @TempDir File outsideDir) {
        ExecutionPolicy policy = new ExecutionPolicy(ExecutionTrustLevel.UNTRUSTED_EXTERNAL, false, false);

        assertThrows(SecurityException.class, () -> policy.validateWorkingDirectory(outsideDir, tempWorkspace),
                "Untrusted workload executing outside designated workspace root must throw SecurityException");
    }
}
