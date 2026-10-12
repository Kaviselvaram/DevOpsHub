package com.devopshub.service;

import com.devopshub.model.DashboardMetrics;
import com.devopshub.model.Execution;
import com.devopshub.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService();
    }

    @Test
    @DisplayName("Dashboard calculates operational metrics correctly")
    void testMetricsCalculation() {
        DashboardMetrics metrics = dashboardService.getMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getTotalProjects() >= 0);
        assertTrue(metrics.getTotalPipelines() >= 0);
        assertTrue(metrics.getTotalExecutions() >= 0);
        assertTrue(metrics.getSuccessRate() >= 0.0 && metrics.getSuccessRate() <= 100.0);
        assertTrue(metrics.getAvgDurationSeconds() >= 0.0);
        assertNotNull(metrics.getFailureCategoryCounts());
    }

    @Test
    @DisplayName("Recent executions and projects feed are populated")
    void testRecentFeeds() {
        List<Execution> recentExecs = dashboardService.getRecentExecutions(5);
        assertNotNull(recentExecs);

        List<Project> recentProjects = dashboardService.getRecentProjects();
        assertNotNull(recentProjects);
        assertFalse(recentProjects.isEmpty(), "Seed projects should be loaded");
    }
}
