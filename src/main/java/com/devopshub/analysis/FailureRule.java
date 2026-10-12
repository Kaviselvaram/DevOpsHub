package com.devopshub.analysis;

import com.devopshub.model.ExecutionLog;
import com.devopshub.model.FailureDiagnosis;
import com.devopshub.model.StageExecution;

import java.util.List;

/**
 * Interface contract for explainable rule-based failure analyzers.
 */
public interface FailureRule {
    String getRuleId();
    String getRuleName();
    boolean matches(StageExecution stageExecution, List<ExecutionLog> logs);
    FailureDiagnosis diagnose(StageExecution stageExecution, List<ExecutionLog> logs);
}
