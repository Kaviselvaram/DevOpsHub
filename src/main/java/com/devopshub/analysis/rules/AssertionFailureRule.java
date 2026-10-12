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

public class AssertionFailureRule implements FailureRule {
    private static final String RULE_ID = "RULE_ASSERTION_FAILURE_01";
    private static final String RULE_NAME = "Test Assertion & Expectation Failure Analyzer";

    private static final Pattern PATTERN_HTTP_MISMATCH = Pattern.compile("expected.*HTTP.*401.*received.*HTTP.*500", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_ASSERTION = Pattern.compile("(AssertionError|expected:.*but was:|AssertionFailedError|FAILED.*Test|expected.*HTTP.*401)", Pattern.CASE_INSENSITIVE);

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
        if (stageExecution.getErrorMessage() != null && PATTERN_ASSERTION.matcher(stageExecution.getErrorMessage()).find()) {
            return true;
        }
        if (logs == null || logs.isEmpty()) return false;

        StringBuilder combined = new StringBuilder();
        for (ExecutionLog log : logs) {
            String msg = log.getMessage();
            combined.append(msg).append("\n");
            if (PATTERN_ASSERTION.matcher(msg).find()) {
                return true;
            }
        }

        String fullText = combined.toString();
        if (PATTERN_HTTP_MISMATCH.matcher(fullText).find()) {
            return true;
        }
        if (fullText.contains("expected HTTP 401") && (fullText.contains("received HTTP 500") || fullText.contains("LoginService"))) {
            return true;
        }

        return false;
    }

    @Override
    public FailureDiagnosis diagnose(StageExecution stageExecution, List<ExecutionLog> logs) {
        List<String> evidenceLines = new ArrayList<>();
        boolean isHttpMismatch = false;
        String serviceHint = "Service / Controller";

        if (logs != null) {
            for (ExecutionLog log : logs) {
                String msg = log.getMessage();
                if (msg.contains("HTTP 401") || msg.contains("HTTP 500") || msg.toLowerCase().contains("assertion") || msg.toLowerCase().contains("exception at")) {
                    evidenceLines.add(msg.trim());
                    if (msg.contains("HTTP 401") || msg.contains("HTTP 500")) {
                        isHttpMismatch = true;
                    }
                    if (msg.contains("LoginService")) {
                        serviceHint = "LoginService";
                    }
                }
            }
        }

        String evidence = evidenceLines.isEmpty() ?
                (stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Assertion error observed in test log stream.")
                : String.join("\n", evidenceLines);

        String title;
        String likelyCause;
        String suggestedAction;

        if (isHttpMismatch) {
            title = "HTTP Status Code Mismatch in Authentication Test";
            likelyCause = "Likely exception-handling issue in " + serviceHint + ". The test expected an unauthorized response (HTTP 401) but an unhandled exception triggered an internal server error (HTTP 500).";
            suggestedAction = "Inspect " + serviceHint + " error handling before rerun. Verify that invalid or unauthorized credentials return HTTP 401 rather than bubbling up unhandled exceptions.";
        } else {
            title = "Unit Test Assertion Failure";
            likelyCause = "Test assertion failed due to unexpected return value or state in " + stageExecution.getStageName() + ".";
            suggestedAction = "Review the failing test specification, inspect the actual output versus expected assertion condition, and verify mock stubs.";
        }

        FailureDiagnosis diagnosis = new FailureDiagnosis();
        diagnosis.setId(UUID.randomUUID().toString());
        diagnosis.setExecutionId(stageExecution.getExecutionId());
        diagnosis.setStageExecutionId(stageExecution.getId());
        diagnosis.setFailedStage(stageExecution.getStageName());
        diagnosis.setCategory(FailureCategory.ASSERTION_FAILURE);
        diagnosis.setTitle(title);
        diagnosis.setLikelyCause(likelyCause);
        diagnosis.setEvidenceExcerpt(evidence);
        diagnosis.setSuggestedAction(suggestedAction);
        diagnosis.setRuleId(RULE_ID);
        diagnosis.setRuleVersion("1.0");
        diagnosis.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        return diagnosis;
    }
}
