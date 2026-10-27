package com.devopshub.analysis;

import com.devopshub.analysis.rules.*;
import com.devopshub.model.ExecutionLog;
import com.devopshub.model.FailureCategory;
import com.devopshub.model.FailureDiagnosis;
import com.devopshub.model.StageExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Intelligent Rule-Based Failure Analysis Engine.
 * Evaluates execution logs, exit codes, and stage metadata to produce
 * evidence-based, actionable root-cause insights.
 */
public class FailureRuleEngine {
    private static final Logger logger = LoggerFactory.getLogger(FailureRuleEngine.class);

    private final List<FailureRule> rules = new ArrayList<>();

    public FailureRuleEngine() {
        // Register core analysis rules in priority order
        rules.add(new SecurityPolicyRejectionRule());
        rules.add(new GitCheckoutFailureRule());
        rules.add(new SecurityGateFailureRule());
        rules.add(new BuildArtifactFailureRule());
        rules.add(new DeploymentFailureRule());
        rules.add(new MissingToolRule());
        rules.add(new MissingDependencyRule());
        rules.add(new CompilationErrorRule());
        rules.add(new AssertionFailureRule());
        rules.add(new DatabaseConnectionRule());
        rules.add(new TimeoutRule());
        rules.add(new PermissionDeniedRule());
    }

    public void registerRule(FailureRule rule) {
        if (rule != null) {
            rules.add(0, rule); // High priority
        }
    }

    public FailureDiagnosis analyze(StageExecution failedStage, List<ExecutionLog> logs) {
        if (failedStage == null) {
            return null;
        }

        logger.info("Executing failure analysis for stage: {}", failedStage.getStageName());

        for (FailureRule rule : rules) {
            if (rule.matches(failedStage, logs)) {
                logger.info("Matched failure rule: {} ({})", rule.getRuleName(), rule.getRuleId());
                return rule.diagnose(failedStage, logs);
            }
        }

        // Truthful Fallback: Generate unclassified diagnosis with recent error logs
        return createFallbackDiagnosis(failedStage, logs);
    }

    public FailureDiagnosis createSecurityRejectionDiagnosis(String executionId, String rejectionReason) {
        FailureDiagnosis d = new FailureDiagnosis();
        d.setId(UUID.randomUUID().toString());
        d.setExecutionId(executionId);
        d.setFailedStage("Pre-Execution Gate");
        d.setCategory(FailureCategory.SECURITY_POLICY_VIOLATION);
        d.setTitle("Execution Blocked by Security Policy (SEC-01)");
        d.setLikelyCause("Untrusted external repository execution requires verified container isolation (Docker), which is unavailable on this host.");
        d.setEvidenceExcerpt(rejectionReason != null ? rejectionReason : "Execution blocked by isolation security policy (SEC-01).");
        d.setSuggestedAction("Start the Docker daemon or configure container runtime settings to enable safe isolated builds for external repositories.");
        d.setRuleId(SecurityPolicyRejectionRule.RULE_ID);
        d.setRuleVersion("1.0");
        d.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        return d;
    }

    private FailureDiagnosis createFallbackDiagnosis(StageExecution failedStage, List<ExecutionLog> logs) {
        List<String> errorLines = new ArrayList<>();
        if (logs != null) {
            for (ExecutionLog log : logs) {
                if (log.getLogLevel() != null && "ERROR".equalsIgnoreCase(log.getLogLevel().name())) {
                    errorLines.add(log.getMessage());
                }
            }
        }

        String evidence = errorLines.isEmpty() ?
                (failedStage.getErrorMessage() != null && !failedStage.getErrorMessage().isEmpty() ?
                        failedStage.getErrorMessage() :
                        "Process exited with code " + failedStage.getExitCode() + ". No standard error output was captured.")
                : String.join("\n", errorLines.subList(Math.max(0, errorLines.size() - 5), errorLines.size()));

        FailureDiagnosis diagnosis = new FailureDiagnosis();
        diagnosis.setId(UUID.randomUUID().toString());
        diagnosis.setExecutionId(failedStage.getExecutionId());
        diagnosis.setStageExecutionId(failedStage.getId());
        diagnosis.setFailedStage(failedStage.getStageName());
        diagnosis.setCategory(FailureCategory.UNKNOWN);
        diagnosis.setTitle("Unclassified Process Exit Failure");
        diagnosis.setLikelyCause("The process terminated with exit code " + failedStage.getExitCode() + ". No specific diagnosis rule matched this failure pattern.");
        diagnosis.setEvidenceExcerpt(evidence);
        diagnosis.setSuggestedAction("No specific diagnosis rule matched this failure. Review the recorded exception and execution logs.");
        diagnosis.setRuleId("RULE_DEFAULT_FALLBACK");
        diagnosis.setRuleVersion("1.0");
        diagnosis.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        return diagnosis;
    }
}
