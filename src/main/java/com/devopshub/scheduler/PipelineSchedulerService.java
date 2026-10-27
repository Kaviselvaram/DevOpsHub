package com.devopshub.scheduler;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.Execution;
import com.devopshub.model.Pipeline;
import com.devopshub.model.TriggerType;
import com.devopshub.config.DatabaseManager;
import com.devopshub.service.PipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;

/**
 * Genuine Automated Pipeline Scheduler.
 * Coordinates time-based and recurring interval pipeline executions with thread-safe controls.
 */

public class PipelineSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(PipelineSchedulerService.class);
    private static PipelineSchedulerService instance;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "DevOpsHub-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, ScheduledJob> scheduledJobs = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> activeTasks = new ConcurrentHashMap<>();
    private com.devopshub.repository.ScheduleRepository scheduleRepository;
    private ExecutionEngine executionEngine;
    private PipelineService pipelineService;

    private PipelineSchedulerService() {
        this.executionEngine = ExecutionEngine.getInstance();
        this.pipelineService = new PipelineService();
        this.scheduleRepository = new com.devopshub.repository.ScheduleRepository(DatabaseManager.getInstance());
        loadPersistedSchedules();
    }

    public static synchronized PipelineSchedulerService getInstance() {
        if (instance == null) {
            instance = new PipelineSchedulerService();
        }
        return instance;
    }

    public synchronized void setDependencies(ExecutionEngine engine, PipelineService service) {
        if (engine != null) this.executionEngine = engine;
        if (service != null) this.pipelineService = service;
        this.scheduleRepository = new com.devopshub.repository.ScheduleRepository(DatabaseManager.getInstance());
    }

    public synchronized void loadPersistedSchedules() {
        try {
            List<ScheduledJob> persisted = scheduleRepository.findAll();
            for (ScheduledJob job : persisted) {
                scheduledJobs.put(job.getPipelineId(), job);
                if (job.isEnabled()) {
                    attachSchedulerTask(job);
                }
            }
            logger.info("Loaded {} persisted pipeline schedules from database.", persisted.size());
        } catch (Exception e) {
            logger.warn("Failed to load persisted schedules on startup: {}", e.getMessage());
        }
    }

    public synchronized ScheduledJob schedulePipeline(String pipelineId, long intervalSeconds) {
        if (pipelineId == null || intervalSeconds <= 0) {
            throw new IllegalArgumentException("Invalid pipelineId or interval");
        }

        unschedulePipeline(pipelineId);

        Optional<Pipeline> pipeOpt = pipelineService.getPipelineById(pipelineId);
        String pipeName = pipeOpt.map(Pipeline::getName).orElse("Pipeline-" + pipelineId);

        ScheduledJob job = new ScheduledJob(pipelineId, pipeName, intervalSeconds, true);
        scheduledJobs.put(pipelineId, job);
        scheduleRepository.save(job);

        attachSchedulerTask(job);

        logger.info("Pipeline '{}' scheduled durably to run every {} seconds", pipeName, intervalSeconds);
        return job;
    }

    private void attachSchedulerTask(ScheduledJob job) {
        String pipelineId = job.getPipelineId();
        long intervalSeconds = job.getIntervalSeconds();

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!job.isEnabled()) return;
                logger.info("Executing scheduled trigger for pipeline '{}' (ID: {})", job.getPipelineName(), pipelineId);
                Optional<Pipeline> p = pipelineService.getPipelineById(pipelineId);
                if (p.isPresent()) {
                    Execution ex = executionEngine.startExecution(p.get(), TriggerType.SCHEDULED, null, null);
                    job.setLastExecutionId(ex.getId());
                    job.setLastRunAt(new Timestamp(System.currentTimeMillis()));
                    job.setExecutionCount(job.getExecutionCount() + 1);
                    job.setNextRunAt(new Timestamp(System.currentTimeMillis() + (intervalSeconds * 1000)));
                    scheduleRepository.update(job);
                    logger.info("Scheduled execution #{} started for pipeline '{}'", ex.getExecutionNumber(), p.get().getName());
                }
            } catch (Exception e) {
                logger.error("Error executing scheduled pipeline {}: {}", pipelineId, e.getMessage(), e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        activeTasks.put(pipelineId, task);
        job.setNextRunAt(new Timestamp(System.currentTimeMillis() + (intervalSeconds * 1000)));
    }

    public synchronized void unschedulePipeline(String pipelineId) {
        ScheduledFuture<?> task = activeTasks.remove(pipelineId);
        if (task != null) {
            task.cancel(false);
        }
        scheduledJobs.remove(pipelineId);
        scheduleRepository.delete(pipelineId);
        logger.info("Pipeline {} unscheduled and removed from persistence.", pipelineId);
    }

    public List<ScheduledJob> getScheduledJobs() {
        return new ArrayList<>(scheduledJobs.values());
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public static class ScheduledJob {
        private final String pipelineId;
        private final String pipelineName;
        private final long intervalSeconds;
        private boolean enabled;
        private Timestamp lastRunAt;
        private Timestamp nextRunAt;
        private String lastExecutionId;
        private int executionCount;

        public ScheduledJob(String pipelineId, String pipelineName, long intervalSeconds, boolean enabled) {
            this.pipelineId = pipelineId;
            this.pipelineName = pipelineName;
            this.intervalSeconds = intervalSeconds;
            this.enabled = enabled;
        }

        public String getPipelineId() { return pipelineId; }
        public String getPipelineName() { return pipelineName; }
        public long getIntervalSeconds() { return intervalSeconds; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Timestamp getLastRunAt() { return lastRunAt; }
        public void setLastRunAt(Timestamp lastRunAt) { this.lastRunAt = lastRunAt; }
        public Timestamp getNextRunAt() { return nextRunAt; }
        public void setNextRunAt(Timestamp nextRunAt) { this.nextRunAt = nextRunAt; }
        public String getLastExecutionId() { return lastExecutionId; }
        public void setLastExecutionId(String lastExecutionId) { this.lastExecutionId = lastExecutionId; }
        public int getExecutionCount() { return executionCount; }
        public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }
    }
}
