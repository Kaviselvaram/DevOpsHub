package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.config.AppConfig;
import com.devopshub.config.DatabaseManager;
import com.devopshub.deployment.DeploymentService;
import com.devopshub.execution.event.ExecutionEvent;
import com.devopshub.execution.event.ExecutionEventBus;
import com.devopshub.execution.event.ExecutionEventType;
import com.devopshub.git.GitService;
import com.devopshub.model.*;
import com.devopshub.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Master Pipeline Observability & Execution Transparency Verification Suite")
public class RealExecutionObservabilityIntegrationTest {

    private static DatabaseManager dbManager;
    private ProjectRepository projectRepository;
    private PipelineRepository pipelineRepository;
    private ExecutionRepository executionRepository;
    private LogRepository logRepository;
    private DiagnosisRepository diagnosisRepository;
    private ExecutionEventRepository eventRepository;
    private ExecutionEventBus eventBus;
    private GitService gitService;
    private ExecutionEngine engine;

    @TempDir
    static Path testWorkspaceDir;

    @BeforeAll
    static void initDatabase() {
        dbManager = DatabaseManager.getInstance();
    }

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.getInstance();
        projectRepository = new ProjectRepository(dbManager);
        pipelineRepository = new PipelineRepository(dbManager);
        executionRepository = new ExecutionRepository(dbManager);
        logRepository = new LogRepository(dbManager);
        diagnosisRepository = new DiagnosisRepository(dbManager);
        eventRepository = new ExecutionEventRepository(dbManager);
        eventBus = ExecutionEventBus.getInstance();
        gitService = new GitService("git", testWorkspaceDir.toFile());
        engine = new ExecutionEngine(executionRepository, logRepository, diagnosisRepository,
                new FailureRuleEngine(), ExecutionThreadPool.getInstance(), projectRepository, gitService, new DeploymentService());
    }

    @Test
    @Order(1)
    @DisplayName("Scenario A: Real Subprocess Execution — Live Output Streaming, Verified Stage Duration & Artifact")
    void testLiveSuccessfulExecutionWithSubprocesses() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-obs-succ-" + uid;
        File localProjectDir = testWorkspaceDir.resolve(projId).toFile();
        localProjectDir.mkdirs();

        // Write a dummy source file
        File srcFile = new File(localProjectDir, "index.js");
        try (FileWriter fw = new FileWriter(srcFile)) {
            fw.write("console.log('App initialization');\n");
        }

        Project project = new Project(projId, "Observability Success Proj " + uid, "Local testing",
                localProjectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-obs-succ-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Observability Success Pipeline " + uid, "Full live lifecycle", "SEQUENTIAL", true);

        // Stage 1: Fast echo stage
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Echo Greeting",
                StageType.BUILD, 1, "node -e \"console.log('Hello from Stage 1'); process.stdout.write('Stage 1 progress\\n')\"", 30, true, null);

        // Stage 2: Measurable time stage that emits multiple lines and creates an artifact in target/
        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Build Artifact Package",
                StageType.PACKAGE, 2,
                "node -e \"" +
                        "console.log('Packaging started...'); " +
                        "const fs = require('fs'); " +
                        "if (!fs.existsSync('target')) fs.mkdirSync('target'); " +
                        "fs.writeFileSync('target/app-release.jar', 'DUMMY_BINARY_DATA_FOR_SHA256'); " +
                        "console.log('Artifact produced successfully in target/app-release.jar');" +
                        "\"", 30, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        pipeline.setStages(List.of(s1, s2));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> finalExecRef = new AtomicReference<>();
        List<ExecutionEvent> liveEvents = new CopyOnWriteArrayList<>();
        List<ExecutionLog> liveLogs = new CopyOnWriteArrayList<>();

        // Capture live events
        eventBus.subscribeGlobal(liveEvents::add);

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                finalExecRef.set(updated);
                latch.countDown();
            }
        }, liveLogs::add);

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "Live execution must complete within 15 seconds");

        Execution result = finalExecRef.get();
        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(), "Pipeline must finish with SUCCESS");
        assertTrue(result.getDurationSeconds() >= 0.05, "Duration must reflect real elapsed time");

        // Verify live events were emitted monotonically
        assertFalse(liveEvents.isEmpty(), "Lifecycle events must be published");
        boolean hasExecStarted = liveEvents.stream().anyMatch(e -> e.getEventType() == ExecutionEventType.EXECUTION_STARTED);
        boolean hasStage1Started = liveEvents.stream().anyMatch(e -> e.getEventType() == ExecutionEventType.STAGE_STARTED && "Echo Greeting".equals(e.getStageName()));
        boolean hasStage1Completed = liveEvents.stream().anyMatch(e -> e.getEventType() == ExecutionEventType.STAGE_COMPLETED && "Echo Greeting".equals(e.getStageName()));
        boolean hasExecCompleted = liveEvents.stream().anyMatch(e -> e.getEventType() == ExecutionEventType.EXECUTION_COMPLETED);

        assertTrue(hasExecStarted, "EXECUTION_STARTED must be published");
        assertTrue(hasStage1Started, "STAGE_STARTED must be published");
        assertTrue(hasStage1Completed, "STAGE_COMPLETED must be published");
        assertTrue(hasExecCompleted, "EXECUTION_COMPLETED must be published");

        // Verify live logs were streamed from real process
        assertFalse(liveLogs.isEmpty(), "Real stdout logs must be captured");
        boolean hasGreetingLog = liveLogs.stream().anyMatch(l -> l.getMessage().contains("Hello from Stage 1"));
        boolean hasPackagingLog = liveLogs.stream().anyMatch(l -> l.getMessage().contains("Packaging started..."));
        assertTrue(hasGreetingLog, "Stage 1 real output must be streamed to log listener");
        assertTrue(hasPackagingLog, "Stage 2 real output must be streamed to log listener");

        // Verify artifact was discovered and SHA-256 computed
        assertNotNull(result.getArtifactPath(), "Built artifact path must be discovered and verified");
        File artifactFile = new File(result.getArtifactPath());
        assertTrue(artifactFile.exists(), "Artifact file must exist on disk");
        ArtifactInfo artifactInfo = engine.inspectBuildArtifact(localProjectDir);
        assertNotNull(artifactInfo);
        assertEquals("app-release.jar", artifactInfo.getName());
        assertEquals("JAR_BINARY", artifactInfo.getType());
        assertNotNull(artifactInfo.getSha256Checksum());
        assertNotEquals("Unknown", artifactInfo.getSha256Checksum());

        // Verify stage records in DB
        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(result.getId());
        assertEquals(2, stages.size());
        assertEquals(StageExecutionStatus.SUCCESS, stages.get(0).getStatus());
        assertEquals(0, stages.get(0).getExitCode());
        assertTrue(stages.get(0).isExitCodeSet());
        assertEquals(StageExecutionStatus.SUCCESS, stages.get(1).getStatus());
        assertEquals(0, stages.get(1).getExitCode());
        assertTrue(stages.get(1).isExitCodeSet());
    }

    @Test
    @Order(2)
    @DisplayName("Scenario B: Real Process Non-Zero Failure (Exit 42), Downstream Skip, and Evidence Diagnosis")
    void testLiveFailureWithSubprocessExitCode42() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-obs-fail-" + uid;
        File localProjectDir = testWorkspaceDir.resolve(projId).toFile();
        localProjectDir.mkdirs();

        Project project = new Project(projId, "Observability Failure Proj " + uid, "Failure testing",
                localProjectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-obs-fail-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Observability Failure Pipeline " + uid, "Controlled failure", "SEQUENTIAL", true);

        // Stage 1: Non-zero exit code 42 with clear stderr error
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Unit Tests Failure",
                StageType.TEST, 1,
                "node -e \"console.error('AssertionError: expected HTTP 401 but received HTTP 500 at LoginService'); process.exit(42)\"",
                30, true, null);

        // Stage 2: Downstream dependent stage that MUST be skipped
        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Downstream Packaging",
                StageType.PACKAGE, 2, "echo 'SHOULD NEVER EXECUTE'", 30, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        pipeline.setStages(List.of(s1, s2));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> finalExecRef = new AtomicReference<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                finalExecRef.set(updated);
                latch.countDown();
            }
        }, null);

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Failure run must terminate within 10 seconds");

        Execution result = finalExecRef.get();
        assertNotNull(result);
        assertEquals(ExecutionStatus.FAILED, result.getStatus(), "Pipeline status must be FAILED");

        // Verify stage records
        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(result.getId());
        assertEquals(2, stages.size());

        StageExecution stage1 = stages.get(0);
        assertEquals(StageExecutionStatus.FAILED, stage1.getStatus());
        assertEquals(42, stage1.getExitCode(), "Actual process exit code 42 must be captured");
        assertTrue(stage1.isExitCodeSet());

        StageExecution stage2 = stages.get(1);
        assertEquals(StageExecutionStatus.SKIPPED, stage2.getStatus(), "Dependent stage must be SKIPPED");
        assertFalse(stage2.isExitCodeSet(), "Skipped stage must NOT have an exit code set (must not display exit 0)");
        assertNotNull(stage2.getErrorMessage(), "Skip reason must be present");
        assertTrue(stage2.getErrorMessage().contains("Skipped"), "Skip reason must state why skipped");

        // Verify evidence-based diagnosis
        Optional<FailureDiagnosis> diagOpt = diagnosisRepository.findByExecutionId(result.getId());
        assertTrue(diagOpt.isPresent(), "Diagnosis must be generated for failed execution");
        FailureDiagnosis diagnosis = diagOpt.get();
        assertEquals(FailureCategory.ASSERTION_FAILURE, diagnosis.getCategory());
        assertNotNull(diagnosis.getEvidenceExcerpt());
        assertTrue(diagnosis.getEvidenceExcerpt().contains("LoginService") || diagnosis.getEvidenceExcerpt().contains("HTTP 401"),
                "Diagnosis must quote the actual stderr evidence line");
        assertNotNull(diagnosis.getSuggestedAction());
        assertTrue(diagnosis.getSuggestedAction().contains("LoginService") || diagnosis.getSuggestedAction().contains("assertion"),
                "Recommendation must be tailored to the detected failure");
    }

    @Test
    @Order(3)
    @DisplayName("Scenario C: Immediate Security Policy Rejection (Untrusted Remote Repo without Container Sandbox)")
    void testImmediateSecurityPolicyRejection() throws Exception {
        AppConfig.getInstance().set("EXECUTION_CONTAINER_ENABLED", "false");
        try {
            String uid = UUID.randomUUID().toString().substring(0, 8);
            String projId = "proj-obs-sec-" + uid;
            Project project = new Project(projId, "Untrusted External Proj " + uid, "Remote untrusted repository",
                    "https://github.com/untrusted-org/untrusted-repo-" + uid + ".git", "main", "Java", "ACTIVE");
            projectRepository.save(project);

            String pipeId = "pipe-obs-sec-" + uid;
            Pipeline pipeline = new Pipeline(pipeId, projId, "Security Policy Rejection Pipeline " + uid, "Fail closed check", "SEQUENTIAL", true);

            Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Checkout Source", StageType.CHECKOUT, 1, "checkout", 30, true, null);
            Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Compile Application", StageType.BUILD, 2, "mvn clean compile", 30, true, null);
            Stage s3 = new Stage(UUID.randomUUID().toString(), pipeId, "Unit Tests", StageType.TEST, 3, "mvn test", 30, true, null);

            pipeline.setStages(List.of(s1, s2, s3));
            pipelineRepository.save(pipeline);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Execution> finalExecRef = new AtomicReference<>();
            List<ExecutionLog> capturedLogs = new CopyOnWriteArrayList<>();

            Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
                if (updated.getStatus().isTerminal()) {
                    finalExecRef.set(updated);
                    latch.countDown();
                }
            }, capturedLogs::add);

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertTrue(completed, "Policy rejection must fail closed promptly");

            Execution result = finalExecRef.get();
            assertNotNull(result);
        assertEquals(ExecutionStatus.BLOCKED, result.getStatus(), "Status must be BLOCKED (not generic FAILED)");
        assertEquals("Blocked by isolation security policy", result.getCurrentPhase());
        assertNotNull(result.getBackendDescription());
        assertTrue(result.getBackendDescription().contains("Blocked"), "Backend description must state blocked");

        // Verify stages are SKIPPED with clear security policy reason and NO fake exit 0
        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(result.getId());
        assertEquals(3, stages.size());
        for (StageExecution se : stages) {
            assertEquals(StageExecutionStatus.SKIPPED, se.getStatus(), "All stages must be SKIPPED");
            assertFalse(se.isExitCodeSet(), "Skipped stage must not have an exit code");
            assertNotNull(se.getErrorMessage());
            assertTrue(se.getErrorMessage().contains("isolation security policy") || se.getErrorMessage().contains("SEC-01"),
                    "Skip reason must state security policy rejection");
        }

        // Verify logs were flushed and captured
        List<ExecutionLog> persistedLogs = logRepository.findByExecutionId(result.getId());
        assertFalse(persistedLogs.isEmpty(), "Security rejection log must be persisted (no empty console defect)");
        boolean hasSecLog = persistedLogs.stream().anyMatch(l -> l.getMessage().contains("SEC-01") || l.getMessage().contains("security policy"));
        assertTrue(hasSecLog, "Persisted logs must contain the explicit security policy rejection message");

        // Verify authentic diagnosis was generated
        Optional<FailureDiagnosis> diagOpt = diagnosisRepository.findByExecutionId(result.getId());
        assertTrue(diagOpt.isPresent(), "Security rejection must produce a diagnosis");
        FailureDiagnosis diag = diagOpt.get();
        assertEquals(FailureCategory.SECURITY_POLICY_VIOLATION, diag.getCategory());
        assertTrue(diag.getTitle().contains("Security Policy"));
        assertTrue(diag.getEvidenceExcerpt().contains("SEC-01") || diag.getEvidenceExcerpt().contains("security policy"));
        assertTrue(diag.getSuggestedAction().contains("Docker") || diag.getSuggestedAction().contains("container"));
        } finally {
            AppConfig.getInstance().set("EXECUTION_CONTAINER_ENABLED", "true");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Scenario D: Empty-Output Process Execution (Truthful Empty State, No Fabricated Lines)")
    void testEmptyOutputProcessExecution() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-obs-empty-" + uid;
        File localProjectDir = testWorkspaceDir.resolve(projId).toFile();
        localProjectDir.mkdirs();

        Project project = new Project(projId, "Empty Output Proj " + uid, "Empty output testing",
                localProjectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-obs-empty-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Empty Output Pipeline " + uid, "Silent process", "SEQUENTIAL", true);

        // Stage: Silent process that exits with 0 without writing to stdout or stderr
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Silent Process",
                StageType.BUILD, 1, "node -e \"/* completely silent process */\"", 30, true, null);

        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> finalExecRef = new AtomicReference<>();
        List<ExecutionLog> logs = new CopyOnWriteArrayList<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                finalExecRef.set(updated);
                latch.countDown();
            }
        }, logs::add);

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed);

        Execution result = finalExecRef.get();
        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());

        // Ensure that no fake lines were invented for the stage output
        long stageLines = logs.stream().filter(l -> "Silent Process".equals(l.getStageName()) && l.getMessage().contains("Silent")).count();
        assertEquals(0, stageLines, "No fabricated console lines should be invented for a silent stage");
    }

    @Test
    @Order(5)
    @DisplayName("Scenario E: High-Volume Output (500+ Lines Handled Bounded and Responsive)")
    void testHighVolumeOutputPerformance() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-obs-vol-" + uid;
        File localProjectDir = testWorkspaceDir.resolve(projId).toFile();
        localProjectDir.mkdirs();

        Project project = new Project(projId, "High Volume Proj " + uid, "Volume testing",
                localProjectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-obs-vol-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "High Volume Pipeline " + uid, "High volume stream", "SEQUENTIAL", true);

        // Stage: Emits 600 lines
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Volume Emitter",
                StageType.BUILD, 1,
                "node -e \"for (let i = 1; i <= 600; i++) console.log('Log message index ' + i + ' with simulated payload');\"",
                30, true, null);

        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> finalExecRef = new AtomicReference<>();
        List<ExecutionLog> logs = new CopyOnWriteArrayList<>();

        long start = System.currentTimeMillis();
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                finalExecRef.set(updated);
                latch.countDown();
            }
        }, logs::add);

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        assertTrue(completed);

        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 10000, "High-volume stream must finish rapidly without hanging");

        Execution result = finalExecRef.get();
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());

        // Verify logs persisted and bounded
        List<ExecutionLog> persisted = logRepository.findByExecutionId(result.getId());
        assertTrue(persisted.size() >= 500, "All high-volume logs must be persisted in batches");
    }

    @Test
    @Order(6)
    @DisplayName("Scenario G: Concurrent Executions (Isolated Timers, Stages, Logs and Events)")
    void testConcurrentExecutionsIsolation() throws Exception {
        String uid1 = UUID.randomUUID().toString().substring(0, 8);
        String projId1 = "proj-conc-1-" + uid1;
        File dir1 = testWorkspaceDir.resolve(projId1).toFile();
        dir1.mkdirs();
        Project p1 = new Project(projId1, "Concurrent Proj 1 " + uid1, "Desc", dir1.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(p1);

        String uid2 = UUID.randomUUID().toString().substring(0, 8);
        String projId2 = "proj-conc-2-" + uid2;
        File dir2 = testWorkspaceDir.resolve(projId2).toFile();
        dir2.mkdirs();
        Project p2 = new Project(projId2, "Concurrent Proj 2 " + uid2, "Desc", dir2.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(p2);

        String pipeId1 = "pipe-conc-1-" + uid1;
        Pipeline pipeline1 = new Pipeline(pipeId1, projId1, "Concurrent Pipe 1 " + uid1, "Desc", "SEQUENTIAL", true);
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId1, "P1 Stage", StageType.BUILD, 1,
                "node -e \"console.log('UNIQUE_TAG_FOR_RUN_1'); setTimeout(() => {}, 200);\"", 30, true, null);
        pipeline1.setStages(List.of(s1));
        pipelineRepository.save(pipeline1);

        String pipeId2 = "pipe-conc-2-" + uid2;
        Pipeline pipeline2 = new Pipeline(pipeId2, projId2, "Concurrent Pipe 2 " + uid2, "Desc", "SEQUENTIAL", true);
        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId2, "P2 Stage", StageType.BUILD, 1,
                "node -e \"console.log('UNIQUE_TAG_FOR_RUN_2'); setTimeout(() => {}, 200);\"", 30, true, null);
        pipeline2.setStages(List.of(s2));
        pipelineRepository.save(pipeline2);

        CountDownLatch latch = new CountDownLatch(2);
        List<ExecutionLog> logs1 = new CopyOnWriteArrayList<>();
        List<ExecutionLog> logs2 = new CopyOnWriteArrayList<>();

        Execution ex1 = engine.startExecution(pipeline1, TriggerType.MANUAL, u -> {
            if (u.getStatus().isTerminal()) latch.countDown();
        }, logs1::add);

        Execution ex2 = engine.startExecution(pipeline2, TriggerType.MANUAL, u -> {
            if (u.getStatus().isTerminal()) latch.countDown();
        }, logs2::add);

        boolean done = latch.await(15, TimeUnit.SECONDS);
        assertTrue(done, "Both concurrent executions must complete");

        // Verify strict isolation: logs1 contains only RUN_1, logs2 contains only RUN_2
        assertTrue(logs1.stream().anyMatch(l -> l.getMessage().contains("UNIQUE_TAG_FOR_RUN_1")));
        assertFalse(logs1.stream().anyMatch(l -> l.getMessage().contains("UNIQUE_TAG_FOR_RUN_2")),
                "Execution 1 must not receive Execution 2 logs");

        assertTrue(logs2.stream().anyMatch(l -> l.getMessage().contains("UNIQUE_TAG_FOR_RUN_2")));
        assertFalse(logs2.stream().anyMatch(l -> l.getMessage().contains("UNIQUE_TAG_FOR_RUN_1")),
                "Execution 2 must not receive Execution 1 logs");
    }

    @Test
    @Order(7)
    @DisplayName("Scenario I: Diagnosis Differentiation & Truthful Fallback for Unclassified Failures")
    void testDiagnosisDifferentiationAndTruthfulFallback() {
        FailureRuleEngine ruleEngine = new FailureRuleEngine();

        // 1. Missing tool
        StageExecution stageMissingTool = new StageExecution(UUID.randomUUID().toString(), "exec-1", "s1", "Build", StageType.BUILD);
        stageMissingTool.setExitCode(127);
        stageMissingTool.setErrorMessage("/bin/sh: line 1: non_existent_tool: command not found");
        FailureDiagnosis diag1 = ruleEngine.analyze(stageMissingTool, Collections.emptyList());
        assertNotNull(diag1);
        assertEquals(FailureCategory.MISSING_BUILD_TOOL, diag1.getCategory());
        assertTrue(diag1.getSuggestedAction().contains("toolchain") || diag1.getSuggestedAction().contains("PATH"));

        // 2. Compilation error
        StageExecution stageCompile = new StageExecution(UUID.randomUUID().toString(), "exec-2", "s2", "Compile", StageType.BUILD);
        stageCompile.setExitCode(1);
        ExecutionLog logCompile = new ExecutionLog();
        logCompile.setLogLevel(LogLevel.ERROR);
        logCompile.setMessage("[ERROR] /src/App.java:[45,12] cannot find symbol: variable logger");
        FailureDiagnosis diag2 = ruleEngine.analyze(stageCompile, List.of(logCompile));
        assertNotNull(diag2);
        assertEquals(FailureCategory.COMPILATION_ERROR, diag2.getCategory());

        // 3. Timeout
        StageExecution stageTimeout = new StageExecution(UUID.randomUUID().toString(), "exec-3", "s3", "Test", StageType.TEST);
        stageTimeout.setExitCode(124);
        stageTimeout.setErrorMessage("Stage execution timed out after 30 seconds.");
        FailureDiagnosis diag3 = ruleEngine.analyze(stageTimeout, Collections.emptyList());
        assertNotNull(diag3);
        assertEquals(FailureCategory.TIMEOUT, diag3.getCategory());

        // 4. Unclassified Failure -> Truthful Fallback
        StageExecution stageUnknown = new StageExecution(UUID.randomUUID().toString(), "exec-4", "s4", "Custom Step", StageType.BUILD);
        stageUnknown.setExitCode(99);
        stageUnknown.setErrorMessage("Process exited with code 99");
        FailureDiagnosis diag4 = ruleEngine.analyze(stageUnknown, Collections.emptyList());
        assertNotNull(diag4);
        assertEquals(FailureCategory.UNKNOWN, diag4.getCategory());
        assertEquals("RULE_DEFAULT_FALLBACK", diag4.getRuleId());
        assertEquals("No specific diagnosis rule matched this failure. Review the recorded exception and execution logs.", diag4.getSuggestedAction());
        assertTrue(diag4.getLikelyCause().contains("99"));
    }

    @Test
    @Order(8)
    @DisplayName("Scenario J: Persistence Integrity Across Application Restart")
    void testPersistenceIntegrityAcrossRestart() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-restart-" + uid;
        File localDir = testWorkspaceDir.resolve(projId).toFile();
        localDir.mkdirs();

        Project project = new Project(projId, "Restart Proj " + uid, "Persistence test", localDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-restart-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Restart Pipeline " + uid, "Persistence test", "SEQUENTIAL", true);
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Restart Stage", StageType.BUILD, 1,
                "node -e \"console.log('RESTART_PERSISTENCE_VERIFIED')\"", 30, true, null);
        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> execRef = new AtomicReference<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, u -> {
            if (u.getStatus().isTerminal()) {
                execRef.set(u);
                latch.countDown();
            }
        }, null);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Execution finished = execRef.get();
        String savedId = finished.getId();

        // Simulate application restart
        DatabaseManager.resetInstanceForTesting();
        DatabaseManager restartedDb = DatabaseManager.getInstance();
        ExecutionRepository reloadedRepo = new ExecutionRepository(restartedDb);
        LogRepository reloadedLogs = new LogRepository(restartedDb);

        Execution reloaded = reloadedRepo.findById(savedId).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, reloaded.getStatus());
        assertFalse(reloaded.getStatus() == ExecutionStatus.RUNNING, "Must not pretend to resume a finished run");

        List<ExecutionLog> logs = reloadedLogs.findByExecutionId(savedId);
        assertFalse(logs.isEmpty());
        assertTrue(logs.stream().anyMatch(l -> l.getMessage().contains("RESTART_PERSISTENCE_VERIFIED")));
    }
}
