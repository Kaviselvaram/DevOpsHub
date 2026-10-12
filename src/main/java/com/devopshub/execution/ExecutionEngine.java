package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.config.AppConfig;
import com.devopshub.model.*;
import com.devopshub.repository.DiagnosisRepository;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Technical Core: Orchestrates pipeline executions, stage DAG dependencies,
 * sequential/parallel fork-joins, log collection, and rule-based failure diagnosis.
 */
public class ExecutionEngine {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionEngine.class);

    private final ExecutionRepository executionRepository;
    private final LogRepository logRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final FailureRuleEngine failureRuleEngine;
    private final ExecutionThreadPool threadPool;
    private final File workspaceDir;

    // Track actively running tasks for cancellation
    private final Map<String, Future<?>> activeExecutions = new ConcurrentHashMap<>();
    private final Map<String, LogCollector> activeLogCollectors = new ConcurrentHashMap<>();

    public ExecutionEngine() {
        this(new ExecutionRepository(), new LogRepository(), new DiagnosisRepository(),
                new FailureRuleEngine(), ExecutionThreadPool.getInstance());
    }

    public ExecutionEngine(ExecutionRepository er, LogRepository lr, DiagnosisRepository dr,
                           FailureRuleEngine fre, ExecutionThreadPool tp) {
        this.executionRepository = er;
        this.logRepository = lr;
        this.diagnosisRepository = dr;
        this.failureRuleEngine = fre;
        this.threadPool = tp;
        this.workspaceDir = new File(AppConfig.getInstance().getWorkspaceDir());
        if (!this.workspaceDir.exists()) {
            this.workspaceDir.mkdirs();
        }
    }

    public Execution startExecution(Pipeline pipeline, TriggerType triggerType,
                                   Consumer<Execution> onUpdate, Consumer<ExecutionLog> onLog) {
        int execNum = executionRepository.getNextExecutionNumber(pipeline.getId());

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID().toString());
        execution.setPipelineId(pipeline.getId());
        execution.setProjectId(pipeline.getProjectId());
        execution.setExecutionNumber(execNum);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setTriggerType(triggerType != null ? triggerType : TriggerType.MANUAL);
        execution.setStartedAt(new Timestamp(System.currentTimeMillis()));
        execution.setPipelineName(pipeline.getName());

        // Save execution first so child stage_executions can reference it
        executionRepository.save(execution);

        // Prepare stage execution records
        List<StageExecution> stageExecs = new ArrayList<>();
        List<Stage> stages = pipeline.getStages() != null ? pipeline.getStages() : Collections.emptyList();
        for (Stage s : stages) {
            StageExecution se = new StageExecution(UUID.randomUUID().toString(), execution.getId(),
                    s.getId(), s.getName(), s.getStageType());
            stageExecs.add(se);
            executionRepository.saveStageExecution(se);
        }
        execution.setStageExecutions(stageExecs);

        // Prepare LogCollector
        LogCollector logCollector = new LogCollector(execution.getId(), logRepository);
        if (onLog != null) {
            logCollector.addListener(onLog);
        }
        activeLogCollectors.put(execution.getId(), logCollector);

        logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                String.format("Starting Pipeline '%s' (Build #%d)", pipeline.getName(), execNum));

        // Submit asynchronous execution task
        Future<?> task = threadPool.getPipelineExecutor().submit(() -> {
            try {
                runPipelineWorkflow(pipeline, execution, stageExecs, logCollector, onUpdate);
            } catch (Exception e) {
                logger.error("Pipeline execution encountered unexpected error: {}", e.getMessage(), e);
                execution.setStatus(ExecutionStatus.FAILED);
                executionRepository.update(execution);
            } finally {
                activeExecutions.remove(execution.getId());
                activeLogCollectors.remove(execution.getId());
                if (onUpdate != null) {
                    onUpdate.accept(execution);
                }
            }
        });

        activeExecutions.put(execution.getId(), task);
        if (onUpdate != null) {
            onUpdate.accept(execution);
        }

        return execution;
    }

    private void runPipelineWorkflow(Pipeline pipeline, Execution execution, List<StageExecution> stageExecs,
                                    LogCollector logCollector, Consumer<Execution> onUpdate) {
        long startTime = System.currentTimeMillis();
        boolean hasFailure = false;
        StageExecution failedStage = null;

        Map<String, StageExecution> stageExecMap = new HashMap<>();
        for (StageExecution se : stageExecs) {
            stageExecMap.put(se.getStageId(), se);
        }

        List<Stage> stages = pipeline.getStages();
        ParallelStageCoordinator parallelCoordinator = new ParallelStageCoordinator(threadPool.getStageExecutor());

        int i = 0;
        while (i < stages.size()) {
            if (Thread.currentThread().isInterrupted()) {
                execution.setStatus(ExecutionStatus.CANCELLED);
                break;
            }

            Stage currentStage = stages.get(i);
            StageExecution currentExec = stageExecMap.get(currentStage.getId());

            // Check if prerequisites succeeded
            boolean prereqsSatisfied = true;
            if (currentStage.getDependsOnStageIds() != null) {
                for (String depId : currentStage.getDependsOnStageIds()) {
                    StageExecution depExec = stageExecMap.get(depId);
                    if (depExec == null || depExec.getStatus() != StageExecutionStatus.SUCCESS) {
                        prereqsSatisfied = false;
                        break;
                    }
                }
            }

            if (!prereqsSatisfied || hasFailure) {
                currentExec.setStatus(StageExecutionStatus.SKIPPED);
                currentExec.setErrorMessage("Skipped: prerequisite stage failed or was not satisfied");
                executionRepository.updateStageExecution(currentExec);
                logCollector.append(currentExec.getId(), currentStage.getName(), LogLevel.WARN, LogStream.STDOUT,
                        "Stage skipped due to previous stage outcome.");
                if (onUpdate != null) onUpdate.accept(execution);
                i++;
                continue;
            }

            // Check if parallel fork group exists
            String group = currentStage.getParallelGroup();
            if (group != null && !group.trim().isEmpty()) {
                List<Stage> parallelGroupStages = new ArrayList<>();
                List<StageRunner> runners = new ArrayList<>();

                int j = i;
                while (j < stages.size() && group.equals(stages.get(j).getParallelGroup())) {
                    Stage s = stages.get(j);
                    StageExecution se = stageExecMap.get(s.getId());
                    parallelGroupStages.add(s);
                    runners.add(new StageRunner(s, se, executionRepository, logCollector, workspaceDir));
                    j++;
                }

                logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                        String.format("Forking %d stages in parallel group '%s'...", runners.size(), group));

                if (onUpdate != null) onUpdate.accept(execution);

                List<StageExecution> parallelResults = parallelCoordinator.executeParallel(runners);
                for (StageExecution res : parallelResults) {
                    if (res.getStatus() == StageExecutionStatus.FAILED) {
                        hasFailure = true;
                        if (failedStage == null) failedStage = res;
                    } else if (res.getStatus() == StageExecutionStatus.CANCELLED) {
                        execution.setStatus(ExecutionStatus.CANCELLED);
                    }
                }

                logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                        String.format("Joined parallel group '%s'.", group));

                i = j; // Advance past the group
            } else {
                // Sequential single stage
                StageRunner runner = new StageRunner(currentStage, currentExec, executionRepository, logCollector, workspaceDir);
                if (onUpdate != null) onUpdate.accept(execution);

                StageExecution res = runner.call();
                if (res.getStatus() == StageExecutionStatus.FAILED) {
                    hasFailure = true;
                    failedStage = res;
                } else if (res.getStatus() == StageExecutionStatus.CANCELLED) {
                    execution.setStatus(ExecutionStatus.CANCELLED);
                }
                i++;
            }

            if (onUpdate != null) onUpdate.accept(execution);
        }

        long endTime = System.currentTimeMillis();
        double totalDuration = (endTime - startTime) / 1000.0;
        execution.setFinishedAt(new Timestamp(endTime));
        execution.setDurationSeconds(Math.round(totalDuration * 10.0) / 10.0);

        if (execution.getStatus() != ExecutionStatus.CANCELLED) {
            execution.setStatus(hasFailure ? ExecutionStatus.FAILED : ExecutionStatus.SUCCESS);
        }

        // Trigger failure analysis if failed
        if (execution.getStatus() == ExecutionStatus.FAILED && failedStage != null) {
            logCollector.append(null, "Intelligence", LogLevel.INFO, LogStream.STDOUT,
                    "Triggering Rule-Based Failure Intelligence Engine...");

            List<ExecutionLog> logs = logRepository.findByExecutionId(execution.getId());
            FailureDiagnosis diagnosis = failureRuleEngine.analyze(failedStage, logs);
            if (diagnosis != null) {
                diagnosisRepository.save(diagnosis);
                execution.setFailureDiagnosis(diagnosis);
                logCollector.append(null, "Intelligence", LogLevel.INFO, LogStream.STDOUT,
                        String.format("Failure Identified: [%s] %s", diagnosis.getCategory().getDisplayName(), diagnosis.getTitle()));
                logCollector.append(null, "Intelligence", LogLevel.INFO, LogStream.STDOUT,
                        "Recommended Next Action: " + diagnosis.getSuggestedAction());
            }
        }

        logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                String.format("Pipeline execution finalized with status: %s (Total Duration: %.1fs)",
                        execution.getStatus(), totalDuration));

        logCollector.flush();
        executionRepository.update(execution);
    }

    public boolean cancelExecution(String executionId) {
        Future<?> task = activeExecutions.get(executionId);
        if (task != null) {
            task.cancel(true);
            LogCollector collector = activeLogCollectors.get(executionId);
            if (collector != null) {
                collector.append(null, "System", LogLevel.WARN, LogStream.STDERR, "Cancellation requested by user.");
                collector.flush();
            }
            return true;
        }
        return false;
    }

    public boolean isRunning(String executionId) {
        return activeExecutions.containsKey(executionId);
    }

    public LogCollector getActiveLogCollector(String executionId) {
        return activeLogCollectors.get(executionId);
    }
}
