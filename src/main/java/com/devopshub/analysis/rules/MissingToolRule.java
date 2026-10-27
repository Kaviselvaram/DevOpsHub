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
import java.util.regex.Pattern;

public class MissingToolRule implements FailureRule {
    public static final String RULE_ID = "RULE_MISSING_TOOL_01";
    public static final String RULE_NAME = "Missing Executable / Build Toolchain Analyzer";

    private static final Pattern PATTERN_NOT_FOUND = Pattern.compile(
            "(command not found|: not found|is not recognized as an internal or external command|No such file or directory)",
            Pattern.CASE_INSENSITIVE
    );

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
        if (stageExecution != null && (stageExecution.getExitCode() == 127 || stageExecution.getExitCode() == 126)) {
            return true;
        }
        if (stageExecution != null && stageExecution.getErrorMessage() != null && PATTERN_NOT_FOUND.matcher(stageExecution.getErrorMessage()).find()) {
            return true;
        }
        if (logs != null) {
            for (ExecutionLog l : logs) {
                if (l.getMessage() != null && PATTERN_NOT_FOUND.matcher(l.getMessage()).find()) {
                    return true;
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
                if (m != null && PATTERN_NOT_FOUND.matcher(m).find()) {
                    evidenceLines.add(m.trim());
                }
            }
        }
        String evidence = evidenceLines.isEmpty() ?
                (stageExecution != null && stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Subprocess exited with code 127 (command not found).")
                : String.join("\n", evidenceLines);

        String cmd = stageExecution != null ? stageExecution.getCommand() : "unknown command";

        FailureDiagnosis d = new FailureDiagnosis();
        d.setId(UUID.randomUUID().toString());
        d.setExecutionId(stageExecution != null ? stageExecution.getExecutionId() : null);
        d.setStageExecutionId(stageExecution != null ? stageExecution.getId() : null);
        d.setFailedStage(stageExecution != null ? stageExecution.getStageName() : "Stage Execution");
        d.setCategory(FailureCategory.MISSING_BUILD_TOOL);
        d.setTitle("Required Build Tool or Executable Not Found");
        d.setLikelyCause("The stage attempted to execute command '" + cmd + "', but the executable is not installed or not present on the environment PATH.");
        d.setEvidenceExcerpt(evidence);
        d.setSuggestedAction("Verify that the required toolchain (e.g., node, npm, maven, git) is installed and accessible on PATH in the execution environment.");
        d.setRuleId(RULE_ID);
        d.setRuleVersion("1.0");
        d.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        return d;
    }
}
