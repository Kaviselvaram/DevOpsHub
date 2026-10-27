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

public class GitCheckoutFailureRule implements FailureRule {
    public static final String RULE_ID = "RULE_GIT_CHECKOUT_01";
    public static final String RULE_NAME = "Git Synchronization & Remote Access Analyzer";

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
            if (err.contains("git checkout failed") || err.contains("fatal: repository") || err.contains("fatal: remote")
                    || err.contains("workspace initialization failed") || err.contains("could not resolve host")) {
                return true;
            }
        }
        if (logs != null) {
            for (ExecutionLog l : logs) {
                if (l.getMessage() != null) {
                    String msg = l.getMessage().toLowerCase();
                    if (msg.contains("git checkout failed") || msg.contains("fatal: repository")
                            || msg.contains("could not resolve host") || msg.contains("authentication failed for")
                            || msg.contains("workspace initialization failed")) {
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
        boolean isAuth = false;
        if (logs != null) {
            for (ExecutionLog l : logs) {
                String m = l.getMessage();
                if (m != null && (m.toLowerCase().contains("git") || m.toLowerCase().contains("fatal:") || m.toLowerCase().contains("error:"))) {
                    evidenceLines.add(m.trim());
                    if (m.toLowerCase().contains("authentication failed") || m.toLowerCase().contains("permission denied (publickey)")) {
                        isAuth = true;
                    }
                }
            }
        }
        String evidence = evidenceLines.isEmpty() ?
                (stageExecution != null && stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Git command exited with failure.")
                : String.join("\n", evidenceLines);

        FailureDiagnosis d = new FailureDiagnosis();
        d.setId(UUID.randomUUID().toString());
        d.setExecutionId(stageExecution != null ? stageExecution.getExecutionId() : null);
        d.setStageExecutionId(stageExecution != null ? stageExecution.getId() : null);
        d.setFailedStage(stageExecution != null ? stageExecution.getStageName() : "Checkout Source");
        d.setCategory(isAuth ? FailureCategory.AUTHENTICATION_FAILURE : FailureCategory.REPOSITORY_CHECKOUT_FAILURE);
        d.setTitle(isAuth ? "Git Remote Authentication Failure" : "Git Repository Synchronization Failure");
        d.setLikelyCause(isAuth ?
                "Remote Git provider rejected credentials or public key authentication." :
                "Failed to clone or fetch repository ref from remote origin. Possible causes include invalid repository URL, missing branch, or network connectivity issue.");
        d.setEvidenceExcerpt(evidence);
        d.setSuggestedAction(isAuth ?
                "Inspect repository access tokens / SSH keys in project configuration and verify credentials." :
                "Verify repository URL, check internet connectivity, and ensure the default branch exists on remote.");
        d.setRuleId(RULE_ID);
        d.setRuleVersion("1.0");
        d.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        return d;
    }
}
