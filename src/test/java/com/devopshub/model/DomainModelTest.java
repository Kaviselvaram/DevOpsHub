package com.devopshub.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {

    @Test
    @DisplayName("ExecutionStatus transitions and terminal checks")
    void testExecutionStatus() {
        assertFalse(ExecutionStatus.QUEUED.isTerminal());
        assertFalse(ExecutionStatus.RUNNING.isTerminal());
        assertTrue(ExecutionStatus.SUCCESS.isTerminal());
        assertTrue(ExecutionStatus.FAILED.isTerminal());
        assertTrue(ExecutionStatus.CANCELLED.isTerminal());

        assertEquals(ExecutionStatus.SUCCESS, ExecutionStatus.fromString("success"));
        assertEquals(ExecutionStatus.QUEUED, ExecutionStatus.fromString("invalid_val"));
    }

    @Test
    @DisplayName("StageExecutionStatus terminal checks")
    void testStageExecutionStatus() {
        assertFalse(StageExecutionStatus.QUEUED.isTerminal());
        assertFalse(StageExecutionStatus.RUNNING.isTerminal());
        assertTrue(StageExecutionStatus.SUCCESS.isTerminal());
        assertTrue(StageExecutionStatus.FAILED.isTerminal());
        assertTrue(StageExecutionStatus.SKIPPED.isTerminal());
        assertTrue(StageExecutionStatus.CANCELLED.isTerminal());
    }

    @Test
    @DisplayName("FailureCategory fromString mapping")
    void testFailureCategory() {
        assertEquals(FailureCategory.ASSERTION_FAILURE, FailureCategory.fromString("assertion_failure"));
        assertEquals(FailureCategory.COMPILATION_ERROR, FailureCategory.fromString("COMPILATION_ERROR"));
        assertEquals(FailureCategory.UNKNOWN, FailureCategory.fromString("xyz_unknown"));
        assertNotNull(FailureCategory.ASSERTION_FAILURE.getDisplayName());
    }

    @Test
    @DisplayName("Project model creation and default fields")
    void testProjectDefaults() {
        Project project = new Project();
        assertNotNull(project.getId());
        assertEquals("ACTIVE", project.getStatus());
        assertEquals("main", project.getDefaultBranch());
        assertEquals("Java", project.getProjectType());
        assertNotNull(project.getCreatedAt());
    }

    @Test
    @DisplayName("Stage model dependency management")
    void testStageDependencies() {
        Stage stage = new Stage();
        assertNotNull(stage.getId());
        assertEquals(300, stage.getTimeoutSeconds());
        assertTrue(stage.isEnabled());
        assertNotNull(stage.getDependsOnStageIds());
        stage.getDependsOnStageIds().add("dep-1");
        assertEquals(1, stage.getDependsOnStageIds().size());
    }
}
