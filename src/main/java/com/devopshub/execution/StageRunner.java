package com.devopshub.execution;

import com.devopshub.model.Stage;
import com.devopshub.model.StageExecution;
import com.devopshub.model.StageExecutionStatus;
import com.devopshub.repository.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Timestamp;
import java.util.concurrent.Callable;

/**
 * Callable task executing an individual pipeline stage.
 */
public class StageRunner implements Callable<StageExecution> {
    private static final Logger logger = LoggerFactory.getLogger(StageRunner.class);

    private final Stage stage;
    private final StageExecution stageExecution;
    private final ExecutionRepository executionRepository;
    private final LogCollector logCollector;
    private final File workspaceDir;

    public StageRunner(Stage stage, StageExecution stageExecution, ExecutionRepository executionRepository,
                       LogCollector logCollector, File workspaceDir) {
        this.stage = stage;
        this.stageExecution = stageExecution;
        this.executionRepository = executionRepository;
        this.logCollector = logCollector;
        this.workspaceDir = workspaceDir;
    }

    @Override
    public StageExecution call() {
        long startTime = System.currentTimeMillis();
        stageExecution.setStatus(StageExecutionStatus.RUNNING);
        stageExecution.setStartedAt(new Timestamp(startTime));
        executionRepository.updateStageExecution(stageExecution);

        logger.info("Starting stage '{}' (ID: {})", stage.getName(), stage.getId());

        ProcessExecutor executor = new ProcessExecutor(workspaceDir);
        int exitCode = executor.execute(stage.getCommand(), stageExecution.getId(), stage.getName(),
                stage.getTimeoutSeconds(), logCollector);

        long endTime = System.currentTimeMillis();
        double duration = (endTime - startTime) / 1000.0;

        stageExecution.setFinishedAt(new Timestamp(endTime));
        stageExecution.setDurationSeconds(Math.round(duration * 10.0) / 10.0);
        stageExecution.setExitCode(exitCode);

        if (exitCode == 0) {
            stageExecution.setStatus(StageExecutionStatus.SUCCESS);
            logger.info("Stage '{}' succeeded in {}s", stage.getName(), duration);
        } else if (exitCode == 130) {
            stageExecution.setStatus(StageExecutionStatus.CANCELLED);
            stageExecution.setErrorMessage("Execution cancelled by user");
            logger.warn("Stage '{}' was cancelled", stage.getName());
        } else {
            stageExecution.setStatus(StageExecutionStatus.FAILED);
            stageExecution.setErrorMessage("Process exited with code: " + exitCode);
            logger.warn("Stage '{}' failed with code {}", stage.getName(), exitCode);
        }

        executionRepository.updateStageExecution(stageExecution);
        return stageExecution;
    }

    public StageExecution getStageExecution() {
        return stageExecution;
    }
}
