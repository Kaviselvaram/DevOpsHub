package com.devopshub.model;

import java.sql.Timestamp;
import java.util.UUID;

public class FailureDiagnosis {
    private String id;
    private String executionId;
    private String stageExecutionId;
    private String failedStage;
    private FailureCategory category;
    private String title;
    private String likelyCause;
    private String evidenceExcerpt;
    private String suggestedAction;
    private String ruleId;
    private String ruleVersion;
    private Timestamp analyzedAt;

    public FailureDiagnosis() {
        this.id = UUID.randomUUID().toString();
        this.category = FailureCategory.UNKNOWN;
        this.ruleVersion = "1.0";
        this.analyzedAt = new Timestamp(System.currentTimeMillis());
    }

    public FailureDiagnosis(String id, String executionId, String stageExecutionId, String failedStage,
                            FailureCategory category, String title, String likelyCause,
                            String evidenceExcerpt, String suggestedAction, String ruleId, String ruleVersion) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.executionId = executionId;
        this.stageExecutionId = stageExecutionId;
        this.failedStage = failedStage;
        this.category = category != null ? category : FailureCategory.UNKNOWN;
        this.title = title;
        this.likelyCause = likelyCause;
        this.evidenceExcerpt = evidenceExcerpt;
        this.suggestedAction = suggestedAction;
        this.ruleId = ruleId;
        this.ruleVersion = ruleVersion != null ? ruleVersion : "1.0";
        this.analyzedAt = new Timestamp(System.currentTimeMillis());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getStageExecutionId() { return stageExecutionId; }
    public void setStageExecutionId(String stageExecutionId) { this.stageExecutionId = stageExecutionId; }

    public String getFailedStage() { return failedStage; }
    public void setFailedStage(String failedStage) { this.failedStage = failedStage; }

    public FailureCategory getCategory() { return category; }
    public void setCategory(FailureCategory category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLikelyCause() { return likelyCause; }
    public void setLikelyCause(String likelyCause) { this.likelyCause = likelyCause; }

    public String getEvidenceExcerpt() { return evidenceExcerpt; }
    public void setEvidenceExcerpt(String evidenceExcerpt) { this.evidenceExcerpt = evidenceExcerpt; }

    public String getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }

    public Timestamp getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Timestamp analyzedAt) { this.analyzedAt = analyzedAt; }
}
