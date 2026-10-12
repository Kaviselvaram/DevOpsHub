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

public class DatabaseConnectionRule implements FailureRule {
    private static final String RULE_ID = "RULE_DATABASE_04";
    private static final String RULE_NAME = "Database Connectivity & Credentials Analyzer";

    private static final Pattern PATTERN_DB = Pattern.compile("(Communications link failure|Access denied for user|Connection refused|Unknown database|SQLNonTransientConnectionException)", Pattern.CASE_INSENSITIVE);

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
            if (PATTERN_DB.matcher(log.getMessage()).find()) {
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
            if (PATTERN_DB.matcher(msg).find()) {
                evidenceLines.add(msg.trim());
            }
        }

        String evidence = evidenceLines.isEmpty() ?
                (stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Database connection failure detected.")
                : String.join("\n", evidenceLines);

        FailureDiagnosis diagnosis = new FailureDiagnosis();
        diagnosis.setId(UUID.randomUUID().toString());
        diagnosis.setExecutionId(stageExecution.getExecutionId());
        diagnosis.setStageExecutionId(stageExecution.getId());
        diagnosis.setFailedStage(stageExecution.getStageName());
        diagnosis.setCategory(FailureCategory.DATABASE_ERROR);
        diagnosis.setTitle("Database Connectivity Failure");
        diagnosis.setLikelyCause("The application stage failed to establish a JDBC connection to the target database daemon or was rejected due to invalid credentials.");
        diagnosis.setEvidenceExcerpt(evidence);
        diagnosis.setSuggestedAction("Verify that MySQL is running on port 3306, inspect DB_USER and DB_PASSWORD credentials in .env, and verify database permissions.");
        diagnosis.setRuleId(RULE_ID);
        diagnosis.setRuleVersion("1.0");
        diagnosis.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        return diagnosis;
    }
}
