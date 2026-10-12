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

public class MissingDependencyRule implements FailureRule {
    private static final String RULE_ID = "RULE_DEPENDENCY_03";
    private static final String RULE_NAME = "Dependency Resolution & Classpath Analyzer";

    private static final Pattern PATTERN_DEP = Pattern.compile("(Could not resolve dependencies|ClassNotFoundException|NoClassDefFoundError|ArtifactNotFoundException|package.*does not exist)", Pattern.CASE_INSENSITIVE);

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
            if (PATTERN_DEP.matcher(log.getMessage()).find()) {
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
            if (PATTERN_DEP.matcher(msg).find()) {
                evidenceLines.add(msg.trim());
            }
        }

        String evidence = evidenceLines.isEmpty() ?
                (stageExecution.getErrorMessage() != null ? stageExecution.getErrorMessage() : "Missing dependency detected.")
                : String.join("\n", evidenceLines);

        FailureDiagnosis diagnosis = new FailureDiagnosis();
        diagnosis.setId(UUID.randomUUID().toString());
        diagnosis.setExecutionId(stageExecution.getExecutionId());
        diagnosis.setStageExecutionId(stageExecution.getId());
        diagnosis.setFailedStage(stageExecution.getStageName());
        diagnosis.setCategory(FailureCategory.MISSING_DEPENDENCY);
        diagnosis.setTitle("Missing Dependency or Unresolved Artifact");
        diagnosis.setLikelyCause("The build system was unable to resolve required library artifacts or runtime classpath packages.");
        diagnosis.setEvidenceExcerpt(evidence);
        diagnosis.setSuggestedAction("Verify pom.xml coordinates (groupId, artifactId, version), ensure repository mirrors/proxies are reachable, and run 'mvn dependency:resolve'.");
        diagnosis.setRuleId(RULE_ID);
        diagnosis.setRuleVersion("1.0");
        diagnosis.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        return diagnosis;
    }
}
