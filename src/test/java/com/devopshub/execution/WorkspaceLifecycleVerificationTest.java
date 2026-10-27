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
import com.devopshub.service.PipelineService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Authoritative Workspace Lifecycle & Filesystem Reality Verification")
public class WorkspaceLifecycleVerificationTest {

    private static DatabaseManager dbManager;
    private ProjectRepository projectRepository;
    private PipelineRepository pipelineRepository;
    private ExecutionRepository executionRepository;
    private LogRepository logRepository;
    private DiagnosisRepository diagnosisRepository;
    private PipelineService pipelineService;

    @TempDir
    Path testRoot;

    private File baseWorkspaceDir;
    private GitService gitService;
    private ExecutionEngine engine;

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

        baseWorkspaceDir = testRoot.resolve("workspaces").toFile();
        baseWorkspaceDir.mkdirs();

        gitService = new GitService("git", baseWorkspaceDir);
        engine = new ExecutionEngine(executionRepository, logRepository, diagnosisRepository,
                new FailureRuleEngine(), ExecutionThreadPool.getInstance(), projectRepository, gitService, new DeploymentService());
        pipelineService = new PipelineService(pipelineRepository, null);
    }

    private File createOriginGitRepository(String repoName) throws IOException, InterruptedException {
        File originDir = testRoot.resolve(repoName).toFile();
        originDir.mkdirs();

        ProcessBuilder initPb = new ProcessBuilder("git", "init", "-b", "main");
        initPb.directory(originDir);
        assertEquals(0, initPb.start().waitFor());

        ProcessBuilder cfgName = new ProcessBuilder("git", "config", "user.name", "CI Bot");
        cfgName.directory(originDir);
        cfgName.start().waitFor();

        ProcessBuilder cfgMail = new ProcessBuilder("git", "config", "user.email", "bot@devopshub.test");
        cfgMail.directory(originDir);
        cfgMail.start().waitFor();

        File readme = new File(originDir, "README.md");
        try (FileWriter fw = new FileWriter(readme)) {
            fw.write("# " + repoName + "\nAuthentic test repository for workspace verification.\n");
        }

        File appFile = new File(originDir, "build.sh");
        try (FileWriter fw = new FileWriter(appFile)) {
            fw.write("#!/bin/sh\necho 'Building package...' > build_output.txt\n");
        }
        appFile.setExecutable(true);

        ProcessBuilder addPb = new ProcessBuilder("git", "add", ".");
        addPb.directory(originDir);
        assertEquals(0, addPb.start().waitFor());

        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "-m", "Initial commit");
        commitPb.directory(originDir);
        assertEquals(0, commitPb.start().waitFor());

        return originDir;
    }

    @Test
    @DisplayName("Scenario A: Remote Git repository clones into exact workspace directory with .git and tracked files")
    void testRemoteGitCloneIntoProjectWorkspace() throws Exception {
        File origin = createOriginGitRepository("origin-remote-a");
        String originUrl = origin.toURI().toString();

        String projId = "proj-ws-" + UUID.randomUUID();
        Project project = new Project(projId, "Workspace Test Project " + UUID.randomUUID(), "Testing clone persistence",
                originUrl, "main", "Generic", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-ws-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, projId, "Workspace Pipeline", "Sequential checkout", "SEQUENTIAL", true);

        // Stage 1: Checkout Source using 'checkout'
        Stage checkoutStage = new Stage(UUID.randomUUID().toString(), pipeId, "Checkout Source",
                StageType.CHECKOUT, 1, "checkout", 60, true, null);

        // Stage 2: Build running build.sh
        Stage buildStage = new Stage(UUID.randomUUID().toString(), pipeId, "Build Step",
                StageType.BUILD, 2, "sh build.sh", 60, true, null);
        buildStage.getDependsOnStageIds().add(checkoutStage.getId());

        pipeline.setStages(List.of(checkoutStage, buildStage));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean done = latch.await(20, TimeUnit.SECONDS);
        assertTrue(done, "Execution should complete within 20s");

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, finalExec.getStatus());

        // Verify filesystem reality at exact expected location: <baseWorkspace>/projects/<projectId>
        File expectedWorkspace = gitService.getProjectWorkspace(projId);
        assertTrue(expectedWorkspace.exists(), "Workspace folder must exist: " + expectedWorkspace.getAbsolutePath());
        assertTrue(expectedWorkspace.isDirectory(), "Workspace must be a directory");

        File gitMetadata = new File(expectedWorkspace, ".git");
        assertTrue(gitMetadata.exists(), ".git directory must exist inside project workspace");

        File readme = new File(expectedWorkspace, "README.md");
        assertTrue(readme.exists(), "Tracked file README.md must exist inside project workspace");

        File buildOutput = new File(expectedWorkspace, "build_output.txt");
        assertTrue(buildOutput.exists(), "Generated build_output.txt must exist inside project workspace");

        // Verify commit hash recorded in execution matches genuine git commit
        String expectedSha = gitService.getCurrentCommitHash(expectedWorkspace);
        assertNotNull(expectedSha);
        assertEquals(expectedSha, finalExec.getCommitHash(), "Recorded commit SHA must match workspace git commit");
    }

    @Test
    @DisplayName("Scenario B: Default pipeline with checkout command 'git status' succeeds and clones workspace")
    void testDefaultPipelineCheckoutCommandStatusSucceeds() throws Exception {
        File origin = createOriginGitRepository("origin-status-b");
        String originUrl = origin.toURI().toString();

        String projId = "proj-status-" + UUID.randomUUID();
        Project project = new Project(projId, "Status Test Project " + UUID.randomUUID(), "Desc", originUrl, "main", "Generic", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-status-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, projId, "Status Pipeline", "Desc", "SEQUENTIAL", true);

        // Stage 1: Checkout Source with command 'git status' (the legacy default that previously failed)
        Stage checkoutStage = new Stage(UUID.randomUUID().toString(), pipeId, "Checkout Source",
                StageType.CHECKOUT, 1, "git status", 60, true, null);

        pipeline.setStages(List.of(checkoutStage));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean done = latch.await(20, TimeUnit.SECONDS);
        assertTrue(done, "Execution should complete within 20s");

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, finalExec.getStatus());

        File expectedWorkspace = gitService.getProjectWorkspace(projId);
        File gitDir = new File(expectedWorkspace, ".git");
        assertTrue(gitDir.exists(), ".git directory must exist after default checkout stage execution");

        File readme = new File(expectedWorkspace, "README.md");
        assertTrue(readme.exists(), "Tracked files must exist in workspace after default checkout");
    }

    @Test
    @DisplayName("Scenario C: Intentional build failure aborts pipeline and preserves workspace for debugging")
    void testIntentionalBuildFailurePreservesWorkspace() throws Exception {
        File origin = createOriginGitRepository("origin-fail-c");
        String originUrl = origin.toURI().toString();

        String projId = "proj-fail-" + UUID.randomUUID();
        Project project = new Project(projId, "Failing Project " + UUID.randomUUID(), "Desc", originUrl, "main", "Generic", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-fail-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, projId, "Failing Pipeline", "Desc", "SEQUENTIAL", true);

        Stage checkoutStage = new Stage(UUID.randomUUID().toString(), pipeId, "Checkout Source",
                StageType.CHECKOUT, 1, "checkout", 60, true, null);

        // Intentionally failing command with exit code 42
        Stage failingStage = new Stage(UUID.randomUUID().toString(), pipeId, "Failing Build",
                StageType.BUILD, 2, "exit 42", 60, true, null);
        failingStage.getDependsOnStageIds().add(checkoutStage.getId());

        Stage downstream = new Stage(UUID.randomUUID().toString(), pipeId, "Downstream Should Skip",
                StageType.PACKAGE, 3, "echo 'should not run'", 60, true, null);
        downstream.getDependsOnStageIds().add(failingStage.getId());

        pipeline.setStages(List.of(checkoutStage, failingStage, downstream));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);

        boolean done = latch.await(20, TimeUnit.SECONDS);
        assertTrue(done, "Execution should finish within 20s");

        Execution finalExec = executionRepository.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, finalExec.getStatus());

        List<StageExecution> stageExecs = executionRepository.findStageExecutionsByExecutionId(ex.getId());
        assertEquals(3, stageExecs.size());

        StageExecution se1 = stageExecs.get(0);
        assertEquals(StageExecutionStatus.SUCCESS, se1.getStatus());
        assertEquals(0, se1.getExitCode());

        StageExecution se2 = stageExecs.get(1);
        assertEquals(StageExecutionStatus.FAILED, se2.getStatus());
        assertEquals(42, se2.getExitCode(), "Failing stage must capture exact nonzero exit code 42");

        StageExecution se3 = stageExecs.get(2);
        assertEquals(StageExecutionStatus.SKIPPED, se3.getStatus(), "Downstream stage must be SKIPPED");

        // Verify workspace is preserved for developer inspection (not deleted)
        File workspace = gitService.getProjectWorkspace(projId);
        assertTrue(workspace.exists(), "Workspace must remain available after failure for diagnosis");
        assertTrue(new File(workspace, "README.md").exists(), "Source files must remain in workspace");
    }

    @Test
    @DisplayName("Scenario D: Restart persistence verifies recorded state against filesystem reality")
    void testRestartPersistenceMatchesFilesystemReality() throws Exception {
        File origin = createOriginGitRepository("origin-persist-d");
        String originUrl = origin.toURI().toString();

        String projId = "proj-persist-" + UUID.randomUUID();
        String projName = "Persistence Project " + UUID.randomUUID();
        Project project = new Project(projId, projName, "Desc", originUrl, "main", "Generic", "ACTIVE");
        projectRepository.save(project);

        String pipeId = "pipe-persist-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, projId, "Persistence Pipeline", "Desc", "SEQUENTIAL", true);
        Stage checkoutStage = new Stage(UUID.randomUUID().toString(), pipeId, "Checkout Source",
                StageType.CHECKOUT, 1, "checkout", 60, true, null);
        pipeline.setStages(List.of(checkoutStage));
        pipelineRepository.save(pipeline);

        CountDownLatch latch = new CountDownLatch(1);
        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                latch.countDown();
            }
        }, null);
        assertTrue(latch.await(20, TimeUnit.SECONDS));

        // Simulate application restart: instantiate brand new repositories and verify all entities
        ProjectRepository newProjectRepo = new ProjectRepository(dbManager);
        PipelineRepository newPipelineRepo = new PipelineRepository(dbManager);
        ExecutionRepository newExecRepo = new ExecutionRepository(dbManager);

        Project loadedProj = newProjectRepo.findById(projId).orElseThrow();
        assertEquals(projName, loadedProj.getName());

        Pipeline loadedPipe = newPipelineRepo.findById(pipeId).orElseThrow();
        assertEquals(1, loadedPipe.getStages().size());

        Execution loadedExec = newExecRepo.findById(ex.getId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCESS, loadedExec.getStatus());
        assertNotNull(loadedExec.getCommitHash());

        // Verify files on disk match the reloaded database state
        File ws = gitService.getProjectWorkspace(projId);
        assertTrue(ws.exists());
        assertTrue(new File(ws, ".git").exists());
        assertTrue(new File(ws, "README.md").exists());
    }
}
