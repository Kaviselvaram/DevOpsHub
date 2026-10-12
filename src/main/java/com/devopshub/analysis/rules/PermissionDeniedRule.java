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

public class PermissionDeniedRule implements FailureRule {
    private static final String RULE_ID = "RULE_PERMISSION_06";
    private static final String RULE_NAME = "Filesystem & Command Permission Analyzer";

    private static final Pattern PATTERN_PERM = Pattern.compile("(Permission denied|Access is denied|EACCES|Operation not permitted)", Pattern.CASE_INSENSITIVE);

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
        if (logs == null) return false;
        for (ExecutionLog log : logs) {
            if (PATTERN_PERM.matcher(log.getMessage()).find()) {
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
            if (PATTERN_PERM.matcher(msg).find()) {
                evidenceLines.add(msg.trim());
            }
        }

        String evidence = evidenceLines.isEmpty() ?
                (stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Permission denied error observed.")
                : String.join("\n", evidenceLines);

        FailureDiagnosis diagnosis = new FailureDiagnosis();
        diagnosis.setId(UUID.randomUUID().toString());
        diagnosis.setExecutionId(stageExecution.getExecutionId());
        diagnosis.setStageExecutionId(stageExecution.getId());
        diagnosis.setFailedStage(stageExecution.getStageName());
        diagnosis.setCategory(FailureCategory.PERMISSION_ERROR);
        diagnosis.setTitle("Command or Filesystem Permission Denied");
        diagnosis.setLikelyCause("The process lacked execute permissions on scripts or write permissions on output directories.");
        diagnosis.setEvidenceExcerpt(evidence);
        diagnosis.setSuggestedAction("Ensure execution permissions are granted ('chmod +x <script>') and check filesystem user ownership on workspace target directories.");
        diagnosis.setRuleId(RULE_ID);
        diagnosis.setRuleVersion("1.0");
        diagnosis.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        return diagnosis;
    }
}
