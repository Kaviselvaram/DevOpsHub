package com.devopshub.model;

import java.util.HashMap;
import java.util.Map;

public class DashboardMetrics {
    private int totalProjects;
    private int totalPipelines;
    private int totalExecutions;
    private int successfulExecutions;
    private int failedExecutions;
    private int runningExecutions;
    private double successRate;
    private double avgDurationSeconds;
    private Map<String, Integer> failureCategoryCounts = new HashMap<>();

    public DashboardMetrics() {}

    public int getTotalProjects() { return totalProjects; }
    public void setTotalProjects(int totalProjects) { this.totalProjects = totalProjects; }

    public int getTotalPipelines() { return totalPipelines; }
    public void setTotalPipelines(int totalPipelines) { this.totalPipelines = totalPipelines; }

    public int getTotalExecutions() { return totalExecutions; }
    public void setTotalExecutions(int totalExecutions) { this.totalExecutions = totalExecutions; }

    public int getSuccessfulExecutions() { return successfulExecutions; }
    public void setSuccessfulExecutions(int successfulExecutions) { this.successfulExecutions = successfulExecutions; }

    public int getFailedExecutions() { return failedExecutions; }
    public void setFailedExecutions(int failedExecutions) { this.failedExecutions = failedExecutions; }

    public int getRunningExecutions() { return runningExecutions; }
    public void setRunningExecutions(int runningExecutions) { this.runningExecutions = runningExecutions; }

    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }

    public double getAvgDurationSeconds() { return avgDurationSeconds; }
    public void setAvgDurationSeconds(double avgDurationSeconds) { this.avgDurationSeconds = avgDurationSeconds; }

    public Map<String, Integer> getFailureCategoryCounts() { return failureCategoryCounts; }
    public void setFailureCategoryCounts(Map<String, Integer> failureCategoryCounts) { this.failureCategoryCounts = failureCategoryCounts; }
}
