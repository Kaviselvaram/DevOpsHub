package com.devopshub.execution;

import com.devopshub.model.StageExecution;
import com.devopshub.model.StageExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Coordinates concurrent Fork/Join parallel stage executions with interruption propagation.
 */
public class ParallelStageCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(ParallelStageCoordinator.class);

    private final ExecutorService stageExecutor;

    public ParallelStageCoordinator(ExecutorService stageExecutor) {
        this.stageExecutor = stageExecutor;
    }

    public List<StageExecution> executeParallel(List<StageRunner> runners) {
        if (runners == null || runners.isEmpty()) {
            return new ArrayList<>();
        }

        logger.info("Coordinating parallel execution of {} stages...", runners.size());

        List<Future<StageExecution>> futures = new ArrayList<>();
        for (StageRunner runner : runners) {
            Future<StageExecution> future = stageExecutor.submit(runner::call);
            futures.add(future);
        }

        List<StageExecution> results = new ArrayList<>();
        for (int i = 0; i < runners.size(); i++) {
            Future<StageExecution> future = futures.get(i);
            StageRunner runner = runners.get(i);
            try {
                if (Thread.currentThread().isInterrupted()) {
                    for (Future<StageExecution> f : futures) {
                        f.cancel(true);
                    }
                }
                StageExecution res = future.get();
                results.add(res);
            } catch (InterruptedException | CancellationException e) {
                Thread.currentThread().interrupt();
                for (Future<StageExecution> f : futures) {
                    f.cancel(true);
                }
                StageExecution se = runner.getStageExecution();
                se.setStatus(StageExecutionStatus.CANCELLED);
                se.setExitCode(130);
                se.setErrorMessage("Stage execution cancelled");
                results.add(se);
            } catch (ExecutionException e) {
                logger.error("Stage execution threw exception: {}", e.getMessage());
                StageExecution se = runner.getStageExecution();
                se.setStatus(StageExecutionStatus.FAILED);
                se.setExitCode(1);
                se.setErrorMessage("Execution error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                results.add(se);
            }
        }

        return results;
    }
}
