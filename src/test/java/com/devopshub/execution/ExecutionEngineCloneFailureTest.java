package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.git.GitService;
import com.devopshub.model.*;
import com.devopshub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ARCH-01: Clone Failure Aborts Pipeline Regression Test")
class ExecutionEngineCloneFailureTest {

    private ExecutionEngine engine;
    private PipelineRepository pipelineRepository;
    private ExecutionRepository executionRepository;
    private LogRepository logRepository;
    private DiagnosisRepository diagnosisRepository;
    private ProjectRepository projectRepository;

    private String projectId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        pipelineRepository = new PipelineRepository();
        executionRepository = new ExecutionRepository();
        logRepository = new LogRepository();
        diagnosisRepository = new DiagnosisRepository();
        projectRepository = new ProjectRepository();

        // Project configured with an invalid, unreachable Git URL to force clone failure
        projectId = "proj-broken-git-" + UUID.randomUUID();
        Project brokenRepoProject = new Project(
                projectId,
                "Broken Git Project " + UUID.randomUUID(),
                "Project with invalid Git repository URL",
                "file:///nonexistent/unreachable/nonexistent.git",
                "main",
                "Custom",
                "ACTIVE"
        );
        projectRepository.save(brokenRepoProject);

        // GitService configured with temp directory workspace root
        GitService gitService = new GitService("git", tempDir);

        engine = new ExecutionEngine(
                executionRepository,
                logRepository,
                diagnosisRepository,
                new FailureRuleEngine(),
                ExecutionThreadPool.getInstance(),
                projectRepository,
                gitService
        );
    }

    @Test
    @DisplayName("When Git clone fails during workspace initialization, the pipeline must abort and skip all stages")
    void testCloneFailureAbortsPipelineAndSkipsStages() throws InterruptedException {
        String pipeId = "pipe-clone-fail-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, projectId, "Clone Failure Pipeline", "Desc", "SEQUENTIAL", true);

        // Stage that should NEVER run if clone failed
        Stage s1 = new Stage("s1-" + UUID.randomUUID(), pipeId, "Compile",
                StageType.BUILD, 1, "echo 'SHOULD_NEVER_RUN'", 30, true, null);
        Stage s2 = new Stage("s2-" + UUID.randomUUID(), pipeId, "Deploy",
                StageType.DEPLOY, 2, "echo 'SHOULD_NEVER_RUN_EITHER'", 30, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        pipeline.setStages(List.of(s1, s2));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                completed.set(true);
                latch.countDown();
            }
        }, null);

        assertNotNull(ex);

        boolean finishedInTime = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finishedInTime, "Execution should terminate promptly upon clone failure");
        assertTrue(completed.get());

        // Verify execution is marked FAILED
        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, finalExec.getStatus(), "Pipeline execution must be FAILED on clone error");

        // Verify all stage executions were marked SKIPPED and never executed
        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(2, stages.size());
        for (StageExecution se : stages) {
            assertEquals(StageExecutionStatus.SKIPPED, se.getStatus(),
                    "Downstream stage must be SKIPPED when repository clone fails");
        }
    }
}
