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

public class DeploymentFailureRule implements FailureRule {
    public static final String RULE_ID = "RULE_DEPLOY_FAILURE_01";
    public static final String RULE_NAME = "Deployment Target & Artifact Staging Analyzer";

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
            if (err.contains("deployment failed") || (err.contains("no built artifact") && err.contains("deploy"))) {
                return true;
            }
        }
        if (logs != null) {
            for (ExecutionLog l : logs) {
                if (l.getMessage() != null) {
                    String msg = l.getMessage().toLowerCase();
                    if (msg.contains("deployment failed:") || msg.contains("no built artifact found to deploy")) {
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
                if (m != null && m.toLowerCase().contains("deployment failed")) {
                    evidenceLines.add(m.trim());
                }
            }
        }
        String evidence = evidenceLines.isEmpty() ?
                (stageExecution != null && stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Deployment operation terminated with error.")
                : String.join("\n", evidenceLines);

        FailureDiagnosis d = new FailureDiagnosis();
        d.setId(UUID.randomUUID().toString());
        d.setExecutionId(stageExecution != null ? stageExecution.getExecutionId() : null);
        d.setStageExecutionId(stageExecution != null ? stageExecution.getId() : null);
        d.setFailedStage(stageExecution != null ? stageExecution.getStageName() : "Deploy Stage");
        d.setCategory(FailureCategory.DEPLOYMENT_FAILURE);
        d.setTitle("Deployment & Staging Execution Failure");
        d.setLikelyCause("Deployment stage could not locate a built distribution package or target staging directory was not writable.");
        d.setEvidenceExcerpt(evidence);
        d.setSuggestedAction("Ensure prior packaging stage generates an artifact (.jar, .war, .zip, or .next build) and deployment target directory exists with proper write permissions.");
        d.setRuleId(RULE_ID);
        d.setRuleVersion("1.0");
        d.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        return d;
    }
}
