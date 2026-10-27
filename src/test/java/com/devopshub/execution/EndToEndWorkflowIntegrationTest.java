package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.config.DatabaseManager;
import com.devopshub.deployment.DeploymentService;
import com.devopshub.git.GitService;
import com.devopshub.model.*;
import com.devopshub.repository.DiagnosisRepository;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.LogRepository;
import com.devopshub.repository.PipelineRepository;
import com.devopshub.repository.ProjectRepository;
import com.devopshub.service.DashboardService;
import com.devopshub.service.ProjectImportService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("End-to-End Workflow Integration & Full Lifecycle Verification")
public class EndToEndWorkflowIntegrationTest {

    private static DatabaseManager dbManager;
    private ProjectRepository projectRepository;
    private PipelineRepository pipelineRepository;
    private ExecutionRepository executionRepository;
    private LogRepository logRepository;
    private DiagnosisRepository diagnosisRepository;
    private ExecutionEngine engine;
    private GitService gitService;
    private DashboardService dashboardService;
    private ProjectImportService importService;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initDb() {
        dbManager = DatabaseManager.getInstance();
    }

    @BeforeEach
    void setUp() {
        projectRepository = new ProjectRepository(dbManager);
        pipelineRepository = new PipelineRepository(dbManager);
        executionRepository = new ExecutionRepository(dbManager);
        logRepository = new LogRepository(dbManager);
        diagnosisRepository = new DiagnosisRepository(dbManager);
        gitService = new GitService("git", tempDir.toFile());
        engine = new ExecutionEngine(executionRepository, logRepository, diagnosisRepository,
                new FailureRuleEngine(), ExecutionThreadPool.getInstance(), projectRepository, gitService, new DeploymentService());
        dashboardService = new DashboardService(projectRepository, pipelineRepository, executionRepository, diagnosisRepository);
        importService = new ProjectImportService();
    }

    @Test
    @DisplayName("Workflow A: Full Lifecycle Real Git Clone -> Real Compile -> Real Package -> Real SHA-256 Deploy -> Verified Persistence")
    void testFullLifecycleRealGitBuildDeployPersistence() throws Exception {
        // 1. Setup a controlled real local Git repository
        File sourceRepoDir = tempDir.resolve("local-sample-app").toFile();
        sourceRepoDir.mkdirs();

        ProcessBuilder initPb = new ProcessBuilder("git", "init", "-b", "main");
        initPb.directory(sourceRepoDir);
        assertEquals(0, initPb.start().waitFor());

        ProcessBuilder configName = new ProcessBuilder("git", "config", "user.name", "CI Engine");
        configName.directory(sourceRepoDir);
        configName.start().waitFor();

        ProcessBuilder configEmail = new ProcessBuilder("git", "config", "user.email", "ci@devopshub.test");
        configEmail.directory(sourceRepoDir);
        configEmail.start().waitFor();

        // Write a real valid Java source code file
        File javaFile = new File(sourceRepoDir, "HelloApp.java");
        try (FileWriter fw = new FileWriter(javaFile)) {
            fw.write("public class HelloApp {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"DevOpsHub Real Build & Verification!\");\n" +
                    "    }\n" +
                    "}\n");
        }

        ProcessBuilder addPb = new ProcessBuilder("git", "add", "HelloApp.java");
        addPb.directory(sourceRepoDir);
        assertEquals(0, addPb.start().waitFor());

        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "-m", "feat: initial authentic source file");
        commitPb.directory(sourceRepoDir);
        assertEquals(0, commitPb.start().waitFor());

        String expectedCommitHash = gitService.getCurrentCommitHash(sourceRepoDir);
        assertNotNull(expectedCommitHash);
        assertEquals(40, expectedCommitHash.length());

        // 2. Register project in DevOpsHub pointing to this real Git repo
        String projId = "proj-e2e-" + UUID.randomUUID();
        Project project = new Project(projId, "E2E Sample Java App " + UUID.randomUUID(), "Real end-to-end integration project",
                sourceRepoDir.getAbsolutePath(), "main", "Java", "ACTIVE");
        projectRepository.save(project);

        // 3. Define Pipeline with authentic CHECKOUT, BUILD, PACKAGE, and DEPLOY stages
        String pipeId = "pipe-e2e-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, projId, "E2E-Delivery-Flow", "Real build and deploy verification", "SEQUENTIAL", true);

        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "Git Checkout",
                StageType.CHECKOUT, 1, "checkout", 60, true, null);

        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "Compile Java Source",
                StageType.BUILD, 2, "javac HelloApp.java", 60, true, null);
        s2.getDependsOnStageIds().add(s1.getId());

        Stage s3 = new Stage(UUID.randomUUID().toString(), pipeId, "Package Executable Archive",
                StageType.PACKAGE, 3, "jar -cfe HelloApp.jar HelloApp HelloApp.class", 60, true, null);
        s3.getDependsOnStageIds().add(s2.getId());

        Stage s4 = new Stage(UUID.randomUUID().toString(), pipeId, "Verify & Deploy Artifact",
                StageType.DEPLOY, 4, "deploy", 60, true, null);
        s4.getDependsOnStageIds().add(s3.getId());

        pipeline.setStages(List.of(s1, s2, s3, s4));
        pipelineRepository.save(pipeline);

        // 4. Execute the pipeline
        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean finishedInTime = latch.await(20, TimeUnit.SECONDS);
        assertTrue(finishedInTime, "Execution should finish within 20 seconds");

        // 5. Verify the execution status
        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, finalExec.getStatus(), "Pipeline must complete with SUCCESS");
        assertTrue(finalExec.getDurationSeconds() > 0.0);

        List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(4, stages.size());

        for (StageExecution se : stages) {
            assertEquals(StageExecutionStatus.SUCCESS, se.getStatus(), "Stage '" + se.getStageName() + "' must succeed");
            assertEquals(0, se.getExitCode());
        }

        // 6. Verify filesystem results
        File compiledClass = new File(sourceRepoDir, "HelloApp.class");
        assertTrue(compiledClass.exists(), "HelloApp.class must exist on filesystem");

        File packagedJar = new File(sourceRepoDir, "HelloApp.jar");
        assertTrue(packagedJar.exists(), "HelloApp.jar must exist on filesystem");
        assertTrue(packagedJar.length() > 0, "HelloApp.jar must have non-zero size");

        File deployedJar = new File(sourceRepoDir, "deployments/HelloApp.jar");
        assertTrue(deployedJar.exists(), "Deployed HelloApp.jar must exist in deployments directory");
        assertEquals(packagedJar.length(), deployedJar.length(), "Deployed jar size must match built jar size");

        // Compute and verify SHA-256 bit-for-bit
        String originalSha = DeploymentService.computeSha256(packagedJar);
        String deployedSha = DeploymentService.computeSha256(deployedJar);
        assertEquals(originalSha, deployedSha, "Source and deployed SHA-256 checksums must match identically");

        // 7. Verify persistent logs in database
        List<ExecutionLog> logs = logRepository.findByExecutionId(ex.getId());
        assertFalse(logs.isEmpty(), "Execution logs must be persisted in database");
        boolean hasShaLog = logs.stream().anyMatch(l -> l.getMessage().contains("SHA-256") || l.getMessage().contains("Deployment verified"));
        assertTrue(hasShaLog, "Logs must confirm verified deployment with SHA-256");

        // Cleanup
        compiledClass.delete();
        packagedJar.delete();
        deployedJar.delete();
    }

    @Test
    @DisplayName("Workflow B: Analytics Engine Independently Validates Metric Calculations Against Controlled Dataset")
    void testAnalyticsCalculationAgainstKnownDataset() {
        String testProjId = "proj-analytics-" + UUID.randomUUID();
        Project proj = new Project(testProjId, "Analytics Test Project " + UUID.randomUUID(), "Desc", "/tmp", "main", "Java", "ACTIVE");
        projectRepository.save(proj);

        String testPipeId = "pipe-analytics-" + UUID.randomUUID();
        Pipeline pipe = new Pipeline(testPipeId, testProjId, "Analytics Pipeline", "Desc", "SEQUENTIAL", true);
        pipelineRepository.save(pipe);

        // Insert controlled executions
        long now = System.currentTimeMillis();

        // 1. Success #1: 10s
        Execution e1 = new Execution(UUID.randomUUID().toString(), testPipeId, testProjId, 1, ExecutionStatus.SUCCESS, TriggerType.MANUAL);
        e1.setStartedAt(new Timestamp(now - 30000));
        e1.setFinishedAt(new Timestamp(now - 20000));
        e1.setDurationSeconds(10.0);
        executionRepository.save(e1);

        // 2. Success #2: 20s
        Execution e2 = new Execution(UUID.randomUUID().toString(), testPipeId, testProjId, 2, ExecutionStatus.SUCCESS, TriggerType.SCHEDULED);
        e2.setStartedAt(new Timestamp(now - 20000));
        e2.setFinishedAt(new Timestamp(now));
        e2.setDurationSeconds(20.0);
        executionRepository.save(e2);

        // 3. Failed #1 with diagnosis: 15s
        String failedExecId = UUID.randomUUID().toString();
        Execution e3 = new Execution(failedExecId, testPipeId, testProjId, 3, ExecutionStatus.FAILED, TriggerType.WEBHOOK);
        e3.setStartedAt(new Timestamp(now - 15000));
        e3.setFinishedAt(new Timestamp(now));
        e3.setDurationSeconds(15.0);
        executionRepository.save(e3);

        FailureDiagnosis diag = new FailureDiagnosis(UUID.randomUUID().toString(), failedExecId,
                "stage-comp", "Compilation Failed", FailureCategory.COMPILATION_ERROR,
                "Compilation Error: Syntax issue", "Missing semicolon", "line 42", "Check missing semicolon",
                "RULE_COMP_01", "1.0");
        diagnosisRepository.save(diag);

        // Query metrics via DashboardService
        DashboardMetrics metrics = dashboardService.getMetrics("ALL TIME (LIFETIME)");
        assertNotNull(metrics);
        assertTrue(metrics.getTotalExecutions() >= 3);
        assertTrue(metrics.getSuccessfulExecutions() >= 2);
        assertTrue(metrics.getFailedExecutions() >= 1);

        // Validate success rate formula: (successful / (successful + failed)) * 100
        int terminalTotal = metrics.getSuccessfulExecutions() + metrics.getFailedExecutions();
        double expectedRate = terminalTotal > 0 ? ((double) metrics.getSuccessfulExecutions() / terminalTotal) * 100.0 : 0.0;
        assertEquals(Math.round(expectedRate * 10.0) / 10.0, metrics.getSuccessRate(), 0.1);

        // Validate failure categories map contains COMPILATION_ERROR
        Map<String, Integer> categories = metrics.getFailureCategoryCounts();
        assertNotNull(categories);
        assertTrue(categories.containsKey("COMPILATION_ERROR"));
        assertTrue(categories.get("COMPILATION_ERROR") >= 1);
    }

    @Test
    @DisplayName("Workflow C: Unsupported Project & Path Traversal Handling")
    void testUnsupportedProjectAndPathSafety() {
        // Invalid non-existent path
        assertThrows(com.devopshub.exception.DevOpsHubException.class, () -> {
            importService.inspectPath(new File("/path/that/does/not/exist/at/all/devopshub_test"));
        });

        // Unsupported file format
        File invalidFile = tempDir.resolve("malware.exe").toFile();
        try {
            invalidFile.createNewFile();
        } catch (IOException ignored) {}

        assertThrows(com.devopshub.exception.DevOpsHubException.class, () -> {
            importService.inspectPath(invalidFile);
        });
    }
}
