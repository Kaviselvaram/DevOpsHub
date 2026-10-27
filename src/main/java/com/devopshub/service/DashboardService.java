package com.devopshub.service;

import com.devopshub.model.DashboardMetrics;
import com.devopshub.model.Execution;
import com.devopshub.model.ExecutionStatus;
import com.devopshub.model.Project;
import com.devopshub.repository.DiagnosisRepository;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.PipelineRepository;
import com.devopshub.repository.ProjectRepository;

import java.util.List;

public class DashboardService {
    private final ProjectRepository projectRepository;
    private final PipelineRepository pipelineRepository;
    private final ExecutionRepository executionRepository;
    private final DiagnosisRepository diagnosisRepository;

    public DashboardService() {
        this.projectRepository = new ProjectRepository();
        this.pipelineRepository = new PipelineRepository();
        this.executionRepository = new ExecutionRepository();
        this.diagnosisRepository = new DiagnosisRepository();
    }

    public DashboardService(ProjectRepository pr, PipelineRepository plr, ExecutionRepository er, DiagnosisRepository dr) {
        this.projectRepository = pr;
        this.pipelineRepository = plr;
        this.executionRepository = er;
        this.diagnosisRepository = dr;
    }

    public DashboardMetrics getMetrics() {
        return getMetrics("ALL TIME (LIFETIME)");
    }

    public DashboardMetrics getMetrics(String timeWindow) {
        java.sql.Timestamp since = null;
        if (timeWindow != null) {
            String w = timeWindow.toUpperCase();
            if (w.contains("7 DAY") || w.contains("7D")) {
                since = new java.sql.Timestamp(System.currentTimeMillis() - (7L * 24 * 3600 * 1000));
            } else if (w.contains("30 DAY") || w.contains("30D")) {
                since = new java.sql.Timestamp(System.currentTimeMillis() - (30L * 24 * 3600 * 1000));
            }
        }

        DashboardMetrics metrics = new DashboardMetrics();

        int totalProjects = projectRepository.count();
        int totalPipelines = pipelineRepository.count();
        int totalExecutions = executionRepository.countAll(since);
        int successful = executionRepository.countByStatus(ExecutionStatus.SUCCESS, since);
        int failed = executionRepository.countByStatus(ExecutionStatus.FAILED, since);
        int running = executionRepository.countByStatus(ExecutionStatus.RUNNING, since);
        double avgDuration = executionRepository.getAverageDurationSeconds(since);

        metrics.setTotalProjects(totalProjects);
        metrics.setTotalPipelines(totalPipelines);
        metrics.setTotalExecutions(totalExecutions);
        metrics.setSuccessfulExecutions(successful);
        metrics.setFailedExecutions(failed);
        metrics.setRunningExecutions(running);
        metrics.setAvgDurationSeconds(Math.round(avgDuration * 10.0) / 10.0);

        int terminalTotal = successful + failed;
        double rate = terminalTotal > 0 ? ((double) successful / terminalTotal) * 100.0 : 0.0;
        metrics.setSuccessRate(Math.round(rate * 10.0) / 10.0);

        metrics.setFailureCategoryCounts(diagnosisRepository.countByCategory());

        return metrics;
    }

    public ExecutionRepository.StageOperationalStats getBuildStageStats() {
        return executionRepository.getStageStats(List.of(com.devopshub.model.StageType.BUILD, com.devopshub.model.StageType.CHECKOUT));
    }

    public ExecutionRepository.StageOperationalStats getTestStageStats() {
        return executionRepository.getStageStats(List.of(com.devopshub.model.StageType.TEST, com.devopshub.model.StageType.SECURITY));
    }

    public ExecutionRepository.StageOperationalStats getDeployStageStats() {
        return executionRepository.getStageStats(List.of(com.devopshub.model.StageType.PACKAGE, com.devopshub.model.StageType.DEPLOY));
    }

    public List<Execution> getRecentExecutions(int limit) {
        return executionRepository.findRecent(limit);
    }

    public List<Project> getRecentProjects() {
        return projectRepository.findAll();
    }

    public java.util.Map<String, Integer> getFrequentlyFailingPipelines() {
        List<Execution> runs = executionRepository.findAll();
        java.util.Map<String, Integer> failMap = new java.util.LinkedHashMap<>();
        for (Execution e : runs) {
            if (e.getStatus() == ExecutionStatus.FAILED) {
                String pName = e.getPipelineName() != null ? e.getPipelineName() : "Unknown Pipeline";
                failMap.put(pName, failMap.getOrDefault(pName, 0) + 1);
            }
        }
        return failMap;
    }
}
