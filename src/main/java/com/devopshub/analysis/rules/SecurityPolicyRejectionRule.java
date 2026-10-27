package com.devopshub.analysis.rules;

import com.devopshub.analysis.FailureRule;
import com.devopshub.model.ExecutionLog;
import com.devopshub.model.FailureCategory;
import com.devopshub.model.FailureDiagnosis;
import com.devopshub.model.StageExecution;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class SecurityPolicyRejectionRule implements FailureRule {
    public static final String RULE_ID = "RULE_SEC_ISOLATION_01";
    public static final String RULE_NAME = "Security Isolation Policy Rejection Analyzer";

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
            if (err.contains("security policy") || err.contains("isolation") || err.contains("container isolation")) {
                return true;
            }
        }
        if (logs != null) {
            for (ExecutionLog l : logs) {
                if (l.getMessage() != null) {
                    String msg = l.getMessage().toLowerCase();
                    if (msg.contains("blocked by security policy") || msg.contains("container isolation required") || msg.contains("sec-01")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public FailureDiagnosis diagnose(StageExecution stageExecution, List<ExecutionLog> logs) {
        String evidence = "Execution blocked by isolation security policy (SEC-01).";
        if (stageExecution != null && stageExecution.getErrorMessage() != null) {
            evidence = stageExecution.getErrorMessage();
        } else if (logs != null) {
            for (ExecutionLog l : logs) {
                if (l.getMessage() != null && l.getMessage().toLowerCase().contains("security")) {
                    evidence = l.getMessage();
                    break;
                }
            }
        }

        FailureDiagnosis d = new FailureDiagnosis();
        d.setId(UUID.randomUUID().toString());
        d.setExecutionId(stageExecution != null ? stageExecution.getExecutionId() : null);
        d.setStageExecutionId(stageExecution != null ? stageExecution.getId() : null);
        d.setFailedStage(stageExecution != null ? stageExecution.getStageName() : "Pre-Execution Gate");
        d.setCategory(FailureCategory.SECURITY_POLICY_VIOLATION);
        d.setTitle("Execution Blocked by Security Policy (SEC-01)");
        d.setLikelyCause("Untrusted external repository execution requires verified container isolation (Docker), which is currently unavailable or unreachable on this host.");
        d.setEvidenceExcerpt(evidence);
        d.setSuggestedAction("Start the Docker daemon on the host machine or configure container runtime options in Settings / .env to enable safe isolated builds.");
        d.setRuleId(RULE_ID);
        d.setRuleVersion("1.0");
        d.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        return d;
    }
}
