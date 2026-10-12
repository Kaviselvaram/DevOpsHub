package com.devopshub.execution;

import com.devopshub.model.StageExecution;
import com.devopshub.model.StageExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Coordinates concurrent Fork/Join parallel stage executions.
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

        List<CompletableFuture<StageExecution>> futures = new ArrayList<>();
        for (StageRunner runner : runners) {
            CompletableFuture<StageExecution> future = CompletableFuture.supplyAsync(runner::call, stageExecutor);
            futures.add(future);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            allOf.join();
        } catch (Exception e) {
            logger.error("Error during parallel stage execution join: {}", e.getMessage());
        }

        List<StageExecution> results = new ArrayList<>();
        for (CompletableFuture<StageExecution> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                logger.error("Could not obtain stage result: {}", e.getMessage());
            }
        }

        return results;
    }
}
