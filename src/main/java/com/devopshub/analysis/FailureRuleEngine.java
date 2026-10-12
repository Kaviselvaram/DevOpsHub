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
 * Evaluates execution logs and metadata to produce actionable root-cause insights.
 */
public class FailureRuleEngine {
    private static final Logger logger = LoggerFactory.getLogger(FailureRuleEngine.class);

    private final List<FailureRule> rules = new ArrayList<>();

    public FailureRuleEngine() {
        // Register core analysis rules in priority order
        rules.add(new AssertionFailureRule());
        rules.add(new CompilationErrorRule());
        rules.add(new MissingDependencyRule());
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

        // Fallback: Generate unclassified diagnosis with recent error logs
        return createFallbackDiagnosis(failedStage, logs);
    }

    private FailureDiagnosis createFallbackDiagnosis(StageExecution failedStage, List<ExecutionLog> logs) {
        List<String> errorLines = new ArrayList<>();
        if (logs != null) {
            for (ExecutionLog log : logs) {
                if (log.getLogLevel() != null && log.getLogLevel().name().equals("ERROR")) {
                    errorLines.add(log.getMessage());
                }
            }
        }

        String evidence = errorLines.isEmpty() ?
                (failedStage.getErrorMessage() != null ? failedStage.getErrorMessage() : "Process exited with code: " + failedStage.getExitCode())
                : String.join("\n", errorLines.subList(Math.max(0, errorLines.size() - 5), errorLines.size()));

        FailureDiagnosis diagnosis = new FailureDiagnosis();
        diagnosis.setId(UUID.randomUUID().toString());
        diagnosis.setExecutionId(failedStage.getExecutionId());
        diagnosis.setStageExecutionId(failedStage.getId());
        diagnosis.setFailedStage(failedStage.getStageName());
        diagnosis.setCategory(FailureCategory.UNKNOWN);
        diagnosis.setTitle("Non-Zero Process Exit Failure");
        diagnosis.setLikelyCause("The stage process terminated with error exit code " + failedStage.getExitCode() + " without matching a specific predefined pattern.");
        diagnosis.setEvidenceExcerpt(evidence);
        diagnosis.setSuggestedAction("Inspect full execution logs in the Log Viewer, examine the raw stderr output, and verify command arguments.");
        diagnosis.setRuleId("RULE_DEFAULT_FALLBACK");
        diagnosis.setRuleVersion("1.0");
        diagnosis.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        return diagnosis;
    }
}
