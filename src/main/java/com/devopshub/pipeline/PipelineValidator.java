package com.devopshub.pipeline;

import com.devopshub.exception.PipelineValidationException;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Stage;

import java.util.*;

/**
 * Validates CI/CD pipeline stage configurations and dependency DAGs.
 */
public class PipelineValidator {

    public void validate(Pipeline pipeline) {
        if (pipeline == null) {
            throw new PipelineValidationException("Pipeline cannot be null.");
        }
        if (pipeline.getName() == null || pipeline.getName().trim().isEmpty()) {
            throw new PipelineValidationException("Pipeline name is required.");
        }
        if (pipeline.getProjectId() == null || pipeline.getProjectId().trim().isEmpty()) {
            throw new PipelineValidationException("Project ID is required.");
        }

        List<Stage> stages = pipeline.getStages();
        if (stages == null || stages.isEmpty()) {
            throw new PipelineValidationException("Pipeline must contain at least one stage.");
        }

        Map<String, Stage> stageMap = new HashMap<>();
        for (Stage stage : stages) {
            if (stage.getName() == null || stage.getName().trim().isEmpty()) {
                throw new PipelineValidationException("Stage name cannot be empty.");
            }
            if (stage.getCommand() == null || stage.getCommand().trim().isEmpty()) {
                throw new PipelineValidationException("Stage '" + stage.getName() + "' must define an executable command.");
            }
            if (stage.getTimeoutSeconds() <= 0) {
                throw new PipelineValidationException("Stage '" + stage.getName() + "' timeout must be positive.");
            }
            stageMap.put(stage.getId(), stage);
        }

        // Validate dependencies exist
        for (Stage stage : stages) {
            if (stage.getDependsOnStageIds() != null) {
                for (String depId : stage.getDependsOnStageIds()) {
                    if (depId.equals(stage.getId())) {
                        throw new PipelineValidationException("Stage '" + stage.getName() + "' cannot depend on itself.");
                    }
                    if (!stageMap.containsKey(depId)) {
                        throw new PipelineValidationException("Stage '" + stage.getName() + "' references non-existent prerequisite stage: " + depId);
                    }
                }
            }
        }

        // Validate acyclic graph (Cycle Detection via DFS)
        checkAcyclic(stages);
    }

    private void checkAcyclic(List<Stage> stages) {
        Map<String, List<String>> adj = new HashMap<>();
        for (Stage s : stages) {
            adj.putIfAbsent(s.getId(), new ArrayList<>());
            if (s.getDependsOnStageIds() != null) {
                for (String dep : s.getDependsOnStageIds()) {
                    adj.putIfAbsent(dep, new ArrayList<>());
                    adj.get(dep).add(s.getId());
                }
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (Stage s : stages) {
            if (hasCycle(s.getId(), adj, visited, recStack)) {
                throw new PipelineValidationException("Cyclic dependency detected in pipeline stages involving stage '" + s.getName() + "'.");
            }
        }
    }

    private boolean hasCycle(String curr, Map<String, List<String>> adj, Set<String> visited, Set<String> recStack) {
        if (recStack.contains(curr)) {
            return true;
        }
        if (visited.contains(curr)) {
            return false;
        }

        visited.add(curr);
        recStack.add(curr);

        List<String> neighbors = adj.getOrDefault(curr, Collections.emptyList());
        for (String next : neighbors) {
            if (hasCycle(next, adj, visited, recStack)) {
                return true;
            }
        }

        recStack.remove(curr);
        return false;
    }
}

