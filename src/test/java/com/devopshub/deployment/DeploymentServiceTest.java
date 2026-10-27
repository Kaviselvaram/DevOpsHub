package com.devopshub.deployment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeploymentService Real Artifact Delivery & SHA-256 Verification Tests")
class DeploymentServiceTest {

    private DeploymentService deploymentService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        deploymentService = new DeploymentService();
    }

    @Test
    @DisplayName("Should successfully deploy artifact and verify SHA-256 integrity checksum")
    void testDeployArtifactSuccess() throws IOException {
        // Create source artifact
        File sourceArtifact = tempDir.resolve("devopshub-sample-app-1.0.jar").toFile();
        try (FileWriter fw = new FileWriter(sourceArtifact)) {
            fw.write("PK\u0003\u0004 Genuine Application Binary Content for Deployment Verification");
        }

        File targetDeployDir = tempDir.resolve("production-deployment-target").toFile();

        DeploymentService.DeploymentResult result = deploymentService.deployArtifact(sourceArtifact, targetDeployDir);

        assertTrue(result.isSuccess(), "Deployment must succeed: " + result.getMessage());
        assertNotNull(result.getTargetPath());
        File deployedFile = new File(result.getTargetPath());
        assertTrue(deployedFile.exists(), "Deployed artifact must physically exist at destination");
        assertEquals(sourceArtifact.length(), deployedFile.length());

        String sourceSha = DeploymentService.computeSha256(sourceArtifact);
        assertEquals(sourceSha, result.getSha256Checksum(), "SHA-256 checksum must match source bit-for-bit");
        assertNotNull(result.getDeployedAt());
        assertTrue(result.getDurationSeconds() >= 0.0);
    }

    @Test
    @DisplayName("Should reject nonexistent source artifact")
    void testDeployNonexistentArtifact() {
        File nonexistent = new File(tempDir.toFile(), "ghost-binary.jar");
        File targetDir = tempDir.resolve("target").toFile();

        DeploymentService.DeploymentResult result = deploymentService.deployArtifact(nonexistent, targetDir);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("Should reject empty (0-byte) source artifact")
    void testDeployEmptyArtifact() throws IOException {
        File emptyArtifact = tempDir.resolve("empty.jar").toFile();
        emptyArtifact.createNewFile();

        File targetDir = tempDir.resolve("target").toFile();

        DeploymentService.DeploymentResult result = deploymentService.deployArtifact(emptyArtifact, targetDir);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("is empty"));
    }

    @Test
    @DisplayName("Should support rollback to previous deployment version")
    void testRollbackSupport() throws IOException {
        File targetDir = tempDir.resolve("staging-target").toFile();

        // 1. First deployment (v1)
        File v1 = tempDir.resolve("app-v1.jar").toFile();
        try (FileWriter fw = new FileWriter(v1)) { fw.write("Version 1.0 Content"); }
        var res1 = deploymentService.deployArtifact(v1, targetDir);
        assertTrue(res1.isSuccess());

        // 2. Second deployment (v2 replaces v1)
        File v2 = tempDir.resolve("app-v1.jar").toFile();
        try (FileWriter fw = new FileWriter(v2)) { fw.write("Version 2.0 Content Updated"); }
        var res2 = deploymentService.deployArtifact(v2, targetDir);
        assertTrue(res2.isSuccess());

        // 3. Rollback back to v1
        com.devopshub.repository.ProjectRepository projectRepo = new com.devopshub.repository.ProjectRepository();
        var proj = new com.devopshub.model.Project("proj-roll-" + java.util.UUID.randomUUID(), "Roll Proj " + java.util.UUID.randomUUID(), "Desc", "local://roll", "main", "Java", "ACTIVE");
        projectRepo.save(proj);
        com.devopshub.repository.PipelineRepository pipeRepo = new com.devopshub.repository.PipelineRepository();
        var pipe = new com.devopshub.model.Pipeline("pipe-roll-" + java.util.UUID.randomUUID(), proj.getId(), "Roll Pipe", "Desc", "SEQUENTIAL", true);
        pipeRepo.save(pipe);
        com.devopshub.repository.ExecutionRepository execRepo = new com.devopshub.repository.ExecutionRepository();
        var exec = new com.devopshub.model.Execution(java.util.UUID.randomUUID().toString(), pipe.getId(), proj.getId(), 1,
                com.devopshub.model.ExecutionStatus.RUNNING, com.devopshub.model.TriggerType.MANUAL);
        execRepo.save(exec);

        DeploymentConfig config = DeploymentConfig.localStaging(targetDir, DeploymentEnvironment.STAGING);
        var rollbackRes = deploymentService.rollback(config, exec.getId(), pipe.getId());
        assertTrue(rollbackRes.isSuccess(), "Rollback must succeed");
        assertTrue(rollbackRes.getMessage().contains("rolled back"));
    }

    @Test
    @DisplayName("Should record deployment history in database repository")
    void testDeploymentHistoryPersistence() throws IOException {
        com.devopshub.repository.ProjectRepository projectRepo = new com.devopshub.repository.ProjectRepository();
        var proj = new com.devopshub.model.Project("proj-dep-" + java.util.UUID.randomUUID(), "Dep Proj " + java.util.UUID.randomUUID(), "Desc", "local://dep", "main", "Java", "ACTIVE");
        projectRepo.save(proj);

        com.devopshub.repository.PipelineRepository pipeRepo = new com.devopshub.repository.PipelineRepository();
        var pipe = new com.devopshub.model.Pipeline("pipe-dep-" + java.util.UUID.randomUUID(), proj.getId(), "Dep Pipe", "Desc", "SEQUENTIAL", true);
        pipeRepo.save(pipe);

        com.devopshub.repository.ExecutionRepository execRepo = new com.devopshub.repository.ExecutionRepository();
        var exec = new com.devopshub.model.Execution(java.util.UUID.randomUUID().toString(), pipe.getId(), proj.getId(), 1,
                com.devopshub.model.ExecutionStatus.RUNNING, com.devopshub.model.TriggerType.MANUAL);
        execRepo.save(exec);

        File artifact = tempDir.resolve("app.jar").toFile();
        try (FileWriter fw = new FileWriter(artifact)) { fw.write("Test Binary"); }
        File targetDir = tempDir.resolve("history-target").toFile();

        DeploymentConfig config = DeploymentConfig.localStaging(targetDir, DeploymentEnvironment.TEST);
        var res = deploymentService.deployArtifact(artifact, config, exec.getId(), pipe.getId());
        assertTrue(res.isSuccess());

        com.devopshub.repository.DeploymentRepository repo = new com.devopshub.repository.DeploymentRepository();
        var records = repo.findByExecutionId(exec.getId());
        assertFalse(records.isEmpty(), "Deployment record must be durably persisted");
        assertEquals("SUCCESS", records.get(0).getStatus());
        assertEquals(DeploymentTargetType.LOCAL_STAGING, records.get(0).getTargetType());
    }
}
