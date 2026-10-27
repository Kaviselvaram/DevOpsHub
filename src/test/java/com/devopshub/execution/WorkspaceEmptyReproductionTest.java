package com.devopshub.execution;

import com.devopshub.config.DatabaseManager;
import com.devopshub.git.GitService;
import com.devopshub.model.*;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.PipelineRepository;
import com.devopshub.repository.ProjectRepository;
import com.devopshub.service.PipelineService;
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

@DisplayName("Reproduction Test: Investigating Why Workspace Directories Remain Empty")
public class WorkspaceEmptyReproductionTest {

    @TempDir
    Path tempWorkspaces;

    @Test
    @DisplayName("Reproduce: Default pipeline with CHECKOUT stage ('git status') fails to clone repository into project workspace")
    void reproduceDefaultPipelineCheckoutFailure() throws IOException, InterruptedException {
        // 1. Create a local disposable Git repository with actual commits
        File originDir = tempWorkspaces.resolve("origin-repo").toFile();
        originDir.mkdirs();

        // Initialize origin git repository
        ProcessBuilder initPb = new ProcessBuilder("git", "init", "-b", "main");
        initPb.directory(originDir);
        assertEquals(0, initPb.start().waitFor());

        ProcessBuilder configName = new ProcessBuilder("git", "config", "user.name", "CI Test");
        configName.directory(originDir);
        assertEquals(0, configName.start().waitFor());

        ProcessBuilder configEmail = new ProcessBuilder("git", "config", "user.email", "ci@test.local");
        configEmail.directory(originDir);
        assertEquals(0, configEmail.start().waitFor());

        File dummyFile = new File(originDir, "package.json");
        try (FileWriter fw = new FileWriter(dummyFile)) {
            fw.write("{\"name\": \"repro-app\", \"version\": \"1.0.0\"}\n");
        }

        ProcessBuilder addPb = new ProcessBuilder("git", "add", "package.json");
        addPb.directory(originDir);
        assertEquals(0, addPb.start().waitFor());

        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "-m", "Initial commit");
        commitPb.directory(originDir);
        assertEquals(0, commitPb.start().waitFor());

        // 2. Setup project and pipeline using standard PipelineService.createDefaultStages
        File baseWorkspace = tempWorkspaces.resolve("workspaces").toFile();
        baseWorkspace.mkdirs();

        GitService gitService = new GitService("git", baseWorkspace);
        ProjectRepository projRepo = new ProjectRepository();
        PipelineRepository pipeRepo = new PipelineRepository();
        ExecutionRepository execRepo = new ExecutionRepository();
        PipelineService pipeService = new PipelineService();

        String projId = "proj-repro-" + UUID.randomUUID();
        // Use file:// URL to simulate remote git repo
        String repoUrl = originDir.toURI().toString();
        Project project = new Project(projId, "Repro Project " + UUID.randomUUID(), "Desc", repoUrl, "main", "NodeJS", "ACTIVE");
        projRepo.save(project);

        String pipeId = "pipe-repro-" + UUID.randomUUID();
        Pipeline pipeline = new Pipeline(pipeId, projId, "Repro Pipeline", "Desc", "SEQUENTIAL", true);
        List<Stage> defaultStages = pipeService.createDefaultStages(pipeId);
        pipeline.setStages(defaultStages);
        pipeRepo.save(pipeline);

        ExecutionEngine engine = new ExecutionEngine(
                execRepo, null, null, null,
                ExecutionThreadPool.getInstance(), projRepo, gitService, null
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        Execution ex = engine.startExecution(pipeline, TriggerType.MANUAL, updated -> {
            if (updated.getStatus().isTerminal()) {
                completed.set(true);
                latch.countDown();
            }
        }, null);

        boolean finishedInTime = latch.await(15, TimeUnit.SECONDS);
        assertTrue(finishedInTime, "Pipeline should terminate within 15 seconds");

        File projectWorkspace = gitService.getProjectWorkspace(projId);
        File gitDir = new File(projectWorkspace, ".git");
        File pkgJson = new File(projectWorkspace, "package.json");

        System.out.println("Project workspace: " + projectWorkspace.getAbsolutePath());
        System.out.println("Project workspace exists: " + projectWorkspace.exists());
        System.out.println("Files in project workspace: " + (projectWorkspace.list() != null ? String.join(", ", projectWorkspace.list()) : "null"));
        System.out.println(".git exists: " + gitDir.exists());
        System.out.println("package.json exists: " + pkgJson.exists());

        Execution finalExec = execRepo.findById(ex.getId()).orElseThrow();
        System.out.println("Final execution status: " + finalExec.getStatus());

        // We assert whether .git and package.json actually exist
        assertTrue(gitDir.exists(), "Root cause reproduction: .git must exist in project workspace");
        assertTrue(pkgJson.exists(), "Root cause reproduction: package.json must exist in project workspace");
    }
}
