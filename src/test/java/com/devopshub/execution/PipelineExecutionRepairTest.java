package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.config.AppConfig;
import com.devopshub.config.DatabaseManager;
import com.devopshub.deployment.DeploymentService;
import com.devopshub.execution.backend.DockerExecutionBackend;
import com.devopshub.execution.backend.ExecutionBackend;
import com.devopshub.execution.backend.ExecutionBackendFactory;
import com.devopshub.execution.backend.HostExecutionBackend;
import com.devopshub.execution.event.ExecutionEvent;
import com.devopshub.execution.event.ExecutionEventBus;
import com.devopshub.execution.event.ExecutionEventType;
import com.devopshub.execution.isolation.ExecutionIsolationService;
import com.devopshub.execution.isolation.ExecutionPolicy;
import com.devopshub.execution.isolation.ExecutionTrustLevel;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Pipeline Execution Runtime & Isolation Verification Suite.
 * Covers Scenarios A through J:
 * - Test A: Backend Availability Check (Docker detection, fallback, config override)
 * - Test B: Successful Isolated Container Execution (node:20-alpine / alpine:latest)
 * - Test C: Failure Propagation in Container Sandbox (exit code 42, skip downstream)
 * - Test D: Untrusted Execution Fail-Closed when Container Unavailable
 * - Test E: Explicitly Trusted Local Project Execution via HostExecutionBackend
 * - Test F: Execution History, Log & Artifact Persistence across DB Restart
 * - Test G: Workspace Directory Confinement & Path Consistency
 * - Test H: Process Cancellation & Container Termination via docker kill
 * - Test I: No Fake Success (absent build output causes stage failure)
 * - Test J: Real Target Execution on L/Air Pipeline (Checkout, npm ci, lint, build, verify)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Pipeline Execution Runtime & Isolation Verification Suite (Tests A - J)")
public class PipelineExecutionRepairTest {

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
    @DisplayName("Test A — Backend Availability: Accurately Probes Docker Runtime and Configuration")
    void testBackendAvailabilityProbing() {
        ExecutionBackendFactory factory = ExecutionBackendFactory.getInstance();
        DockerExecutionBackend dockerBackend = factory.getDockerBackend();

        boolean dockerResponding = DockerExecutionBackend.checkDockerAvailability();
        assertEquals(dockerResponding, dockerBackend.isAvailable(),
                "DockerExecutionBackend availability must match real Docker CLI probe");

        if (dockerResponding) {
            assertTrue(dockerBackend.getAvailabilityDetails().contains("operational") ||
                       dockerBackend.getAvailabilityDetails().contains("Server Version"),
                    "Availability details must disclose operational status and server version");
        }

        // Test configuration disabling container execution
        AppConfig.getInstance().set("EXECUTION_CONTAINER_ENABLED", "false");
        assertFalse(factory.isContainerAvailable(), "Container availability must be false when explicitly disabled in config");
        AppConfig.getInstance().set("EXECUTION_CONTAINER_ENABLED", "true");
        assertEquals(dockerResponding, factory.isContainerAvailable(), "Container availability must restore when re-enabled");
    }

    @Test
    @Order(2)
    @DisplayName("Test B — Successful Isolated Container Execution: Real Container Sandboxing")
    void testSuccessfulIsolatedContainerExecution() throws Exception {
        Assumptions.assumeTrue(ExecutionBackendFactory.getInstance().isContainerAvailable(),
                "Docker container runtime must be available for isolated container test");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-docker-succ-" + uid;
        File projectDir = testWorkspaceDir.resolve(projId).toFile();
        projectDir.mkdirs();

        // Write a test package.json and script
        File pkgJson = new File(projectDir, "package.json");
        try (FileWriter fw = new FileWriter(pkgJson)) {
            fw.write("{\"name\": \"docker-test-app\", \"version\": \"1.0.0\"}\n");
        }

        // Remote untrusted trigger forces UNTRUSTED_EXTERNAL
        Project project = new Project(projId, "Docker Isolated Proj " + uid, "Testing container isolation",
                projectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-docker-succ-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Docker Isolated Pipeline " + uid, "Isolated workflow", "SEQUENTIAL", true);

        // Stage 1: Echo and container inspection inside node:20-alpine
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Container Sandboxed Echo",
                StageType.BUILD, 1,
                "node -e \"console.log('CONTAINER_USER_ID: ' + process.getuid()); console.log('CONTAINER_EXECUTION_VERIFIED')\"",
                60, true, null);

        // Stage 2: Package artifact creation in target/
        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Container Artifact Creation",
                StageType.PACKAGE, 2,
                "node -e \"const fs = require('fs'); if (!fs.existsSync('target')) fs.mkdirSync('target'); fs.writeFileSync('target/app.jar', 'DUMMY_ARTIFACT_DATA'); console.log('Artifact created.');\"",
                60, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        pipeline.setStages(List.of(s1, s2));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> execRef = new AtomicReference<>();
        List<ExecutionLog> logs = new CopyOnWriteArrayList<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.WEBHOOK, updated -> {
            if (updated.getStatus().isTerminal()) {
                execRef.set(updated);
                latch.countDown();
            }
        }, logs::add);

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertTrue(completed, "Docker container pipeline execution must complete within 60 seconds");

        Execution result = execRef.get();
        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(), "Container execution must succeed");

        // Verify container logs were captured
        boolean hasContainerBanner = logs.stream().anyMatch(l -> l.getMessage().contains("[CONTAINER EXECUTION]"));
        boolean hasIsolationBanner = logs.stream().anyMatch(l -> l.getMessage().contains("[ISOLATION] Caps: ALL dropped"));
        boolean hasVerifiedOutput = logs.stream().anyMatch(l -> l.getMessage().contains("CONTAINER_EXECUTION_VERIFIED"));

        assertTrue(hasContainerBanner, "Log stream must record container execution metadata");
        assertTrue(hasIsolationBanner, "Log stream must record security isolation flags");
        assertTrue(hasVerifiedOutput, "Process output from inside container must be streamed to logs");

        // Verify stage records
        List<StageExecution> stageExecs = executionRepository.findStageExecutionsByExecutionId(result.getId());
        assertEquals(2, stageExecs.size());
        for (StageExecution se : stageExecs) {
            assertEquals(StageExecutionStatus.SUCCESS, se.getStatus());
            assertEquals(0, se.getExitCode());
            assertTrue(se.isExitCodeSet());
        }

        // Verify artifact was discovered on host filesystem
        assertNotNull(result.getArtifactPath());
        File artifactFile = new File(result.getArtifactPath());
        assertTrue(artifactFile.exists(), "Artifact created inside container must exist on mounted host workspace");
    }

    @Test
    @Order(3)
    @DisplayName("Test C — Failure Propagation in Container: Real Non-Zero Exit Code and Downstream Skip")
    void testFailurePropagationInContainer() throws Exception {
        Assumptions.assumeTrue(ExecutionBackendFactory.getInstance().isContainerAvailable(),
                "Docker container runtime must be available for container failure test");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-docker-fail-" + uid;
        File projectDir = testWorkspaceDir.resolve(projId).toFile();
        projectDir.mkdirs();

        Project project = new Project(projId, "Docker Fail Proj " + uid, "Testing failure propagation",
                projectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-docker-fail-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Docker Fail Pipeline " + uid, "Failing workflow", "SEQUENTIAL", true);

        // Stage 1: Explicitly exits with code 42
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Failing Stage",
                StageType.TEST, 1,
                "node -e \"console.error('FatalException: Database connection refused on port 5432'); process.exit(42)\"",
                60, true, null);

        // Stage 2: Dependent downstream stage
        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Downstream Skipped Stage",
                StageType.PACKAGE, 2, "echo 'NEVER RUN'", 60, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        pipeline.setStages(List.of(s1, s2));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> execRef = new AtomicReference<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.WEBHOOK, updated -> {
            if (updated.getStatus().isTerminal()) {
                execRef.set(updated);
                latch.countDown();
            }
        }, null);

        boolean completed = latch.await(45, TimeUnit.SECONDS);
        assertTrue(completed, "Failing container execution must terminate promptly");

        Execution result = execRef.get();
        assertNotNull(result);
        assertEquals(ExecutionStatus.FAILED, result.getStatus(), "Pipeline must be marked FAILED");

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(result.getId());
        assertEquals(2, stages.size());

        StageExecution stage1 = stages.get(0);
        assertEquals(StageExecutionStatus.FAILED, stage1.getStatus());
        assertEquals(42, stage1.getExitCode(), "Actual container process exit code 42 must be captured");
        assertTrue(stage1.isExitCodeSet());

        StageExecution stage2 = stages.get(1);
        assertEquals(StageExecutionStatus.SKIPPED, stage2.getStatus(), "Downstream stage must be SKIPPED");
        assertFalse(stage2.isExitCodeSet(), "Skipped stage must NOT have exit code set");
        assertNotNull(stage2.getErrorMessage());
        assertTrue(stage2.getErrorMessage().contains("Skipped"), "Skip reason must be recorded");

        // Verify diagnosis
        Optional<FailureDiagnosis> diagOpt = diagnosisRepository.findByExecutionId(result.getId());
        assertTrue(diagOpt.isPresent(), "Failure diagnosis must be generated for container failure");
        assertEquals("Failing Stage", diagOpt.get().getFailedStage());
    }

    @Test
    @Order(4)
    @DisplayName("Test D — Untrusted Execution Fails Closed when Container Backend is Unavailable")
    void testUntrustedExecutionFailsClosedWhenContainerUnavailable() throws Exception {
        // Temporarily disable container availability in config to test fail-closed policy
        AppConfig.getInstance().set("EXECUTION_CONTAINER_ENABLED", "false");
        try {
            String uid = UUID.randomUUID().toString().substring(0, 8);
            String projId = "proj-untrusted-failclose-" + uid;
            Project project = new Project(projId, "Untrusted Proj " + uid, "Testing fail-closed policy",
                    "https://github.com/untrusted-author/untrusted-repo-" + uid + ".git", "main", "NodeJS", "ACTIVE");
            projectRepository.save(project);

            String pipeId = "pipe-untrusted-failclose-" + uid;
            Pipeline pipeline = new Pipeline(pipeId, projId, "Untrusted Pipeline " + uid, "Should be blocked", "SEQUENTIAL", true);

            Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Malicious Stage",
                    StageType.BUILD, 1, "echo 'ATTEMPTING HOST COMPROMISE'", 30, true, null);
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

            boolean finished = latch.await(10, TimeUnit.SECONDS);
            assertTrue(finished, "Blocked execution must terminate immediately");

            Execution result = execRef.get();
            assertNotNull(result);
            assertEquals(ExecutionStatus.BLOCKED, result.getStatus(), "Execution must be marked BLOCKED, not FAILED");
            assertTrue(result.getDurationSeconds() < 2.0, "Blocked execution must abort in under 2 seconds");

            // Stages must be SKIPPED with clear skip reasons and exitCodeSet=false
            List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(result.getId());
            assertEquals(1, stages.size());
            StageExecution se = stages.get(0);
            assertEquals(StageExecutionStatus.SKIPPED, se.getStatus());
            assertFalse(se.isExitCodeSet(), "Blocked stage must NOT have exitCodeSet=true");
            assertTrue(se.getErrorMessage().contains("blocked by isolation security policy") ||
                       se.getErrorMessage().contains("SEC-01"));

            // Verify diagnosis explicitly states SEC-01 isolation block
            Optional<FailureDiagnosis> diagOpt = diagnosisRepository.findByExecutionId(result.getId());
            assertTrue(diagOpt.isPresent());
            assertEquals(FailureCategory.SECURITY_POLICY_VIOLATION, diagOpt.get().getCategory());
            assertTrue(diagOpt.get().getLikelyCause().contains("container isolation"));
        } finally {
            AppConfig.getInstance().set("EXECUTION_CONTAINER_ENABLED", "true");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Test E — Trusted Local Project Execution via HostExecutionBackend")
    void testTrustedLocalProjectExecutionViaHostBackend() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-trusted-host-" + uid;
        File projectDir = testWorkspaceDir.resolve(projId).toFile();
        projectDir.mkdirs();

        // Local directory path evaluates to TRUSTED_LOCAL
        Project project = new Project(projId, "Trusted Local Proj " + uid, "Testing host execution",
                projectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-trusted-host-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Trusted Host Pipeline " + uid, "Host workflow", "SEQUENTIAL", true);

        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Host Print Env",
                StageType.BUILD, 1, "node -e \"console.log('HOST_EXECUTION_ACTIVE'); console.log('WORKSPACE: ' + process.cwd());\"", 30, true, null);
        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> execRef = new AtomicReference<>();
        List<ExecutionLog> logs = new CopyOnWriteArrayList<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                execRef.set(updated);
                latch.countDown();
            }
        }, logs::add);

        boolean finished = latch.await(15, TimeUnit.SECONDS);
        assertTrue(finished, "Host pipeline execution must complete within 15 seconds");

        Execution result = execRef.get();
        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());

        boolean hasHostLog = logs.stream().anyMatch(l -> l.getMessage().contains("HOST_EXECUTION_ACTIVE"));
        assertTrue(hasHostLog, "Logs must contain output executed on host");
    }

    @Test
    @Order(6)
    @DisplayName("Test F — Persistence Across Restart: Historical Reconstruction Integrity")
    void testPersistenceAcrossRestart() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-persist-audit-" + uid;
        File projectDir = testWorkspaceDir.resolve(projId).toFile();
        projectDir.mkdirs();

        Project project = new Project(projId, "Persistence Audit Proj " + uid, "Testing DB restart",
                projectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-persist-audit-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Persist Pipe " + uid, "Audit pipe", "SEQUENTIAL", true);

        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Stage One",
                StageType.BUILD, 1, "node -e \"console.log('RESTART_PERSISTENCE_MARKER');\"", 30, true, null);
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

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        Execution finished = execRef.get();
        assertNotNull(finished);

        // Restart database connection pool
        Thread.sleep(300);
        DatabaseManager.resetInstanceForTesting();
        DatabaseManager reloadedDb = DatabaseManager.getInstance();
        ExecutionRepository reloadedExecRepo = new ExecutionRepository(reloadedDb);
        LogRepository reloadedLogRepo = new LogRepository(reloadedDb);

        Execution reloaded = reloadedExecRepo.findById(finished.getId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, reloaded.getStatus());
        assertEquals(finished.getExecutionNumber(), reloaded.getExecutionNumber());

        List<ExecutionLog> reloadedLogs = reloadedLogRepo.findByExecutionId(finished.getId());
        assertFalse(reloadedLogs.isEmpty());
        assertTrue(reloadedLogs.stream().anyMatch(l -> l.getMessage().contains("RESTART_PERSISTENCE_MARKER")));
    }

    @Test
    @Order(7)
    @DisplayName("Test G — Workspace Consistency: Directory Confinement and Path Enforcement")
    void testWorkspaceConsistencyAndConfinement() {
        ExecutionPolicy policy = new ExecutionPolicy(ExecutionTrustLevel.UNTRUSTED_EXTERNAL);
        File authorizedBase = testWorkspaceDir.toFile();

        // 1. Authorized subfolder in workspace
        File validProjectDir = new File(authorizedBase, "projects/proj-123");
        validProjectDir.mkdirs();
        assertDoesNotThrow(() -> policy.validateWorkingDirectory(validProjectDir, authorizedBase));

        // 2. Traversal attempt escaping authorized base
        File traversalDir = new File(authorizedBase, "../../../etc");
        assertThrows(SecurityException.class, () -> policy.validateWorkingDirectory(traversalDir, authorizedBase));

        // 3. Application root itself is forbidden
        File appRoot = new File(".");
        assertThrows(SecurityException.class, () -> policy.validateWorkingDirectory(appRoot, authorizedBase));
    }

    @Test
    @Order(8)
    @DisplayName("Test H — Process Cancellation & Container Termination")
    void testProcessCancellationAndContainerTermination() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-cancel-test-" + uid;
        File projectDir = testWorkspaceDir.resolve(projId).toFile();
        projectDir.mkdirs();

        Project project = new Project(projId, "Cancel Proj " + uid, "Testing cancel",
                projectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-cancel-test-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "Cancel Pipe " + uid, "Testing cancel", "SEQUENTIAL", true);

        // 30-second sleep stage
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Cancellable Sleep",
                StageType.BUILD, 1, "node -e \"setTimeout(() => {}, 30000)\"", 60, true, null);
        pipeline.setStages(List.of(s1));
        pipelineRepository.save(pipeline);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);
        AtomicReference<Execution> execRef = new AtomicReference<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus() == ExecutionStatus.RUNNING) {
                startLatch.countDown();
            }
            if (updated.getStatus().isTerminal()) {
                execRef.set(updated);
                finishLatch.countDown();
            }
        }, null);

        // Wait until running, then issue cancel
        assertTrue(startLatch.await(5, TimeUnit.SECONDS));
        Thread.sleep(500);

        boolean cancelAcknowledged = engine.cancelExecution(ex.getId());
        assertTrue(cancelAcknowledged, "Engine must acknowledge active execution cancellation");

        boolean finished = finishLatch.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "Execution must terminate promptly after cancellation");

        Execution result = execRef.get();
        assertNotNull(result);
        assertEquals(ExecutionStatus.CANCELLED, result.getStatus(), "Terminal state must be CANCELLED");
    }

    @Test
    @Order(9)
    @DisplayName("Test I — No Fake Success: Missing Build Output Directory Fails Stage")
    void testNoFakeSuccessOnMissingOutput() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String projId = "proj-nofake-succ-" + uid;
        File projectDir = testWorkspaceDir.resolve(projId).toFile();
        projectDir.mkdirs();

        // Simulate Next.js project with package.json and next.config.js
        try (FileWriter fw = new FileWriter(new File(projectDir, "package.json"))) {
            fw.write("{\"name\": \"next-fake-check\"}\n");
        }
        try (FileWriter fw = new FileWriter(new File(projectDir, "next.config.js"))) {
            fw.write("module.exports = {};\n");
        }

        Project project = new Project(projId, "No Fake Success Proj " + uid, "Testing fake success defense",
                projectDir.getAbsolutePath(), "main", "NodeJS", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-nofake-succ-" + uid;
        Pipeline pipeline = new Pipeline(pipeId, projId, "No Fake Success Pipeline " + uid, "Workflow", "SEQUENTIAL", true);

        // Command exits 0 via echo with 'build' in command, but does NOT produce the expected .next directory
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Build Fake Echo",
                StageType.BUILD, 1, "echo 'Simulating build exit 0 without .next'", 30, true, null);
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

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        Execution result = execRef.get();
        assertNotNull(result);

        // StageRunner must detect missing .next directory and fail the stage rather than reporting fake success
        assertEquals(ExecutionStatus.FAILED, result.getStatus(),
                "Stage must fail when build command exits 0 but expected output directory is absent");
    }

    @Test
    @Order(10)
    @DisplayName("Test J — Real Target Execution: Full 5-Stage Pipeline on L/Air Repository")
    void testRealTargetExecutionOnLairPipeline() throws Exception {
        String projId = "proj-target-hhtestair-" + UUID.randomUUID().toString().substring(0, 8);
        String pipeId = "pipe-target-hhtestair-" + UUID.randomUUID().toString().substring(0, 8);
        File projectWorkspace = gitService.getProjectWorkspace(projId);

        // Step 1: Real Git checkout from GitHub
        var gitRes = gitService.cloneOrFetch("https://github.com/Kaviselvaram/lair.git", "main", projectWorkspace);
        assertEquals(0, gitRes.getExitCode(), "Real clone of L/Air repository must succeed");

        String commit = gitService.getCurrentCommitHash(projectWorkspace);
        assertNotNull(commit);
        assertEquals(40, commit.length());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Project project = new Project(projId, "Lair Verified Target " + suffix, "Real target project",
                projectWorkspace.getAbsolutePath(), "main", "Next.js (NodeJS)", "ACTIVE");
        projectRepository.save(project);

        Pipeline pipeline = new Pipeline(pipeId, projId, "Lair Production Pipeline " + suffix, "5-stage CI workflow", "SEQUENTIAL", true);

        // 5 authentic stages matching user's screenshot
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Git Checkout",
                StageType.CHECKOUT, 1, "checkout", 120, true, null);

        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Install Dependencies",
                StageType.BUILD, 2, "npm ci", 300, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        Stage s3 = new Stage(UUID.randomUUID().toString(), pipeId, "Lint Source",
                StageType.TEST, 3, "npm run lint", 300, true, null);
        s3.getDependsOnStageIds().add(s2.getId());

        Stage s4 = new Stage(UUID.randomUUID().toString(), pipeId, "Build Application",
                StageType.PACKAGE, 4, "npm run build", 300, true, null);
        s4.getDependsOnStageIds().add(s3.getId());

        Stage s5 = new Stage(UUID.randomUUID().toString(), pipeId, "Verify Next.js Build Artifacts",
                StageType.PACKAGE, 5, "test -f .next/BUILD_ID || test -d .next", 120, true, null);
        s5.getDependsOnStageIds().add(s4.getId());

        pipeline.setStages(List.of(s1, s2, s3, s4, s5));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> execRef = new AtomicReference<>();
        List<ExecutionLog> logs = new CopyOnWriteArrayList<>();

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                execRef.set(updated);
                latch.countDown();
            }
        }, logs::add);

        boolean completed = latch.await(4, TimeUnit.MINUTES);
        assertTrue(completed, "Full 5-stage pipeline must complete within 4 minutes");

        Execution result = execRef.get();
        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(), "All 5 stages of the pipeline must succeed");

        // Verify stage records
        List<StageExecution> stageExecs = executionRepository.findStageExecutionsByExecutionId(result.getId());
        assertEquals(5, stageExecs.size(), "All 5 stages must have execution records");
        for (StageExecution se : stageExecs) {
            assertEquals(StageExecutionStatus.SUCCESS, se.getStatus(), "Stage '" + se.getStageName() + "' must be SUCCESS");
            assertEquals(0, se.getExitCode());
            assertTrue(se.isExitCodeSet());
            assertTrue(se.getDurationSeconds() >= 0.0);
        }

        // Verify .next/BUILD_ID physically exists
        File buildId = new File(projectWorkspace, ".next/BUILD_ID");
        assertTrue(buildId.exists(), ".next/BUILD_ID must be produced by Next.js build");

        // Verify artifact inspected
        assertNotNull(result.getArtifactPath());
        File artifactDir = new File(result.getArtifactPath());
        assertTrue(artifactDir.exists());
    }
}
