package com.devopshub.security;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a discrete security finding (secret leak, CVE vulnerability, or config flaw).
 */
public class SecurityFinding {
    private String id;
    private String executionId;
    private String stageExecutionId;
    private String ruleId;
    private SecuritySeverity severity;
    private SecurityCategory category;
    private String title;
    private String filePath;
    private Integer lineNumber;
    private String description;
    private String remediation;
    private Timestamp createdAt;

    public SecurityFinding() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public SecurityFinding(String executionId, String stageExecutionId, String ruleId,
                           SecuritySeverity severity, SecurityCategory category, String title,
                           String filePath, Integer lineNumber, String description, String remediation) {
        this();
        this.executionId = executionId;
        this.stageExecutionId = stageExecutionId;
        this.ruleId = ruleId;
        this.severity = severity;
        this.category = category;
        this.title = title;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.description = description;
        this.remediation = remediation;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getStageExecutionId() { return stageExecutionId; }
    public void setStageExecutionId(String stageExecutionId) { this.stageExecutionId = stageExecutionId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public SecuritySeverity getSeverity() { return severity; }
    public void setSeverity(SecuritySeverity severity) { this.severity = severity; }

    public SecurityCategory getCategory() { return category; }
    public void setCategory(SecurityCategory category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRemediation() { return remediation; }
    public void setRemediation(String remediation) { this.remediation = remediation; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s): %s in %s:%s",
                severity, title, ruleId, category, filePath != null ? filePath : "global", lineNumber != null ? lineNumber : 0);
    }
}
