package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.model.*;
import com.devopshub.repository.DiagnosisRepository;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.LogRepository;
import com.devopshub.repository.PipelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionEngineTest {

    private ExecutionEngine engine;
    private PipelineRepository pipelineRepository;
    private ExecutionRepository executionRepository;
    private LogRepository logRepository;
    private DiagnosisRepository diagnosisRepository;

    @BeforeEach
    void setUp() {
        pipelineRepository = new PipelineRepository();
        executionRepository = new ExecutionRepository();
        logRepository = new LogRepository();
        diagnosisRepository = new DiagnosisRepository();
        engine = new ExecutionEngine(executionRepository, logRepository, diagnosisRepository,
                new FailureRuleEngine(), ExecutionThreadPool.getInstance());
    }

    @Test
    @DisplayName("Sequential execution of stages completes successfully")
    void testSequentialSuccessExecution() throws InterruptedException {
        String pipeId = "pipe-success-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-001", "Test Success Pipeline", "Desc", "SEQUENTIAL", true);

        Stage s1 = new Stage("s1-" + UUID.randomUUID(), pipeId, "Checkout",
                StageType.CHECKOUT, 1, "echo 'Checkout ref main'", 30, true, null);
        Stage s2 = new Stage("s2-" + UUID.randomUUID(), pipeId, "Build",
                StageType.BUILD, 2, "echo 'Compiling sources'", 30, true, null);
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
        assertEquals(ExecutionStatus.RUNNING, ex.getStatus());

        boolean finishedInTime = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finishedInTime, "Execution should finish within 10 seconds");
        assertTrue(completed.get());

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, finalExec.getStatus());
        assertTrue(finalExec.getDurationSeconds() >= 0);

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(2, stages.size());
        assertEquals(StageExecutionStatus.SUCCESS, stages.get(0).getStatus());
        assertEquals(StageExecutionStatus.SUCCESS, stages.get(1).getStatus());
    }

    @Test
    @DisplayName("Failing stage skips subsequent dependent stages and triggers rule diagnosis")
    void testFailingStagePropagationAndDiagnosis() throws InterruptedException {
        String pipeId = "pipe-fail-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-003", "Failing Pipeline", "Desc", "SEQUENTIAL", true);

        Stage s1 = new Stage("s1-" + UUID.randomUUID(), pipeId, "Compile",
                StageType.BUILD, 1, "echo 'Build successful'", 30, true, null);

        Stage s2 = new Stage("s2-" + UUID.randomUUID(), pipeId, "Test Authentication",
                StageType.TEST, 2, "echo '[10:42:09.021] expected HTTP 401\n[10:42:09.023] received HTTP 500\nexception at LoginService' && exit 1", 30, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        Stage s3 = new Stage("s3-" + UUID.randomUUID(), pipeId, "Package",
                StageType.PACKAGE, 3, "echo 'Packaging'", 30, true, null);
        s3.getDependsOnStageIds().add(s2.getId());

        pipeline.setStages(List.of(s1, s2, s3));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finished);

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, finalExec.getStatus());

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(3, stages.size());
        assertEquals(StageExecutionStatus.SUCCESS, stages.get(0).getStatus());
        assertEquals(StageExecutionStatus.FAILED, stages.get(1).getStatus());
        assertEquals(StageExecutionStatus.SKIPPED, stages.get(2).getStatus());

        // Check diagnosis was produced
        assertTrue(diagnosisRepository.findByExecutionId(ex.getId()).isPresent());
        FailureDiagnosis diagnosis = diagnosisRepository.findByExecutionId(ex.getId()).get();
        assertEquals(FailureCategory.ASSERTION_FAILURE, diagnosis.getCategory());
        assertTrue(diagnosis.getSuggestedAction().contains("LoginService"));
    }

    @Test
    @DisplayName("Parallel fork group executes concurrent stages")
    void testParallelForkExecution() throws InterruptedException {
        String pipeId = "pipe-parallel-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, "proj-004", "Parallel Test Pipeline", "Desc", "PARALLEL_FORK", true);

        Stage s1 = new Stage("s1-" + UUID.randomUUID(), pipeId, "Build",
                StageType.BUILD, 1, "echo 'Built'", 30, true, null);

        Stage s2 = new Stage("s2-" + UUID.randomUUID(), pipeId, "Unit Tests",
                StageType.TEST, 2, "echo 'Tests passing'", 30, true, "FORK_TEST");
        s2.getDependsOnStageIds().add(s1.getId());

        Stage s3 = new Stage("s3-" + UUID.randomUUID(), pipeId, "Security Scan",
                StageType.SECURITY, 3, "echo 'Security SAST passing'", 30, true, "FORK_TEST");
        s3.getDependsOnStageIds().add(s1.getId());

        Stage s4 = new Stage("s4-" + UUID.randomUUID(), pipeId, "Deploy",
                StageType.DEPLOY, 4, "echo 'Deployed'", 30, true, null);
        s4.getDependsOnStageIds().add(s2.getId());
        s4.getDependsOnStageIds().add(s3.getId());

        pipeline.setStages(List.of(s1, s2, s3, s4));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finished);

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, finalExec.getStatus());

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(4, stages.size());
        for (StageExecution se : stages) {
            assertEquals(StageExecutionStatus.SUCCESS, se.getStatus());
        }
    }
}
