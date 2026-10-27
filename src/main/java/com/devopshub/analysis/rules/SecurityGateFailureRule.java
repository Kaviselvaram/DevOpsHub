package com.devopshub.analysis.rules;

import com.devopshub.analysis.FailureRule;
import com.devopshub.model.ExecutionLog;
import com.devopshub.model.FailureCategory;
import com.devopshub.model.FailureDiagnosis;
import com.devopshub.model.StageExecution;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SecurityGateFailureRule implements FailureRule {
    public static final String RULE_ID = "RULE_SEC_GATE_01";
    public static final String RULE_NAME = "Security Gate & Vulnerability Analyzer";

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
        if (stageExecution != null && stageExecution.getErrorMessage() != null) {
            String err = stageExecution.getErrorMessage().toLowerCase();
            if (err.contains("security gate failure") || err.contains("critical security findings")) {
                return true;
            }
        }
        if (logs != null) {
            for (ExecutionLog l : logs) {
                if (l.getMessage() != null) {
                    String msg = l.getMessage().toLowerCase();
                    if (msg.contains("security gate failure") || msg.contains("critical security findings detected")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public FailureDiagnosis diagnose(StageExecution stageExecution, List<ExecutionLog> logs) {
        List<String> evidenceLines = new ArrayList<>();
        if (logs != null) {
            for (ExecutionLog l : logs) {
                String m = l.getMessage();
                if (m != null && (m.contains("[CRITICAL]") || m.contains("Security gate failure"))) {
                    evidenceLines.add(m.trim());
                }
            }
        }
        String evidence = evidenceLines.isEmpty() ?
                (stageExecution != null && stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Critical security vulnerabilities detected by security scan.")
                : String.join("\n", evidenceLines);

        FailureDiagnosis d = new FailureDiagnosis();
        d.setId(UUID.randomUUID().toString());
        d.setExecutionId(stageExecution != null ? stageExecution.getExecutionId() : null);
        d.setStageExecutionId(stageExecution != null ? stageExecution.getId() : null);
        d.setFailedStage(stageExecution != null ? stageExecution.getStageName() : "Security Scan");
        d.setCategory(FailureCategory.SECURITY_SCANNER_FAILURE);
        d.setTitle("Security Gate Threshold Policy Failure");
        d.setLikelyCause("Static analysis identified critical security findings (e.g. unencrypted secrets, SQL injection patterns, or high-risk vulnerabilities) that breach quality gates.");
        d.setEvidenceExcerpt(evidence);
        d.setSuggestedAction("Remediate critical security vulnerabilities flagged in the Security Scan stage, remove leaked credentials, and review security findings tab.");
        d.setRuleId(RULE_ID);
        d.setRuleVersion("1.0");
        d.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        return d;
    }
}
