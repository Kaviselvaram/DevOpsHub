package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Project;
import com.devopshub.model.Stage;
import com.devopshub.model.StageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pipeline Repository Dependency & Integrity Regression Suite")
public class PipelineRepositoryDependencyIntegrityTest {

    private DatabaseManager dbManager;
    private ProjectRepository projectRepository;
    private PipelineRepository pipelineRepository;

    private String testProjectId;
    private final List<String> createdPipelineIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.getInstance();
        projectRepository = new ProjectRepository(dbManager);
        pipelineRepository = new PipelineRepository(dbManager);

        testProjectId = "proj-integrity-test-" + UUID.randomUUID();
        Project project = new Project(testProjectId, "Integrity Test Project", "Testing FK constraints",
                "https://github.com/example/integrity.git", "main", "Java", "ACTIVE");
        projectRepository.save(project);
    }

    @AfterEach
    void tearDown() {
        for (String pipeId : createdPipelineIds) {
            try {
                pipelineRepository.delete(pipeId);
            } catch (Exception ignored) {}
        }
        try {
            projectRepository.delete(testProjectId);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Verify saving pipeline with forward/inverted stage dependency order succeeds without FK violation")
    void testSavePipelineWithForwardReferencingDependencies() {
        String pipeId = "pipe-fwd-dep-" + UUID.randomUUID();
        createdPipelineIds.add(pipeId);

        Pipeline pipeline = new Pipeline(pipeId, testProjectId, "Forward Dependency Pipeline", "Desc", "SEQUENTIAL", true);

        // Stage 1 (earlier in list) depends on Stage 2 (later in list)
        String s1Id = UUID.randomUUID().toString();
        String s2Id = UUID.randomUUID().toString();

        Stage stage1 = new Stage(s1Id, pipeId, "Deploy Stage", StageType.DEPLOY, 2, "echo deploy", 120, true, null);
        stage1.getDependsOnStageIds().add(s2Id); // Depends on s2 which comes LATER in the list

        Stage stage2 = new Stage(s2Id, pipeId, "Build Stage", StageType.BUILD, 1, "echo build", 180, true, null);

        // Intentionally put stage1 first in the list
        pipeline.setStages(List.of(stage1, stage2));

        // In single-pass insertion, this threw:
        // "Cannot add or update a child row: a foreign key constraint fails (`devopshub`.`stage_dependencies`...)"
        assertDoesNotThrow(() -> pipelineRepository.save(pipeline),
                "Two-pass persistence must succeed even when prerequisite stage appears later in stages list");

        // Verify reloaded pipeline and stages
        Optional<Pipeline> reloadedOpt = pipelineRepository.findById(pipeId);
        assertTrue(reloadedOpt.isPresent());
        Pipeline reloaded = reloadedOpt.get();
        assertEquals(2, reloaded.getStages().size());

        Stage reloadedS1 = reloaded.getStages().stream().filter(s -> s.getId().equals(s1Id)).findFirst().orElseThrow();
        assertEquals(1, reloadedS1.getDependsOnStageIds().size());
        assertEquals(s2Id, reloadedS1.getDependsOnStageIds().get(0));
    }

    @Test
    @DisplayName("Verify atomic updateWithStages updates stages and dependencies atomically")
    void testAtomicPipelineUpdateWithAddedAndRemovedStages() {
        String pipeId = "pipe-update-atomic-" + UUID.randomUUID();
        createdPipelineIds.add(pipeId);

        Pipeline pipeline = new Pipeline(pipeId, testProjectId, "Atomic Update Pipeline", "Initial", "SEQUENTIAL", true);

        String s1Id = UUID.randomUUID().toString();
        String s2Id = UUID.randomUUID().toString();
        Stage s1 = new Stage(s1Id, pipeId, "Stage 1", StageType.CHECKOUT, 1, "echo 1", 60, true, null);
        Stage s2 = new Stage(s2Id, pipeId, "Stage 2", StageType.BUILD, 2, "echo 2", 120, true, null);
        s2.getDependsOnStageIds().add(s1Id);
        pipeline.setStages(List.of(s1, s2));

        pipelineRepository.save(pipeline);

        // Now update: remove s1, keep s2, add s3 that depends on s2
        String s3Id = UUID.randomUUID().toString();
        Stage s2Updated = new Stage(s2Id, pipeId, "Stage 2 Updated", StageType.BUILD, 1, "echo 2 updated", 120, true, null);
        s2Updated.getDependsOnStageIds().clear(); // no longer depends on removed s1

        Stage s3 = new Stage(s3Id, pipeId, "Stage 3", StageType.TEST, 2, "echo 3", 90, true, null);
        s3.getDependsOnStageIds().add(s2Id);

        pipeline.setName("Atomic Update Pipeline (Revised)");
        assertDoesNotThrow(() -> pipelineRepository.updateWithStages(pipeline, List.of(s2Updated, s3)),
                "updateWithStages must atomically prune removed stages and persist revised stages and dependencies");

        Pipeline reloaded = pipelineRepository.findById(pipeId).orElseThrow();
        assertEquals("Atomic Update Pipeline (Revised)", reloaded.getName());
        assertEquals(2, reloaded.getStages().size());

        assertFalse(reloaded.getStages().stream().anyMatch(s -> s.getId().equals(s1Id)), "Stage 1 must have been deleted");
        Stage reloadedS3 = reloaded.getStages().stream().filter(s -> s.getId().equals(s3Id)).findFirst().orElseThrow();
        assertEquals(List.of(s2Id), reloadedS3.getDependsOnStageIds());
    }

    @Test
    @DisplayName("Verify pipeline duplication preserves DAG dependencies with remapped stage IDs")
    void testDuplicationPreservesDAGTopology() {
        String sourcePipeId = "pipe-source-dup-" + UUID.randomUUID();
        createdPipelineIds.add(sourcePipeId);

        Pipeline source = new Pipeline(sourcePipeId, testProjectId, "Source Pipeline", "Desc", "SEQUENTIAL", true);

        String s1Id = UUID.randomUUID().toString();
        String s2Id = UUID.randomUUID().toString();
        String s3Id = UUID.randomUUID().toString();

        Stage s1 = new Stage(s1Id, sourcePipeId, "Checkout", StageType.CHECKOUT, 1, "echo checkout", 60, true, null);
        Stage s2 = new Stage(s2Id, sourcePipeId, "Build", StageType.BUILD, 2, "echo build", 120, true, null);
        s2.getDependsOnStageIds().add(s1Id);

        Stage s3 = new Stage(s3Id, sourcePipeId, "Package", StageType.PACKAGE, 3, "echo pkg", 120, true, null);
        s3.getDependsOnStageIds().add(s2Id);

        source.setStages(List.of(s1, s2, s3));
        pipelineRepository.save(source);

        // Duplicate pipeline
        Pipeline duplicate = pipelineRepository.duplicate(sourcePipeId);
        assertNotNull(duplicate);
        createdPipelineIds.add(duplicate.getId());

        assertNotEquals(sourcePipeId, duplicate.getId());
        assertEquals(3, duplicate.getStages().size());

        Stage dupS1 = duplicate.getStages().get(0);
        Stage dupS2 = duplicate.getStages().get(1);
        Stage dupS3 = duplicate.getStages().get(2);

        // All IDs must be new
        assertNotEquals(s1Id, dupS1.getId());
        assertNotEquals(s2Id, dupS2.getId());
        assertNotEquals(s3Id, dupS3.getId());

        // Dependencies must be remapped to the new stage IDs
        assertEquals(0, dupS1.getDependsOnStageIds().size());
        assertEquals(1, dupS2.getDependsOnStageIds().size());
        assertEquals(dupS1.getId(), dupS2.getDependsOnStageIds().get(0), "Dup S2 must depend on new Dup S1");

        assertEquals(1, dupS3.getDependsOnStageIds().size());
        assertEquals(dupS2.getId(), dupS3.getDependsOnStageIds().get(0), "Dup S3 must depend on new Dup S2");
    }

    @Test
    @DisplayName("Verify dangling dependencies on non-existent stages are safely ignored without crashing")
    void testDanglingDependencyGracefullyHandled() {
        String pipeId = "pipe-dangling-" + UUID.randomUUID();
        createdPipelineIds.add(pipeId);

        Pipeline pipeline = new Pipeline(pipeId, testProjectId, "Dangling Dependency Pipe", "Desc", "SEQUENTIAL", true);

        String s1Id = UUID.randomUUID().toString();
        Stage s1 = new Stage(s1Id, pipeId, "Stage With Dangling Dep", StageType.BUILD, 1, "echo build", 120, true, null);
        s1.getDependsOnStageIds().add("non-existent-stage-uuid-99999");
        pipeline.setStages(List.of(s1));

        // Must not throw FK constraint violation
        assertDoesNotThrow(() -> pipelineRepository.save(pipeline));

        Pipeline reloaded = pipelineRepository.findById(pipeId).orElseThrow();
        assertEquals(1, reloaded.getStages().size());
        assertTrue(reloaded.getStages().get(0).getDependsOnStageIds().isEmpty(),
                "Non-existent dangling dependency should be omitted to maintain relational integrity");
    }
}
