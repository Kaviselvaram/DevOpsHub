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
    private final java.util.Map<String, String> environmentVariables;
    private final com.devopshub.execution.isolation.ExecutionPolicy executionPolicy;

    public StageRunner(Stage stage, StageExecution stageExecution, ExecutionRepository executionRepository,
                       LogCollector logCollector, File workspaceDir) {
        this(stage, stageExecution, executionRepository, logCollector, workspaceDir, null, null);
    }

    public StageRunner(Stage stage, StageExecution stageExecution, ExecutionRepository executionRepository,
                       LogCollector logCollector, File workspaceDir, java.util.Map<String, String> environmentVariables) {
        this(stage, stageExecution, executionRepository, logCollector, workspaceDir, environmentVariables, null);
    }

    public StageRunner(Stage stage, StageExecution stageExecution, ExecutionRepository executionRepository,
                       LogCollector logCollector, File workspaceDir, java.util.Map<String, String> environmentVariables,
                       com.devopshub.execution.isolation.ExecutionPolicy executionPolicy) {
        this.stage = stage;
        this.stageExecution = stageExecution;
        this.executionRepository = executionRepository;
        this.logCollector = logCollector;
        this.workspaceDir = workspaceDir;
        this.environmentVariables = environmentVariables != null ? environmentVariables : new java.util.HashMap<>();
        this.executionPolicy = executionPolicy != null ? executionPolicy :
                new com.devopshub.execution.isolation.ExecutionPolicy(com.devopshub.execution.isolation.ExecutionTrustLevel.UNTRUSTED_EXTERNAL);
    }

    @Override
    public StageExecution call() {
        long startTime = System.currentTimeMillis();
        stageExecution.setStatus(StageExecutionStatus.RUNNING);
        stageExecution.setCommand(stage.getCommand());
        stageExecution.setStartedAt(new Timestamp(startTime));
        executionRepository.updateStageExecution(stageExecution);

        logger.info("Starting stage '{}' (ID: {}) with trust level {}", stage.getName(), stage.getId(), executionPolicy.getTrustLevel());

        ProcessExecutor executor = new ProcessExecutor(workspaceDir, executionPolicy);
        int exitCode = executor.execute(stage.getCommand(), stageExecution.getId(), stage.getName(),
                stage.getTimeoutSeconds(), logCollector, environmentVariables);

        // Verify build output if this is a build/packaging stage
        if (exitCode == 0 && stage.getCommand() != null && stage.getCommand().contains("build") && new File(workspaceDir, "package.json").exists()) {
            boolean isNext = new File(workspaceDir, "next.config.ts").exists() ||
                             new File(workspaceDir, "next.config.js").exists() ||
                             new File(workspaceDir, "next.config.mjs").exists();
            if (isNext) {
                File nextDir = new File(workspaceDir, ".next");
                if (!nextDir.exists() || !nextDir.isDirectory() || (nextDir.list() != null && nextDir.list().length == 0)) {
                    exitCode = 1;
                    logCollector.append(stageExecution.getId(), stage.getName(), com.devopshub.model.LogLevel.ERROR, com.devopshub.model.LogStream.STDERR,
                            "Build verification failed: Process exited with 0, but expected Next.js build output directory (.next) is missing.");
                }
            }
        }

        // Run Security Scanner if stage is SECURITY
        if (stage.getStageType() == com.devopshub.model.StageType.SECURITY) {
            com.devopshub.security.CompositeSecurityScanner securityScanner = new com.devopshub.security.CompositeSecurityScanner();
            com.devopshub.security.SecurityScanResult scanResult = securityScanner.scan(workspaceDir, stageExecution.getExecutionId(), stageExecution.getId());

            com.devopshub.repository.SecurityFindingRepository findingRepo = new com.devopshub.repository.SecurityFindingRepository();
            findingRepo.saveAll(scanResult.getFindings());

            logCollector.append(stageExecution.getId(), stage.getName(), com.devopshub.model.LogLevel.INFO, com.devopshub.model.LogStream.STDOUT,
                    "[SECURITY SCAN] " + scanResult.generateSummary());

            for (com.devopshub.security.SecurityFinding f : scanResult.getFindings()) {
                com.devopshub.model.LogLevel level = f.getSeverity() == com.devopshub.security.SecuritySeverity.CRITICAL ?
                        com.devopshub.model.LogLevel.ERROR : com.devopshub.model.LogLevel.WARN;
                com.devopshub.model.LogStream stream = f.getSeverity() == com.devopshub.security.SecuritySeverity.CRITICAL ?
                        com.devopshub.model.LogStream.STDERR : com.devopshub.model.LogStream.STDOUT;
                logCollector.append(stageExecution.getId(), stage.getName(), level, stream,
                        String.format("[%s] %s (%s): %s in %s:%s - %s",
                                f.getSeverity(), f.getTitle(), f.getRuleId(), f.getCategory(),
                                f.getFilePath(), f.getLineNumber(), f.getRemediation()));
            }

            if (scanResult.getCountBySeverity(com.devopshub.security.SecuritySeverity.CRITICAL) > 0) {
                exitCode = 1;
                logCollector.append(stageExecution.getId(), stage.getName(), com.devopshub.model.LogLevel.ERROR, com.devopshub.model.LogStream.STDERR,
                        "Security gate failure: Critical security findings detected. Pipeline aborted.");
            }
        }

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

        boolean interrupted = Thread.interrupted();
        try {
            executionRepository.updateStageExecution(stageExecution);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return stageExecution;
    }

    public StageExecution getStageExecution() {
        return stageExecution;
    }
}
