package com.devopshub.pipeline;

import com.devopshub.exception.PipelineValidationException;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Stage;
import com.devopshub.model.StageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelineValidatorTest {

    private PipelineValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PipelineValidator();
    }

    @Test
    @DisplayName("Valid pipeline with linear DAG passes validation")
    void testValidPipeline() {
        Pipeline pipeline = new Pipeline();
        pipeline.setId("p-1");
        pipeline.setName("Valid Pipeline");
        pipeline.setProjectId("proj-1");

        Stage s1 = new Stage("s1", "p-1", "Checkout", StageType.CHECKOUT, 1, "echo checkout", 120, true, null);
        Stage s2 = new Stage("s2", "p-1", "Build", StageType.BUILD, 2, "echo build", 300, true, null);
        s2.getDependsOnStageIds().add("s1");

        pipeline.setStages(List.of(s1, s2));

        assertDoesNotThrow(() -> validator.validate(pipeline));
    }

    @Test
    @DisplayName("Pipeline with null or empty name fails")
    void testEmptyNameThrows() {
        Pipeline p = new Pipeline();
        p.setProjectId("proj-1");
        assertThrows(PipelineValidationException.class, () -> validator.validate(p));

        p.setName("   ");
        assertThrows(PipelineValidationException.class, () -> validator.validate(p));
    }

    @Test
    @DisplayName("Pipeline without stages fails")
    void testEmptyStagesThrows() {
        Pipeline p = new Pipeline();
        p.setName("No Stages Pipeline");
        p.setProjectId("proj-1");
        p.setStages(new ArrayList<>());
        assertThrows(PipelineValidationException.class, () -> validator.validate(p));
    }

    @Test
    @DisplayName("Stage depending on itself fails")
    void testSelfDependencyThrows() {
        Pipeline p = new Pipeline();
        p.setName("Self Dependent");
        p.setProjectId("proj-1");

        Stage s1 = new Stage("s1", "p-1", "Stage 1", StageType.BUILD, 1, "echo test", 100, true, null);
        s1.getDependsOnStageIds().add("s1");
        p.setStages(List.of(s1));

        assertThrows(PipelineValidationException.class, () -> validator.validate(p));
    }

    @Test
    @DisplayName("Stage depending on non-existent stage fails")
    void testMissingDependencyThrows() {
        Pipeline p = new Pipeline();
        p.setName("Missing Dep");
        p.setProjectId("proj-1");

        Stage s1 = new Stage("s1", "p-1", "Stage 1", StageType.BUILD, 1, "echo test", 100, true, null);
        s1.getDependsOnStageIds().add("non-existent-id");
        p.setStages(List.of(s1));

        assertThrows(PipelineValidationException.class, () -> validator.validate(p));
    }

    @Test
    @DisplayName("Cyclic dependency between stages fails")
    void testCyclicDependencyThrows() {
        Pipeline p = new Pipeline();
        p.setName("Cyclic Pipeline");
        p.setProjectId("proj-1");

        Stage s1 = new Stage("s1", "p-1", "Stage 1", StageType.BUILD, 1, "echo s1", 100, true, null);
        Stage s2 = new Stage("s2", "p-1", "Stage 2", StageType.TEST, 2, "echo s2", 100, true, null);

        s1.getDependsOnStageIds().add("s2");
        s2.getDependsOnStageIds().add("s1");

        p.setStages(List.of(s1, s2));

        assertThrows(PipelineValidationException.class, () -> validator.validate(p));
    }
}
