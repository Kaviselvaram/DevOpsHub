package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.config.DatabaseManager;
import com.devopshub.deployment.DeploymentService;
import com.devopshub.git.GitService;
import com.devopshub.git.GitService.GitExecutionResult;
import com.devopshub.model.*;
import com.devopshub.repository.*;
import com.devopshub.service.ProjectImportService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Real Integration & Lifecycle Verification Test for L/Air:
 * Target: https://github.com/Kaviselvaram/lair.git
 * Next.js 16, React 19, TypeScript, Tailwind v4.
 *
 * Verifies:
 * 1. Real Git checkout & commit identification
 * 2. Authentic Next.js project & package manager detection
 * 3. Real dependency installation via npm ci
 * 4. Real lint (npm run lint) and build (npm run build) execution
 * 5. Authentic build artifact verification (.next/BUILD_ID)
 * 6. Full persistence and execution history verification across restart
 * 7. Controlled non-zero exit code failure (node -e "process.exit(23)") with downstream skip
 * 8. Controlled process cancellation with process tree termination
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealLairIntegrationTest {

    private static DatabaseManager dbManager;
    private ProjectRepository projectRepository;
    private PipelineRepository pipelineRepository;
    private ExecutionRepository executionRepository;
    private LogRepository logRepository;
    private DiagnosisRepository diagnosisRepository;
    private GitService gitService;
    private ExecutionEngine engine;
    private ProjectImportService importService;

    @TempDir
    static Path sharedWorkspaceDir;

    @BeforeAll
    static void initDatabase() {
        dbManager = DatabaseManager.getInstance();
        com.devopshub.config.AppConfig.getInstance().set("ALLOW_REMOTE_REPOS_AS_TRUSTED", "true");
    }

    @AfterAll
    static void tearDownAll() {
        com.devopshub.config.AppConfig.getInstance().set("ALLOW_REMOTE_REPOS_AS_TRUSTED", "false");
    }

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.getInstance();
        projectRepository = new ProjectRepository(dbManager);
        pipelineRepository = new PipelineRepository(dbManager);
        executionRepository = new ExecutionRepository(dbManager);
        logRepository = new LogRepository(dbManager);
        diagnosisRepository = new DiagnosisRepository(dbManager);
        gitService = new GitService("git", sharedWorkspaceDir.toFile());
        engine = new ExecutionEngine(executionRepository, logRepository, diagnosisRepository,
                new FailureRuleEngine(), ExecutionThreadPool.getInstance(), projectRepository, gitService, new DeploymentService());
        importService = new ProjectImportService();
    }

    @Test
    @Order(1)
    @DisplayName("Step 1 & 2: Real Git Clone of L/Air, Commit Resolution & Project Detection")
    void testRealCheckoutAndDetection() {
        String repoUrl = "https://github.com/Kaviselvaram/lair.git";
        File targetDir = sharedWorkspaceDir.resolve("lair-detection-test").toFile();

        // Step 1: Real checkout
        GitExecutionResult cloneResult = gitService.cloneOrFetch(repoUrl, "main", targetDir);
        assertEquals(0, cloneResult.getExitCode(), "Git clone of L/Air must succeed with exit 0: " + cloneResult.getStderr());
        assertTrue(new File(targetDir, "package.json").exists(), "package.json must exist in cloned repo");
        assertTrue(new File(targetDir, ".git").exists(), ".git directory must exist");

        // Verify valid working tree and real commit hash
        String commitHash = gitService.getCurrentCommitHash(targetDir);
        assertNotNull(commitHash);
        assertNotEquals("UNKNOWN", commitHash);
        assertEquals(40, commitHash.length(), "Commit hash must be 40 characters");
        assertEquals("d2b7ca779a76f036d7a89f565a99c727db6c591a", commitHash, "Commit must match real remote HEAD");

        // Step 2: Real project detection
        ProjectImportService.DetectedProject detected = importService.inspectDirectory(targetDir);
        assertNotNull(detected);
        assertTrue(detected.isNextJs(), "Target must be detected as Next.js");
        assertTrue(detected.getProjectType().contains("Next.js"), "Project type must state Next.js");
        assertEquals("npm", detected.getBuildSystem(), "Package manager must be detected as npm");
        assertEquals("npm ci", detected.getLockfileCommand(), "Lockfile package-lock.json must yield npm ci");
        assertTrue(detected.hasScript("build"), "Must detect build script in package.json");
        assertTrue(detected.hasScript("lint"), "Must detect lint script in package.json");
        assertFalse(detected.hasScript("test"), "Must accurately detect that L/Air has NO test script in package.json");
    }

    @Test
    @Order(2)
    @DisplayName("Step 3-6: Real End-to-End Pipeline Execution on L/Air, Artifact Verification & DB Restart")
    void testRealLairPipelineExecutionAndPersistenceAcrossRestart() throws Exception {
        String repoUrl = "https://github.com/Kaviselvaram/lair.git";
        String projId = "proj-lair-" + UUID.randomUUID().toString().substring(0, 8);
        String projName = "L/Air Target " + UUID.randomUUID().toString().substring(0, 8);

        // Create Project in DevOpsHub
        Project project = new Project(projId, projName,
                "Real external Next.js repository", repoUrl, "main", "Next.js (NodeJS)", "ACTIVE");
        projectRepository.save(project);

        // Pre-clone or verify workspace directory
        File projectWorkspace = gitService.getProjectWorkspace(projId);
        File cachedDir = sharedWorkspaceDir.resolve("lair-detection-test").toFile();
        GitExecutionResult cloneResult;
        if (new File(cachedDir, ".git").exists()) {
            cloneResult = gitService.cloneOrFetch(cachedDir.getAbsolutePath(), "main", projectWorkspace);
        } else {
            cloneResult = gitService.cloneOrFetch(repoUrl, "main", projectWorkspace);
        }
        assertEquals(0, cloneResult.getExitCode(), "Pre-clone workspace checkout must succeed: " + cloneResult.getStderr());

        // Perform authentic detection on checked-out source
        ProjectImportService.DetectedProject detected = importService.inspectDirectory(projectWorkspace);

        // Generate authentic pipeline
        String pipeId = "pipe-lair-" + UUID.randomUUID().toString().substring(0, 8);
        Pipeline pipeline = new Pipeline(pipeId, projId, "L/Air Production CI", "Authentic lint and build pipeline", "SEQUENTIAL", true);
        List<Stage> stages = importService.generateAuthenticStages(detected, pipeId);
        pipeline.setStages(stages);
        pipelineRepository.save(pipeline);

        // Execute pipeline via DevOpsHub ExecutionEngine
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> finalExecutionRef = new AtomicReference<>();

        Execution execution = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                finalExecutionRef.set(updated);
                latch.countDown();
            }
        }, null);

        assertNotNull(execution);
        assertNotNull(execution.getId());

        // Wait for real build execution (Next.js build usually completes in 30-60s)
        boolean completed = latch.await(6, TimeUnit.MINUTES);
        assertTrue(completed, "Real L/Air pipeline execution timed out after 6 minutes");

        Execution finalExec = finalExecutionRef.get();
        assertNotNull(finalExec);
        assertEquals(ExecutionStatus.SUCCESS, finalExec.getStatus(), "Pipeline execution must complete with SUCCESS");
        assertNotNull(finalExec.getCommitHash(), "Verified commit hash must be recorded on execution");
        assertEquals("d2b7ca779a76f036d7a89f565a99c727db6c591a", finalExec.getCommitHash());

        // Verify stage executions
        List<StageExecution> stageExecs = executionRepository.findStageExecutionsByExecutionId(finalExec.getId());
        assertFalse(stageExecs.isEmpty());
        for (StageExecution se : stageExecs) {
            assertEquals(StageExecutionStatus.SUCCESS, se.getStatus(), "Stage '" + se.getStageName() + "' must be SUCCESS");
            assertEquals(0, se.getExitCode(), "Stage '" + se.getStageName() + "' must have exit code 0");
            assertNotNull(se.getCommand(), "Stage command must be persisted");
        }

        // Verify build artifact
        assertNotNull(finalExec.getArtifactPath(), "Build artifact path must be discovered and recorded");
        File artifactFile = new File(finalExec.getArtifactPath());
        assertTrue(artifactFile.exists(), "Artifact path must physically exist on filesystem: " + artifactFile.getAbsolutePath());
        assertTrue(new File(projectWorkspace, ".next/BUILD_ID").exists(), ".next/BUILD_ID must be produced by Next.js build");

        // Verify logs were captured
        List<ExecutionLog> logs = logRepository.findByExecutionId(finalExec.getId());
        assertFalse(logs.isEmpty(), "Real stdout/stderr logs must be captured and persisted");
        boolean hasCompileSuccessLog = logs.stream().anyMatch(l -> l.getMessage().contains("Compiled successfully") || l.getMessage().contains("✓") || l.getMessage().contains("Generating static pages"));
        assertTrue(hasCompileSuccessLog, "Logs must contain Next.js real compilation output");

        // STEP 6: SIMULATE APPLICATION RESTART & VERIFY PERSISTENCE
        String savedExecId = finalExec.getId();
        Thread.sleep(500);
        DatabaseManager.resetInstanceForTesting();
        DatabaseManager reloadedDb = DatabaseManager.getInstance();
        ExecutionRepository reloadedExecRepo = new ExecutionRepository(reloadedDb);
        LogRepository reloadedLogRepo = new LogRepository(reloadedDb);

        Execution reloadedExec = reloadedExecRepo.findById(savedExecId)
                .orElseThrow(() -> new AssertionError("Execution record missing after persistence restart"));

        assertEquals(ExecutionStatus.SUCCESS, reloadedExec.getStatus());
        assertEquals("d2b7ca779a76f036d7a89f565a99c727db6c591a", reloadedExec.getCommitHash());
        assertEquals(finalExec.getArtifactPath(), reloadedExec.getArtifactPath());
        assertTrue(new File(reloadedExec.getArtifactPath()).exists());

        List<StageExecution> reloadedStages = reloadedExecRepo.findStageExecutionsByExecutionId(savedExecId);
        assertEquals(stageExecs.size(), reloadedStages.size());
        for (StageExecution rse : reloadedStages) {
            assertEquals(StageExecutionStatus.SUCCESS, rse.getStatus());
            assertEquals(0, rse.getExitCode());
            assertNotNull(rse.getCommand());
        }

        List<ExecutionLog> reloadedLogs = reloadedLogRepo.findByExecutionId(savedExecId);
        assertEquals(logs.size(), reloadedLogs.size(), "All log entries must survive persistence restart");
    }

    @Test
    @Order(3)
    @DisplayName("Phase 6 Mandatory: Controlled Non-Zero Exit Code (node -e 'process.exit(23)') and Downstream Skipping")
    void testControlledFailureExitCode23AndDownstreamSkipping() throws Exception {
        String projId = "proj-fail-" + UUID.randomUUID().toString().substring(0, 8);
        Project project = new Project(projId, "Failure Target " + UUID.randomUUID().toString().substring(0, 8),
                "Controlled failure test", sharedWorkspaceDir.toFile().getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-fail-23-" + UUID.randomUUID().toString().substring(0, 8);
        Pipeline pipeline = new Pipeline(pipeId, projId, "Mandatory Failure Verification", "Exit code 23 test", "SEQUENTIAL", true);

        // Stage 1: node -e "process.exit(23)"
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Controlled Node Failure",
                StageType.TEST, 1, "node -e \"process.exit(23)\"", 30, true, null);

        // Stage 2: Dependent downstream stage
        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Downstream Skipped Stage",
                StageType.DEPLOY, 2, "echo 'MUST NOT RUN'", 30, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        pipeline.setStages(List.of(s1, s2));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> execRef = new AtomicReference<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                execRef.set(updated);
                latch.countDown();
            }
        }, null);

        boolean done = latch.await(15, TimeUnit.SECONDS);
        assertTrue(done, "Controlled failure execution must terminate promptly");

        Execution finished = execRef.get();
        assertNotNull(finished);
        assertEquals(ExecutionStatus.FAILED, finished.getStatus(), "Pipeline must be marked FAILED");

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(finished.getId());
        assertEquals(2, stages.size());

        StageExecution stage1 = stages.get(0);
        assertEquals(StageExecutionStatus.FAILED, stage1.getStatus());
        assertEquals(23, stage1.getExitCode(), "Actual exit code 23 must be captured from subprocess");
        assertNotNull(stage1.getCommand());
        assertTrue(stage1.getCommand().contains("process.exit(23)"));

        StageExecution stage2 = stages.get(1);
        assertEquals(StageExecutionStatus.SKIPPED, stage2.getStatus(), "Downstream stage must be SKIPPED");

        // Verify failure preserved across restart
        Thread.sleep(500);
        String execId = finished.getId();
        DatabaseManager.resetInstanceForTesting();
        ExecutionRepository restartedRepo = new ExecutionRepository(DatabaseManager.getInstance());

        Execution restartedExec = restartedRepo.findById(execId).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, restartedExec.getStatus());

        List<StageExecution> restartedStages = restartedRepo.findStageExecutionsByExecutionId(execId);
        assertEquals(StageExecutionStatus.FAILED, restartedStages.get(0).getStatus());
        assertEquals(23, restartedStages.get(0).getExitCode());
        assertEquals(StageExecutionStatus.SKIPPED, restartedStages.get(1).getStatus());
    }

    @Test
    @Order(4)
    @DisplayName("Phase 6 Mandatory: Controlled Long-Running Process Cancellation and Process Termination")
    void testControlledProcessCancellation() throws Exception {
        String projId = "proj-cancel-" + UUID.randomUUID().toString().substring(0, 8);
        Project project = new Project(projId, "Cancel Target " + UUID.randomUUID().toString().substring(0, 8),
                "Controlled cancellation test", sharedWorkspaceDir.toFile().getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-cancel-" + UUID.randomUUID().toString().substring(0, 8);
        Pipeline pipeline = new Pipeline(pipeId, projId, "Cancellation Verification", "Sleep cancellation test", "SEQUENTIAL", true);

        // Stage: Long-running node process
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Long-Running Node Sleep",
                StageType.BUILD, 1, "node -e \"setTimeout(() => {}, 30000)\"", 60, true, null);

        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> execRef = new AtomicReference<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                execRef.set(updated);
                latch.countDown();
            }
        }, null);

        // Allow subprocess to start
        Thread.sleep(600);

        // Cancel execution
        boolean cancelledAcknowledged = engine.cancelExecution(ex.getId());
        assertTrue(cancelledAcknowledged, "Engine must acknowledge active cancellation");

        boolean done = latch.await(10, TimeUnit.SECONDS);
        assertTrue(done, "Cancelled process must terminate promptly");

        Execution cancelled = execRef.get();
        assertNotNull(cancelled);
        assertEquals(ExecutionStatus.CANCELLED, cancelled.getStatus(), "Status must be CANCELLED");

        // Verify across DB restart
        Thread.sleep(500);
        String execId = cancelled.getId();
        DatabaseManager.resetInstanceForTesting();
        ExecutionRepository restartedRepo = new ExecutionRepository(DatabaseManager.getInstance());

        Execution reloaded = restartedRepo.findById(execId).orElseThrow();
        assertEquals(ExecutionStatus.CANCELLED, reloaded.getStatus(), "CANCELLED status must survive persistence restart");
    }
}
