package com.devopshub.service;

import com.devopshub.exception.DevOpsHubException;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Stage;
import com.devopshub.model.StageType;
import com.devopshub.pipeline.PipelineValidator;
import com.devopshub.repository.PipelineRepository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PipelineService {
    private final PipelineRepository pipelineRepository;
    private final PipelineValidator validator;

    public PipelineService() {
        this.pipelineRepository = new PipelineRepository();
        this.validator = new PipelineValidator();
    }

    public PipelineService(PipelineRepository pipelineRepository, PipelineValidator validator) {
        this.pipelineRepository = pipelineRepository;
        this.validator = validator;
    }

    public List<Pipeline> getAllPipelines() {
        return pipelineRepository.findAll();
    }

    public List<Pipeline> getPipelinesByProject(String projectId) {
        return pipelineRepository.findByProjectId(projectId);
    }

    public Optional<Pipeline> getPipelineById(String id) {
        return pipelineRepository.findById(id);
    }

    public Pipeline createPipeline(String projectId, String name, String description, String executionMode, List<Stage> stages) {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(UUID.randomUUID().toString());
        pipeline.setProjectId(projectId);
        pipeline.setName(name);
        pipeline.setDescription(description != null ? description : "");
        pipeline.setExecutionMode(executionMode != null ? executionMode : "SEQUENTIAL");
        pipeline.setActive(true);
        pipeline.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        pipeline.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        if (stages != null && !stages.isEmpty()) {
            for (Stage s : stages) {
                s.setPipelineId(pipeline.getId());
            }
            pipeline.setStages(stages);
        } else {
            // Default 6 standard stages matching presentation (Slide 3)
            pipeline.setStages(createDefaultStages(pipeline.getId()));
        }

        validator.validate(pipeline);
        pipelineRepository.save(pipeline);
        return pipeline;
    }

    public Pipeline updatePipeline(String id, String name, String description, String executionMode, boolean active, List<Stage> stages) {
        Pipeline pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new DevOpsHubException("Pipeline not found with id: " + id));

        pipeline.setName(name);
        pipeline.setDescription(description);
        pipeline.setExecutionMode(executionMode);
        pipeline.setActive(active);
        pipeline.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        if (stages != null) {
            for (Stage s : stages) {
                s.setPipelineId(pipeline.getId());
            }
            pipeline.setStages(stages);
            validator.validate(pipeline);
        }

        pipelineRepository.update(pipeline);
        if (stages != null) {
            for (Stage s : stages) {
                pipelineRepository.saveStage(s);
            }
        }
        return pipeline;
    }

    public void deletePipeline(String id) {
        pipelineRepository.delete(id);
    }

    public Pipeline duplicatePipeline(String id) {
        return pipelineRepository.duplicate(id);
    }

    public List<Pipeline> getPipelinesByProjectId(String projectId) {
        return pipelineRepository.findByProjectId(projectId);
    }

    public List<Stage> createDefaultStages(String pipelineId) {
        List<Stage> stages = new ArrayList<>();

        Stage checkout = new Stage(UUID.randomUUID().toString(), pipelineId, "Checkout Source", StageType.CHECKOUT, 1,
                "echo \"Checking out git ref refs/heads/main...\"", 120, true, null);

        Stage build = new Stage(UUID.randomUUID().toString(), pipelineId, "Build Application", StageType.BUILD, 2,
                "echo \"Compiling source code with javac...\"", 300, true, null);
        build.getDependsOnStageIds().add(checkout.getId());

        Stage test = new Stage(UUID.randomUUID().toString(), pipelineId, "Unit Tests", StageType.TEST, 3,
                "echo \"Running test suites...\"", 300, true, "FORK_TEST");
        test.getDependsOnStageIds().add(build.getId());

        Stage security = new Stage(UUID.randomUUID().toString(), pipelineId, "SAST Security Scan", StageType.SECURITY, 4,
                "echo \"Analyzing codebase dependencies for CVEs...\"", 300, true, "FORK_TEST");
        security.getDependsOnStageIds().add(build.getId());

        Stage pkg = new Stage(UUID.randomUUID().toString(), pipelineId, "Package Binary", StageType.PACKAGE, 5,
                "echo \"Packaging distribution bundle...\"", 180, true, null);
        pkg.getDependsOnStageIds().add(test.getId());
        pkg.getDependsOnStageIds().add(security.getId());

        Stage deploy = new Stage(UUID.randomUUID().toString(), pipelineId, "Deploy Staging", StageType.DEPLOY, 6,
                "echo \"Deploying application to staging cluster...\"", 240, true, null);
        deploy.getDependsOnStageIds().add(pkg.getId());

        stages.add(checkout);
        stages.add(build);
        stages.add(test);
        stages.add(security);
        stages.add(pkg);
        stages.add(deploy);

        return stages;
    }
}
