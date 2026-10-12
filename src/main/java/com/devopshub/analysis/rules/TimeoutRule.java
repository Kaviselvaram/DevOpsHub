package com.devopshub.analysis.rules;

import com.devopshub.analysis.FailureRule;
import com.devopshub.model.FailureCategory;
import com.devopshub.model.FailureDiagnosis;
import com.devopshub.model.ExecutionLog;
import com.devopshub.model.StageExecution;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class TimeoutRule implements FailureRule {
    private static final String RULE_ID = "RULE_TIMEOUT_05";
    private static final String RULE_NAME = "Execution Timeout Analyzer";

    private static final Pattern PATTERN_TIMEOUT = Pattern.compile("(timed out|ExecutionTimeoutException|operation timed out)", Pattern.CASE_INSENSITIVE);

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public String getRuleName() {
        return RULE_NAME;
    }

    @Override
    public boolean matches(StageExecution stageExecution, List<ExecutionLog> logs) {
        if (stageExecution.getExitCode() == 124 || stageExecution.getExitCode() == 137) {
            return true;
        }
        if (logs == null) return false;
        for (ExecutionLog log : logs) {
            if (PATTERN_TIMEOUT.matcher(log.getMessage()).find()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FailureDiagnosis diagnose(StageExecution stageExecution, List<ExecutionLog> logs) {
        List<String> evidenceLines = new ArrayList<>();
        for (ExecutionLog log : logs) {
            String msg = log.getMessage();
            if (PATTERN_TIMEOUT.matcher(msg).find()) {
                evidenceLines.add(msg.trim());
            }
        }

        String evidence = evidenceLines.isEmpty() ?
                "Stage execution exceeded timeout limit (exit code: " + stageExecution.getExitCode() + ")"
                : String.join("\n", evidenceLines);

        FailureDiagnosis diagnosis = new FailureDiagnosis();
        diagnosis.setId(UUID.randomUUID().toString());
        diagnosis.setExecutionId(stageExecution.getExecutionId());
        diagnosis.setStageExecutionId(stageExecution.getId());
        diagnosis.setFailedStage(stageExecution.getStageName());
        diagnosis.setCategory(FailureCategory.TIMEOUT);
        diagnosis.setTitle("Stage Execution Exceeded Timeout");
        diagnosis.setLikelyCause("The process hung or took longer than the configured timeout threshold without producing completion signals.");
        diagnosis.setEvidenceExcerpt(evidence);
        diagnosis.setSuggestedAction("Increase timeout limit in stage configuration if legitimate, or inspect tests/scripts for infinite loops and deadlocks.");
        diagnosis.setRuleId(RULE_ID);
        diagnosis.setRuleVersion("1.0");
        diagnosis.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        return diagnosis;
    }
}
