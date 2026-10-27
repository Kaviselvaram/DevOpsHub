package com.devopshub.execution.event;

/**
 * Explicit, fine-grained lifecycle event types for pipeline execution observability.
 */
public enum ExecutionEventType {
    EXECUTION_QUEUED,
    EXECUTION_STARTED,
    EXECUTION_BLOCKED,
    WORKSPACE_PREPARATION_STARTED,
    WORKSPACE_PREPARATION_COMPLETED,
    GIT_OPERATION_STARTED,
    GIT_OPERATION_COMPLETED,
    COMMIT_RESOLVED,
    STAGE_QUEUED,
    STAGE_STARTED,
    STAGE_OUTPUT,
    STAGE_PROGRESS_UPDATED,
    STAGE_COMPLETED,
    STAGE_FAILED,
    STAGE_SKIPPED,
    STAGE_CANCELLED,
    STAGE_BLOCKED,
    ARTIFACT_DISCOVERED,
    ARTIFACT_VERIFIED,
    DIAGNOSIS_CREATED,
    EXECUTION_COMPLETED,
    EXECUTION_FAILED,
    EXECUTION_CANCELLED,
    EXECUTION_TIMED_OUT;

    public boolean isTerminal() {
        return this == EXECUTION_COMPLETED || this == EXECUTION_FAILED ||
               this == EXECUTION_CANCELLED || this == EXECUTION_TIMED_OUT ||
               this == EXECUTION_BLOCKED;
    }

    public boolean isError() {
        return this == EXECUTION_FAILED || this == STAGE_FAILED ||
               this == EXECUTION_BLOCKED || this == STAGE_BLOCKED ||
               this == EXECUTION_TIMED_OUT;
    }

    public boolean isWarning() {
        return this == STAGE_SKIPPED || this == STAGE_CANCELLED || this == EXECUTION_CANCELLED;
    }

    public boolean isSuccess() {
        return this == EXECUTION_COMPLETED || this == STAGE_COMPLETED ||
               this == ARTIFACT_VERIFIED || this == COMMIT_RESOLVED ||
               this == WORKSPACE_PREPARATION_COMPLETED;
    }
}
