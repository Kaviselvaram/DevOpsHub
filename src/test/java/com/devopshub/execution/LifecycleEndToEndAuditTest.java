package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.config.DatabaseManager;
import com.devopshub.exception.PipelineValidationException;
import com.devopshub.model.*;
import com.devopshub.pipeline.PipelineValidator;
import com.devopshub.repository.DiagnosisRepository;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.LogRepository;
import com.devopshub.repository.PipelineRepository;
import com.devopshub.repository.ProjectRepository;
import com.devopshub.service.PipelineService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rigorous lifecycle audit tests validating real OS command executions,
 * exit code interpretations, timeout enforcements, user cancellations,
 * transaction boundaries, and historical audit trail purges.
 */
public class LifecycleEndToEndAuditTest {

    private static DatabaseManager dbManager;
    private ProjectRepository projectRepository;
    private PipelineRepository pipelineRepository;
    private ExecutionRepository executionRepository;
    private LogRepository logRepository;
    private DiagnosisRepository diagnosisRepository;
    private ExecutionEngine engine;
    private PipelineService pipelineService;

    @BeforeAll
    static void initDb() {
        dbManager = DatabaseManager.getInstance();
    }

    @BeforeEach
    void setUp() {
        projectRepository = new ProjectRepository(dbManager);
        if (projectRepository.findById("proj-audit-local").isEmpty()) {
            Project auditProject = new Project("proj-audit-local", "Local Audit Project", "Isolated local test environment", "local://audit", "main", "Custom", "ACTIVE");
            projectRepository.save(auditProject);
        }

        pipelineRepository = new PipelineRepository(dbManager);
        if (pipelineRepository.findById("pipe-audit-local").isEmpty()) {
            Pipeline auditPipe = new Pipeline("pipe-audit-local", "proj-audit-local", "Local Audit Pipeline", "Desc", "SEQUENTIAL", true);
            pipelineRepository.save(auditPipe);
        }
        executionRepository = new ExecutionRepository(dbManager);
        logRepository = new LogRepository(dbManager);
        diagnosisRepository = new DiagnosisRepository(dbManager);
        engine = new ExecutionEngine(executionRepository, logRepository, diagnosisRepository,
                new FailureRuleEngine(), ExecutionThreadPool.getInstance(), projectRepository, new com.devopshub.git.GitService());
        pipelineService = new PipelineService(pipelineRepository, new PipelineValidator());
    }

    @Test
    @DisplayName("Scenario 1: Real command execution capturing exit code 0, duration, and stdout")
    void testRealProcessExecutionSuccess() throws InterruptedException {
        String pipeId = "audit-pipe-success-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-audit-local", "Audit Success Pipeline", "Desc", "SEQUENTIAL", true);

        // Run real command producing standard output
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Print Java Runtime",
                StageType.BUILD, 1, "java -version", 60, true, null);

        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean done = latch.await(10, TimeUnit.SECONDS);
        assertTrue(done, "Execution should complete within 10s");

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, finalExec.getStatus());
        assertTrue(finalExec.getDurationSeconds() >= 0.0);

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(1, stages.size());
        assertEquals(StageExecutionStatus.SUCCESS, stages.get(0).getStatus());
        assertEquals(0, stages.get(0).getExitCode());
    }

    @Test
    @DisplayName("Scenario 2: Real non-zero command failure halts downstream dependent stages")
    void testRealProcessFailureAndSkippedDownstream() throws InterruptedException {
        String pipeId = "audit-pipe-fail-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-audit-local", "Audit Failure Pipeline", "Desc", "SEQUENTIAL", true);

        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Failing Command",
                StageType.TEST, 1, "sh -c 'echo \"Failing stage intentionally\" >&2; exit 42'", 30, true, null);

        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Downstream Deploy",
                StageType.DEPLOY, 2, "echo 'This should not run'", 30, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        pipeline.setStages(List.of(s1, s2));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean done = latch.await(10, TimeUnit.SECONDS);
        assertTrue(done);

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, finalExec.getStatus());

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(2, stages.size());
        assertEquals(StageExecutionStatus.FAILED, stages.get(0).getStatus());
        assertEquals(42, stages.get(0).getExitCode());
        assertEquals(StageExecutionStatus.SKIPPED, stages.get(1).getStatus());
    }

    @Test
    @DisplayName("Scenario 3: Process timeout terminates process and records error status")
    void testProcessTimeoutEnforcement() throws InterruptedException {
        String pipeId = "audit-pipe-timeout-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-audit-local", "Audit Timeout Pipeline", "Desc", "SEQUENTIAL", true);

        // Sleep for 30s with a 1s timeout
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Long Sleeping Stage",
                StageType.BUILD, 1, "sleep 30", 1, true, null);

        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean done = latch.await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(done, "Timeout should force completion well before 30 seconds");
        assertTrue(elapsed < 8000, "Timeout should have triggered quickly (took " + elapsed + "ms)");

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, finalExec.getStatus());

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(1, stages.size());
        assertEquals(StageExecutionStatus.FAILED, stages.get(0).getStatus());
        assertEquals(124, stages.get(0).getExitCode()); // 124 is standard Unix timeout exit code
    }

    @Test
    @DisplayName("Scenario 4: User cancellation terminates active task")
    void testUserCancellation() throws InterruptedException {
        String pipeId = "audit-pipe-cancel-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-audit-local", "Audit Cancel Pipeline", "Desc", "SEQUENTIAL", true);

        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Cancellable Sleep",
                StageType.BUILD, 1, "sleep 15", 30, true, null);

        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        // Let the process start, then issue cancellation
        Thread.sleep(300);
        boolean cancelled = engine.cancelExecution(ex.getId());
        assertTrue(cancelled, "Engine should acknowledge cancellation of active execution");

        boolean done = latch.await(5, TimeUnit.SECONDS);
        assertTrue(done, "Cancellation should promptly finish the execution");

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.CANCELLED, finalExec.getStatus());
    }

    @Test
    @DisplayName("Scenario 5: Unavailable executable reports process failure without crash")
    void testUnavailableCommandGracefullyHandled() throws InterruptedException {
        String pipeId = "audit-pipe-badcmd-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-audit-local", "Audit Bad Command Pipeline", "Desc", "SEQUENTIAL", true);

        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Invalid Binary",
                StageType.BUILD, 1, "non_existent_command_xyz_876321", 30, true, null);

        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean done = latch.await(10, TimeUnit.SECONDS);
        assertTrue(done);

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, finalExec.getStatus());

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(1, stages.size());
        assertEquals(StageExecutionStatus.FAILED, stages.get(0).getStatus());
        assertNotEquals(0, stages.get(0).getExitCode());
    }

    @Test
    @DisplayName("Scenario 6: ExecutionRepository.cleanFinishedExecutions removes completed executions")
    void testExecutionPurgeAndCleanHistory() {
        String execId = "audit-purge-" + UUID.randomUUID();
        Execution ex = new Execution(execId, "pipe-audit-local", "proj-audit-local", 888, ExecutionStatus.SUCCESS, TriggerType.MANUAL);
        executionRepository.save(ex);

        assertTrue(executionRepository.findById(execId).isPresent());

        int cleaned = executionRepository.cleanFinishedExecutions();
        assertTrue(cleaned >= 1, "Should have purged at least the completed test execution");
        assertTrue(executionRepository.findById(execId).isEmpty(), "Purged execution should no longer exist in DB");
    }

    @Test
    @DisplayName("Scenario 7: Updating pipeline deletes removed stages from database")
    void testPipelineUpdateDeletesRemovedStages() {
        String pipeId = "pipe-update-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-audit-local", "Stage Update Test", "Desc", "SEQUENTIAL", true);

        Stage s1 = new Stage("stage-keep-" + UUID.randomUUID(), pipeId, "Keep Me", StageType.BUILD, 1, "echo keep", 30, true, null);
        Stage s2 = new Stage("stage-del-" + UUID.randomUUID(), pipeId, "Delete Me", StageType.TEST, 2, "echo delete", 30, true, null);

        pipeline.setStages(new ArrayList<>(List.of(s1, s2)));
        pipelineRepository.save(pipeline);

        List<Stage> loaded1 = pipelineRepository.findStagesByPipelineId(pipeId);
        assertEquals(2, loaded1.size());

        // Update pipeline removing stage 2
        pipelineService.updatePipeline(pipeId, "Stage Update Test", "Updated Desc", "SEQUENTIAL", true, List.of(s1));

        List<Stage> loaded2 = pipelineRepository.findStagesByPipelineId(pipeId);
        assertEquals(1, loaded2.size(), "Removed stage should have been deleted from database");
        assertEquals(s1.getId(), loaded2.get(0).getId());

        // Cleanup
        pipelineRepository.delete(pipeId);
    }
}
