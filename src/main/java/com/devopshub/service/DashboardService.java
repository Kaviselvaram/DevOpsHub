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
        DashboardMetrics metrics = new DashboardMetrics();

        int totalProjects = projectRepository.count();
        int totalPipelines = pipelineRepository.count();
        int totalExecutions = executionRepository.countAll();
        int successful = executionRepository.countByStatus(ExecutionStatus.SUCCESS);
        int failed = executionRepository.countByStatus(ExecutionStatus.FAILED);
        int running = executionRepository.countByStatus(ExecutionStatus.RUNNING);
        double avgDuration = executionRepository.getAverageDurationSeconds();

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
