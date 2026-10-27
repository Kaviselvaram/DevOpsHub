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

public class BuildArtifactFailureRule implements FailureRule {
    public static final String RULE_ID = "RULE_ARTIFACT_VALIDATION_01";
    public static final String RULE_NAME = "Build Output & Artifact Verification Analyzer";

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
            if (err.contains("build verification failed") || err.contains("artifact") && err.contains("missing")) {
                return true;
            }
        }
        if (logs != null) {
            for (ExecutionLog l : logs) {
                if (l.getMessage() != null) {
                    String msg = l.getMessage().toLowerCase();
                    if (msg.contains("build verification failed") || (msg.contains("expected") && msg.contains("build output directory") && msg.contains("missing"))) {
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
                if (m != null && m.toLowerCase().contains("verification failed")) {
                    evidenceLines.add(m.trim());
                }
            }
        }
        String evidence = evidenceLines.isEmpty() ?
                (stageExecution != null && stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Build completed with code 0 but required output bundle was not found.")
                : String.join("\n", evidenceLines);

        FailureDiagnosis d = new FailureDiagnosis();
        d.setId(UUID.randomUUID().toString());
        d.setExecutionId(stageExecution != null ? stageExecution.getExecutionId() : null);
        d.setStageExecutionId(stageExecution != null ? stageExecution.getId() : null);
        d.setFailedStage(stageExecution != null ? stageExecution.getStageName() : "Build Stage");
        d.setCategory(FailureCategory.ARTIFACT_VALIDATION_FAILURE);
        d.setTitle("Build Output Artifact Verification Failure");
        d.setLikelyCause("The build command exited with code 0, but the expected production build directory (.next or target/) was not generated or remained empty.");
        d.setEvidenceExcerpt(evidence);
        d.setSuggestedAction("Inspect build scripts and verify that output directory configuration aligns with project conventions.");
        d.setRuleId(RULE_ID);
        d.setRuleVersion("1.0");
        d.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        return d;
    }
}
