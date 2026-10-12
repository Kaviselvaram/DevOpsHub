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

public class CompilationErrorRule implements FailureRule {
    private static final String RULE_ID = "RULE_COMPILATION_ERROR_02";
    private static final String RULE_NAME = "Java Compiler Error Analyzer";

    private static final Pattern PATTERN_COMPILATION = Pattern.compile("(cannot find symbol|COMPILATION ERROR|syntax error|';' expected|incompatible types)", Pattern.CASE_INSENSITIVE);

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
            if (PATTERN_COMPILATION.matcher(log.getMessage()).find()) {
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
            if (PATTERN_COMPILATION.matcher(msg).find() || msg.contains(".java:") || msg.contains("location: class")) {
                evidenceLines.add(msg.trim());
            }
        }

        String evidence = evidenceLines.isEmpty() ?
                (stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Source compilation error detected.")
                : String.join("\n", evidenceLines);

        FailureDiagnosis diagnosis = new FailureDiagnosis();
        diagnosis.setId(UUID.randomUUID().toString());
        diagnosis.setExecutionId(stageExecution.getExecutionId());
        diagnosis.setStageExecutionId(stageExecution.getId());
        diagnosis.setFailedStage(stageExecution.getStageName());
        diagnosis.setCategory(FailureCategory.COMPILATION_ERROR);
        diagnosis.setTitle("Java Source Compilation Failure");
        diagnosis.setLikelyCause("Compiler encountered unresolved symbols, missing imports, or syntax errors during build.");
        diagnosis.setEvidenceExcerpt(evidence);
        diagnosis.setSuggestedAction("Check the offending file and line number in the compiler log, verify missing imports or method signatures, and recompile locally with 'javac' or 'mvn compile'.");
        diagnosis.setRuleId(RULE_ID);
        diagnosis.setRuleVersion("1.0");
        diagnosis.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        return diagnosis;
    }
}
