package com.devopshub.scheduler;

import com.devopshub.model.Pipeline;
import com.devopshub.model.Stage;
import com.devopshub.model.StageType;
import com.devopshub.repository.PipelineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PipelineSchedulerService Real Recurring Trigger Tests")
class PipelineSchedulerServiceTest {

    private PipelineSchedulerService schedulerService;
    private Pipeline testPipeline;

    @BeforeEach
    void setUp() {
        com.devopshub.repository.ProjectRepository projectRepo = new com.devopshub.repository.ProjectRepository();
        com.devopshub.model.Project project = new com.devopshub.model.Project(
                "proj-sched-" + UUID.randomUUID(),
                "Scheduler Test Project " + UUID.randomUUID(),
                "Testing scheduler",
                "https://github.com/test/repo.git",
                "main",
                "Java",
                "ACTIVE"
        );
        projectRepo.save(project);

        PipelineRepository repo = new PipelineRepository();
        String pipeId = "sched-test-pipe-" + UUID.randomUUID();
        testPipeline = new Pipeline(
                pipeId,
                project.getId(),
                "Scheduler Unit Test Pipeline",
                "Tests recurring scheduling",
                "SEQUENTIAL",
                true
        );
        testPipeline.setStages(List.of(new Stage(UUID.randomUUID().toString(), pipeId, "Stage 1", StageType.BUILD, 1, "echo 'Scheduled run'", 60, true, null)));
        repo.save(testPipeline);

        schedulerService = PipelineSchedulerService.getInstance();
        schedulerService.setDependencies(null, new com.devopshub.service.PipelineService(repo, new com.devopshub.pipeline.PipelineValidator()));
    }

    @AfterEach
    void tearDown() {
        if (testPipeline != null) {
            schedulerService.unschedulePipeline(testPipeline.getId());
        }
    }

    @Test
    @DisplayName("Should successfully register and track scheduled pipeline job")
    void testScheduleAndUnschedule() {
        PipelineSchedulerService.ScheduledJob job = schedulerService.schedulePipeline(testPipeline.getId(), 3600);

        assertNotNull(job);
        assertEquals(testPipeline.getId(), job.getPipelineId());
        assertEquals(3600, job.getIntervalSeconds());
        assertTrue(job.isEnabled());
        assertNotNull(job.getNextRunAt());

        List<PipelineSchedulerService.ScheduledJob> jobs = schedulerService.getScheduledJobs();
        assertTrue(jobs.stream().anyMatch(j -> j.getPipelineId().equals(testPipeline.getId())));

        // Test unschedule
        schedulerService.unschedulePipeline(testPipeline.getId());
        List<PipelineSchedulerService.ScheduledJob> jobsAfter = schedulerService.getScheduledJobs();
        assertFalse(jobsAfter.stream().anyMatch(j -> j.getPipelineId().equals(testPipeline.getId())));
    }

    @Test
    @DisplayName("Should reject invalid interval or null pipeline id")
    void testInvalidScheduleArgs() {
        assertThrows(IllegalArgumentException.class, () -> schedulerService.schedulePipeline(null, 60));
        assertThrows(IllegalArgumentException.class, () -> schedulerService.schedulePipeline(testPipeline.getId(), 0));
        assertThrows(IllegalArgumentException.class, () -> schedulerService.schedulePipeline(testPipeline.getId(), -10));
    }

    @Test
    @DisplayName("Should durably persist schedules and reload from database")
    void testSchedulePersistenceAcrossReload() {
        schedulerService.schedulePipeline(testPipeline.getId(), 1800);

        com.devopshub.repository.ScheduleRepository scheduleRepo = new com.devopshub.repository.ScheduleRepository();
        var persisted = scheduleRepo.findByPipelineId(testPipeline.getId());
        assertTrue(persisted.isPresent(), "Schedule must be stored in database");
        assertEquals(1800, persisted.get().getIntervalSeconds());
        assertTrue(persisted.get().isEnabled());

        // Simulate reload
        schedulerService.loadPersistedSchedules();
        assertTrue(schedulerService.getScheduledJobs().stream().anyMatch(j -> j.getPipelineId().equals(testPipeline.getId())));
    }
}
