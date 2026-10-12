package com.devopshub.execution;

import com.devopshub.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized, bounded thread pools for pipeline and stage execution.
 */
public class ExecutionThreadPool {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionThreadPool.class);
    private static ExecutionThreadPool instance;

    private final ExecutorService pipelineExecutor;
    private final ExecutorService stageExecutor;

    private ExecutionThreadPool() {
        AppConfig config = AppConfig.getInstance();
        int maxPipelines = config.getMaxConcurrentPipelines();
        int maxStages = maxPipelines * 4;

        this.pipelineExecutor = new ThreadPoolExecutor(
                maxPipelines,
                maxPipelines,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new NamedThreadFactory("DevOpsHub-Pipeline-")
        );

        this.stageExecutor = new ThreadPoolExecutor(
                maxStages,
                maxStages,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new NamedThreadFactory("DevOpsHub-Stage-")
        );

        logger.info("Initialized execution thread pools: Pipelines max={}, Stages max={}", maxPipelines, maxStages);
    }

    public static synchronized ExecutionThreadPool getInstance() {
        if (instance == null) {
            instance = new ExecutionThreadPool();
        }
        return instance;
    }

    public ExecutorService getPipelineExecutor() {
        return pipelineExecutor;
    }

    public ExecutorService getStageExecutor() {
        return stageExecutor;
    }

    public void shutdown() {
        logger.info("Shutting down execution thread pools...");
        pipelineExecutor.shutdown();
        stageExecutor.shutdown();
        try {
            if (!pipelineExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                pipelineExecutor.shutdownNow();
            }
            if (!stageExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                stageExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pipelineExecutor.shutdownNow();
            stageExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger count = new AtomicInteger(1);

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + count.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
