package com.devopshub.execution;

import com.devopshub.analysis.FailureRuleEngine;
import com.devopshub.config.AppConfig;
import com.devopshub.deployment.DeploymentService;
import com.devopshub.execution.event.ExecutionEvent;
import com.devopshub.execution.event.ExecutionEventBus;
import com.devopshub.execution.event.ExecutionEventType;
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
 * sequential/parallel fork-joins, log collection, fine-grained lifecycle event publishing,
 * and rule-based failure diagnosis.
 */
public class ExecutionEngine {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionEngine.class);

    private final ExecutionRepository executionRepository;
    private final LogRepository logRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final FailureRuleEngine failureRuleEngine;
    private final ExecutionThreadPool threadPool;
    private final File workspaceDir;
    private final com.devopshub.repository.ProjectRepository projectRepository;
    private final com.devopshub.git.GitService gitService;
    private final DeploymentService deploymentService;
    private final ExecutionEventBus eventBus;

    // Track actively running tasks for cancellation and live status
    private static final Map<String, Future<?>> activeExecutions = new ConcurrentHashMap<>();
    private static final Map<String, Execution> activeExecutionEntities = new ConcurrentHashMap<>();
    private static final Map<String, LogCollector> activeLogCollectors = new ConcurrentHashMap<>();
    private static final Map<String, List<Consumer<Execution>>> executionListeners = new ConcurrentHashMap<>();

    private static volatile ExecutionEngine instance;

    public static ExecutionEngine getInstance() {
        if (instance == null) {
            synchronized (ExecutionEngine.class) {
                if (instance == null) {
                    instance = new ExecutionEngine();
                }
            }
        }
        return instance;
    }

    public ExecutionEngine() {
        this(new ExecutionRepository(), new LogRepository(), new DiagnosisRepository(),
                new FailureRuleEngine(), ExecutionThreadPool.getInstance());
    }

    public ExecutionEngine(ExecutionRepository er, LogRepository lr, DiagnosisRepository dr,
                           FailureRuleEngine fre, ExecutionThreadPool tp) {
        this(er, lr, dr, fre, tp, new com.devopshub.repository.ProjectRepository(), new com.devopshub.git.GitService(), new DeploymentService());
    }

    public ExecutionEngine(ExecutionRepository er, LogRepository lr, DiagnosisRepository dr,
                           FailureRuleEngine fre, ExecutionThreadPool tp,
                           com.devopshub.repository.ProjectRepository pr, com.devopshub.git.GitService gs) {
        this(er, lr, dr, fre, tp, pr, gs, new DeploymentService());
    }

    public ExecutionEngine(ExecutionRepository er, LogRepository lr, DiagnosisRepository dr,
                           FailureRuleEngine fre, ExecutionThreadPool tp,
                           com.devopshub.repository.ProjectRepository pr, com.devopshub.git.GitService gs,
                           DeploymentService ds) {
        this.executionRepository = er;
        this.logRepository = lr;
        this.diagnosisRepository = dr;
        this.failureRuleEngine = fre != null ? fre : new FailureRuleEngine();
        this.threadPool = tp;
        this.projectRepository = pr != null ? pr : new com.devopshub.repository.ProjectRepository();
        this.gitService = gs != null ? gs : new com.devopshub.git.GitService();
        this.deploymentService = ds != null ? ds : new DeploymentService();
        this.eventBus = ExecutionEventBus.getInstance();
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
        execution.setCurrentPhase("Queued");
        execution.setTriggerType(triggerType != null ? triggerType : TriggerType.MANUAL);
        execution.setStartedAt(new Timestamp(System.currentTimeMillis()));
        execution.setPipelineName(pipeline.getName());

        // Save execution first so child stage_executions can reference it
        executionRepository.save(execution);

        // Emit queued lifecycle event
        eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.EXECUTION_QUEUED,
                String.format("Pipeline '%s' (Build #%d) queued for execution", pipeline.getName(), execNum));

        // Prepare stage execution records
        List<StageExecution> stageExecs = new ArrayList<>();
        List<Stage> stages = pipeline.getStages() != null ? pipeline.getStages() : Collections.emptyList();
        for (Stage s : stages) {
            StageExecution se = new StageExecution(UUID.randomUUID().toString(), execution.getId(),
                    s.getId(), s.getName(), s.getStageType());
            se.setCommand(s.getCommand());
            se.setExitCodeSet(false);
            stageExecs.add(se);
            executionRepository.saveStageExecution(se);

            eventBus.publish(execution.getId(), pipeline.getId(), se.getId(), s.getName(),
                    s.getStageOrder(), ExecutionEventType.STAGE_QUEUED, "NONE -> QUEUED",
                    String.format("Stage '%s' queued", s.getName()), null, null, true);
        }
        execution.setStageExecutions(stageExecs);

        // Prepare LogCollector
        LogCollector logCollector = new LogCollector(execution.getId(), logRepository);
        if (onLog != null) {
            logCollector.addListener(onLog);
        }
        activeLogCollectors.put(execution.getId(), logCollector);
        activeExecutionEntities.put(execution.getId(), execution);

        logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                String.format("Starting Pipeline '%s' (Build #%d) [Trigger: %s]", pipeline.getName(), execNum, execution.getTriggerType()));

        // Submit asynchronous execution task
        Future<?> task = threadPool.getPipelineExecutor().submit(() -> {
            try {
                runPipelineWorkflow(pipeline, execution, stageExecs, logCollector, onUpdate);
            } catch (Exception e) {
                Thread.interrupted(); // Clear interrupt flag so database cleanup can proceed
                logger.error("Pipeline execution encountered unexpected error: {}", e.getMessage(), e);
                if (execution.getStatus() != ExecutionStatus.CANCELLED) {
                    execution.setStatus(ExecutionStatus.FAILED);
                }
                execution.setCurrentPhase("Encountered unexpected engine error");
                logCollector.append(null, "System", LogLevel.ERROR, LogStream.STDERR,
                        "Unexpected engine execution error: " + e.getMessage());
                logCollector.flush();
                executionRepository.update(execution);
                eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.EXECUTION_FAILED,
                        "Pipeline failed unexpectedly: " + e.getMessage());
            } finally {
                activeExecutions.remove(execution.getId());
                activeExecutionEntities.remove(execution.getId());
                activeLogCollectors.remove(execution.getId());
                if (onUpdate != null) {
                    onUpdate.accept(execution);
                }
                notifyExecutionUpdate(execution);
            }
        });

        activeExecutions.put(execution.getId(), task);
        if (onUpdate != null) {
            onUpdate.accept(execution);
        }
        notifyExecutionUpdate(execution);

        return execution;
    }

    public void addExecutionListener(String executionId, Consumer<Execution> listener) {
        if (executionId != null && listener != null) {
            executionListeners.computeIfAbsent(executionId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(listener);
        }
    }

    public void removeExecutionListener(String executionId, Consumer<Execution> listener) {
        if (executionId != null && listener != null) {
            List<Consumer<Execution>> list = executionListeners.get(executionId);
            if (list != null) {
                list.remove(listener);
            }
        }
    }

    public void notifyExecutionUpdate(Execution execution) {
        if (execution == null || execution.getId() == null) return;
        List<Consumer<Execution>> list = executionListeners.get(execution.getId());
        if (list != null) {
            for (Consumer<Execution> listener : list) {
                try {
                    listener.accept(execution);
                } catch (Exception ignored) {}
            }
        }
    }

    private void runPipelineWorkflow(Pipeline pipeline, Execution execution, List<StageExecution> stageExecs,
                                    LogCollector logCollector, Consumer<Execution> onUpdate) {
        long startTime = System.currentTimeMillis();
        boolean hasFailure = false;
        StageExecution failedStage = null;

        execution.setCurrentPhase("Preparing workspace and evaluating policy");
        eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.EXECUTION_STARTED,
                "Pipeline execution accepted on worker thread");

        Project project = null;
        if (pipeline.getProjectId() != null) {
            project = projectRepository.findById(pipeline.getProjectId()).orElse(null);
        }

        File effectiveDir = workspaceDir;
        if (project != null && project.getRepositoryUrl() != null && !project.getRepositoryUrl().trim().isEmpty()) {
            String repoUrl = project.getRepositoryUrl().trim();
            File localDir = new File(repoUrl);
            if (localDir.exists() && localDir.isDirectory()) {
                effectiveDir = localDir;
            } else if (gitService.isValidRepositoryUrl(repoUrl)) {
                effectiveDir = gitService.getProjectWorkspace(project.getId());
            }
        }

        if (!effectiveDir.exists()) {
            effectiveDir.mkdirs();
        }

        // Security boundary and execution isolation policy (SEC-01)
        com.devopshub.execution.isolation.ExecutionTrustLevel trustLevel =
                com.devopshub.execution.isolation.ExecutionIsolationService.getInstance().resolveTrustLevel(project, execution.getTriggerType());
        com.devopshub.execution.isolation.ExecutionPolicy executionPolicy =
                com.devopshub.execution.isolation.ExecutionIsolationService.getInstance().resolvePolicy(trustLevel);

        if (trustLevel == com.devopshub.execution.isolation.ExecutionTrustLevel.TRUSTED_LOCAL) {
            execution.setBackendDescription("Host Execution (Restricted)");
        } else {
            execution.setBackendDescription("Docker Container Isolation (Required)");
        }

        if (!executionPolicy.canExecute()) {
            String rejection = executionPolicy.getRejectionReason();
            logger.warn("Pipeline execution blocked by security policy: {}", rejection);

            execution.setStatus(ExecutionStatus.BLOCKED);
            execution.setCurrentPhase("Blocked by isolation security policy");
            execution.setBackendDescription("Blocked: Container Isolation Unavailable");

            logCollector.append(null, "Security", LogLevel.ERROR, LogStream.STDERR, rejection);

            eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.EXECUTION_BLOCKED, rejection);

            for (StageExecution se : stageExecs) {
                se.setStatus(StageExecutionStatus.SKIPPED);
                se.setErrorMessage("Skipped: Execution blocked by isolation security policy");
                se.setExitCodeSet(false);
                executionRepository.updateStageExecution(se);

                eventBus.publish(execution.getId(), pipeline.getId(), se.getId(), se.getStageName(),
                        0, ExecutionEventType.STAGE_SKIPPED, "QUEUED -> SKIPPED",
                        "Skipped: Execution blocked by isolation security policy (SEC-01)", null, null, true);
            }

            long now = System.currentTimeMillis();
            double duration = Math.max(0.1, Math.round((now - execution.getStartedAt().getTime()) / 100.0) / 10.0);
            execution.setFinishedAt(new Timestamp(now));
            execution.setDurationSeconds(duration);

            // Generate authentic evidence-based security diagnosis
            FailureDiagnosis diag = failureRuleEngine.createSecurityRejectionDiagnosis(execution.getId(), rejection);
            if (diagnosisRepository != null) {
                diagnosisRepository.save(diag);
            }
            execution.setFailureDiagnosis(diag);

            eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.DIAGNOSIS_CREATED,
                    "Generated Security Isolation Policy diagnosis (RULE_SEC_ISOLATION_01)");

            // Crucial: flush logs immediately so UI and database receive them
            logCollector.flush();
            executionRepository.update(execution);

            if (onUpdate != null) onUpdate.accept(execution);
            notifyExecutionUpdate(execution);
            return;
        }

        // Workspace Preparation Event
        eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.WORKSPACE_PREPARATION_STARTED,
                "Preparing workspace directory: " + effectiveDir.getAbsolutePath());

        // Standard CI/CD environment variables
        Map<String, String> env = new HashMap<>();
        env.put("DEVOPSHUB", "true");
        env.put("DEVOPSHUB_PIPELINE", pipeline.getName());
        env.put("DEVOPSHUB_BUILD_NUMBER", String.valueOf(execution.getExecutionNumber()));
        env.put("DEVOPSHUB_WORKSPACE", effectiveDir.getAbsolutePath());
        if (project != null) {
            env.put("DEVOPSHUB_PROJECT_NAME", project.getName());
            env.put("DEVOPSHUB_BRANCH", project.getDefaultBranch() != null ? project.getDefaultBranch() : "main");
        }

        logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                String.format("Working directory for pipeline execution: %s", effectiveDir.getAbsolutePath()));

        // Ensure workspace is initialized if project has a Git repository and first stage is not CHECKOUT
        File gitDir = new File(effectiveDir, ".git");
        boolean firstStageIsCheckout = pipeline.getStages() != null && !pipeline.getStages().isEmpty()
                && pipeline.getStages().get(0).getStageType() == StageType.CHECKOUT;
        boolean isSameLocalDir = false;
        if (project != null && project.getRepositoryUrl() != null) {
            try {
                File repoLocal = new File(project.getRepositoryUrl());
                if (repoLocal.exists() && repoLocal.getCanonicalPath().equals(effectiveDir.getCanonicalPath())) {
                    isSameLocalDir = true;
                }
            } catch (Exception ignored) {}
        }

        if (!gitDir.exists() && !firstStageIsCheckout && project != null && !isSameLocalDir && gitService.isValidRepositoryUrl(project.getRepositoryUrl())) {
            String branch = project.getDefaultBranch() != null ? project.getDefaultBranch() : "main";
            logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                    String.format("Initializing workspace from %s (branch: %s)...", com.devopshub.util.SecretMasker.mask(project.getRepositoryUrl()), branch));

            eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.GIT_OPERATION_STARTED,
                    "Pre-checkout repository initialization from " + com.devopshub.util.SecretMasker.mask(project.getRepositoryUrl()));

            var gitRes = gitService.cloneOrFetch(project.getRepositoryUrl(), branch, effectiveDir);
            if (gitRes.getExitCode() == 0) {
                String commit = gitService.getCurrentCommitHash(effectiveDir);
                if (commit != null && !commit.trim().isEmpty()) {
                    execution.setCommitHash(commit);
                    eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.COMMIT_RESOLVED,
                            "Commit resolved: " + commit);
                }
                logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                        "Workspace checkout successful. Verified commit: " + (commit != null ? commit : "unknown"));

                eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.GIT_OPERATION_COMPLETED,
                        "Workspace checkout successful (exit code 0)");

                executionRepository.update(execution);
            } else {
                logCollector.append(null, "System", LogLevel.ERROR, LogStream.STDERR,
                        "Workspace initialization failed: " + gitRes.getStderr());

                eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.GIT_OPERATION_COMPLETED,
                        "Workspace initialization failed with exit code " + gitRes.getExitCode() + ": " + gitRes.getStderr());

                execution.setStatus(ExecutionStatus.FAILED);
                execution.setCurrentPhase("Failed: Workspace initialization failed");

                for (StageExecution se : stageExecs) {
                    se.setStatus(StageExecutionStatus.SKIPPED);
                    se.setErrorMessage("Skipped: workspace initialization failed: " + gitRes.getStderr());
                    se.setExitCodeSet(false);
                    executionRepository.updateStageExecution(se);

                    eventBus.publish(execution.getId(), pipeline.getId(), se.getId(), se.getStageName(),
                            0, ExecutionEventType.STAGE_SKIPPED, "QUEUED -> SKIPPED",
                            "Skipped: Workspace initialization failed: " + gitRes.getStderr(), null, null, true);
                }

                long now = System.currentTimeMillis();
                execution.setFinishedAt(new Timestamp(now));
                execution.setDurationSeconds(Math.max(0.1, Math.round((now - execution.getStartedAt().getTime()) / 100.0) / 10.0));

                logCollector.flush();
                List<ExecutionLog> logs = logRepository.findByExecutionId(execution.getId());
                if (!stageExecs.isEmpty()) {
                    StageExecution dummyFirst = stageExecs.get(0);
                    dummyFirst.setErrorMessage("Git checkout failed: " + gitRes.getStderr());
                    dummyFirst.setExitCode(gitRes.getExitCode());
                    FailureDiagnosis diag = failureRuleEngine.analyze(dummyFirst, logs);
                    if (diag != null) {
                        if (diagnosisRepository != null) diagnosisRepository.save(diag);
                        execution.setFailureDiagnosis(diag);
                        eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.DIAGNOSIS_CREATED,
                                "Generated Git Checkout Failure diagnosis");
                    }
                }

                executionRepository.update(execution);
                eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.EXECUTION_FAILED,
                        "Pipeline failed during workspace checkout initialization");

                if (onUpdate != null) onUpdate.accept(execution);
                notifyExecutionUpdate(execution);
                return;
            }
        } else if (gitDir.exists()) {
            String commit = gitService.getCurrentCommitHash(effectiveDir);
            if (commit != null && !commit.trim().isEmpty()) {
                execution.setCommitHash(commit);
                logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                        "Resolved repository commit: " + commit);
                eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.COMMIT_RESOLVED,
                        "Commit resolved: " + commit);
                executionRepository.update(execution);
            }
        }

        eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.WORKSPACE_PREPARATION_COMPLETED,
                "Workspace initialized and ready for stage execution");

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
                execution.setCurrentPhase("Cancelled by user");
                break;
            }

            Stage currentStage = stages.get(i);
            StageExecution currentExec = stageExecMap.get(currentStage.getId());

            // Check if prerequisites succeeded
            boolean prereqsSatisfied = true;
            String failedPrereqName = null;
            if (currentStage.getDependsOnStageIds() != null) {
                for (String depId : currentStage.getDependsOnStageIds()) {
                    StageExecution depExec = stageExecMap.get(depId);
                    if (depExec == null || depExec.getStatus() != StageExecutionStatus.SUCCESS) {
                        prereqsSatisfied = false;
                        failedPrereqName = depExec != null ? depExec.getStageName() : depId;
                        break;
                    }
                }
            }

            if (!prereqsSatisfied || hasFailure) {
                String skipReason = hasFailure ?
                        "Skipped: Upstream stage execution failed" :
                        "Skipped: Prerequisite stage '" + failedPrereqName + "' was not satisfied";
                currentExec.setStatus(StageExecutionStatus.SKIPPED);
                currentExec.setErrorMessage(skipReason);
                currentExec.setExitCodeSet(false);
                executionRepository.updateStageExecution(currentExec);

                logCollector.append(currentExec.getId(), currentStage.getName(), LogLevel.WARN, LogStream.STDOUT,
                        skipReason);

                eventBus.publish(execution.getId(), pipeline.getId(), currentExec.getId(), currentStage.getName(),
                        currentStage.getStageOrder(), ExecutionEventType.STAGE_SKIPPED, "QUEUED -> SKIPPED",
                        skipReason, null, null, true);

                if (onUpdate != null) onUpdate.accept(execution);
                notifyExecutionUpdate(execution);
                i++;
                continue;
            }

            currentExec.setStatus(StageExecutionStatus.RUNNING);
            currentExec.setStartedAt(new Timestamp(System.currentTimeMillis()));
            executionRepository.updateStageExecution(currentExec);

            execution.setCurrentPhase(String.format("Running Stage %d of %d: %s", (i + 1), stages.size(), currentStage.getName()));
            executionRepository.update(execution);
            notifyExecutionUpdate(execution);

            eventBus.publish(execution.getId(), pipeline.getId(), currentExec.getId(), currentStage.getName(),
                    currentStage.getStageOrder(), ExecutionEventType.STAGE_STARTED, "QUEUED -> RUNNING",
                    String.format("Started stage %d: %s", (i + 1), currentStage.getName()), null, null, true);

            // Handle real checkout if stage is CHECKOUT
            if (currentStage.getStageType() == StageType.CHECKOUT && project != null && gitService.isValidRepositoryUrl(project.getRepositoryUrl())) {
                String cmd = currentStage.getCommand();
                String branch = project.getDefaultBranch() != null ? project.getDefaultBranch() : "main";
                currentExec.setCommand(cmd != null && !cmd.trim().isEmpty() ? cmd : "git clone --branch " + branch + " " + project.getRepositoryUrl());
                currentExec.setStatus(StageExecutionStatus.RUNNING);
                currentExec.setStartedAt(new Timestamp(System.currentTimeMillis()));
                executionRepository.updateStageExecution(currentExec);
                notifyExecutionUpdate(execution);

                eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.GIT_OPERATION_STARTED,
                        "Performing Git synchronization for ref: " + branch);

                logCollector.append(currentExec.getId(), currentStage.getName(), LogLevel.INFO, LogStream.STDOUT,
                        "Performing real Git synchronization for ref: " + branch);
                var gitRes = gitService.cloneOrFetch(project.getRepositoryUrl(), branch, effectiveDir);
                if (gitRes.getExitCode() == 0) {
                    String commit = gitService.getCurrentCommitHash(effectiveDir);
                    execution.setCommitHash(commit);
                    logCollector.append(currentExec.getId(), currentStage.getName(), LogLevel.INFO, LogStream.STDOUT,
                            "Git checkout successful. Verified commit: " + commit);

                    eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.GIT_OPERATION_COMPLETED,
                            "Git checkout successful");
                    eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.COMMIT_RESOLVED,
                            "Commit resolved: " + commit);

                    boolean isStandardCheckout = cmd == null || cmd.trim().isEmpty()
                            || cmd.trim().equalsIgnoreCase("checkout")
                            || cmd.trim().equalsIgnoreCase("git checkout")
                            || cmd.trim().equalsIgnoreCase("git status")
                            || cmd.trim().toLowerCase().startsWith("git checkout")
                            || cmd.trim().toLowerCase().startsWith("git clone")
                            || cmd.trim().toLowerCase().startsWith("echo");

                    if (isStandardCheckout) {
                        currentExec.setStatus(StageExecutionStatus.SUCCESS);
                        currentExec.setExitCode(0);
                        currentExec.setDurationSeconds(gitRes.getDurationSeconds());
                        currentExec.setFinishedAt(new Timestamp(System.currentTimeMillis()));
                        executionRepository.updateStageExecution(currentExec);
                        executionRepository.update(execution);

                        eventBus.publish(execution.getId(), pipeline.getId(), currentExec.getId(), currentStage.getName(),
                                currentStage.getStageOrder(), ExecutionEventType.STAGE_COMPLETED, "RUNNING -> SUCCESS",
                                "Checkout completed with exit code 0", gitRes.getDurationSeconds(), 0, true);

                        if (onUpdate != null) onUpdate.accept(execution);
                        notifyExecutionUpdate(execution);
                        i++;
                        continue;
                    }
                    // Otherwise, proceed to run the custom checkout script/command below
                } else {
                    currentExec.setStatus(StageExecutionStatus.FAILED);
                    currentExec.setExitCode(gitRes.getExitCode());
                    currentExec.setErrorMessage("Git checkout failed: " + gitRes.getStderr());
                    currentExec.setFinishedAt(new Timestamp(System.currentTimeMillis()));

                    logCollector.append(currentExec.getId(), currentStage.getName(), LogLevel.ERROR, LogStream.STDERR,
                            "Git checkout failed with exit code " + gitRes.getExitCode() + ": " + gitRes.getStderr());
                    executionRepository.updateStageExecution(currentExec);
                    executionRepository.update(execution);
                    hasFailure = true;
                    failedStage = currentExec;

                    eventBus.publish(execution.getId(), pipeline.getId(), currentExec.getId(), currentStage.getName(),
                            currentStage.getStageOrder(), ExecutionEventType.STAGE_FAILED, "RUNNING -> FAILED",
                            "Git checkout failed with exit code " + gitRes.getExitCode(), gitRes.getDurationSeconds(), gitRes.getExitCode(), true);

                    if (onUpdate != null) onUpdate.accept(execution);
                    notifyExecutionUpdate(execution);
                    i++;
                    continue;
                }
            }

            // Handle real deployment if stage is DEPLOY and command is empty or auto-deploy
            if (currentStage.getStageType() == StageType.DEPLOY) {
                String cmd = currentStage.getCommand();
                if (cmd == null || cmd.trim().isEmpty() || cmd.trim().equalsIgnoreCase("deploy") || cmd.trim().equalsIgnoreCase("deploy artifact")) {
                    currentExec.setStatus(StageExecutionStatus.RUNNING);
                    currentExec.setStartedAt(new Timestamp(System.currentTimeMillis()));
                    executionRepository.updateStageExecution(currentExec);
                    notifyExecutionUpdate(execution);

                    File deployTarget = new File(effectiveDir, "deployments");
                    File artifact = findDeploymentArtifact(effectiveDir);
                    if (artifact == null) {
                        currentExec.setStatus(StageExecutionStatus.FAILED);
                        currentExec.setExitCode(1);
                        currentExec.setErrorMessage("Deployment failed: No built artifact (.jar, .war, .zip, .tar.gz) found in target/ or dist/ directory.");
                        currentExec.setFinishedAt(new Timestamp(System.currentTimeMillis()));

                        logCollector.append(currentExec.getId(), currentStage.getName(), LogLevel.ERROR, LogStream.STDERR,
                                "Deployment failed: No built artifact found to deploy. Verify that build and package stages produced an artifact.");
                        executionRepository.updateStageExecution(currentExec);
                        executionRepository.update(execution);
                        hasFailure = true;
                        failedStage = currentExec;

                        eventBus.publish(execution.getId(), pipeline.getId(), currentExec.getId(), currentStage.getName(),
                                currentStage.getStageOrder(), ExecutionEventType.STAGE_FAILED, "RUNNING -> FAILED",
                                "Deployment failed: No built artifact found", null, 1, true);

                        if (onUpdate != null) onUpdate.accept(execution);
                        notifyExecutionUpdate(execution);
                        i++;
                        continue;
                    }

                    var depResult = deploymentService.deployArtifact(artifact, deployTarget);
                    if (depResult.isSuccess()) {
                        currentExec.setStatus(StageExecutionStatus.SUCCESS);
                        currentExec.setExitCode(0);
                        currentExec.setDurationSeconds(depResult.getDurationSeconds());
                        currentExec.setFinishedAt(new Timestamp(System.currentTimeMillis()));
                        logCollector.append(currentExec.getId(), currentStage.getName(), LogLevel.INFO, LogStream.STDOUT,
                                String.format("Deployment verified: %s (Size: %dB, SHA-256: %s)",
                                        depResult.getTargetPath(), depResult.getArtifactSizeBytes(), depResult.getSha256Checksum()));
                        executionRepository.updateStageExecution(currentExec);
                        executionRepository.update(execution);

                        eventBus.publish(execution.getId(), pipeline.getId(), currentExec.getId(), currentStage.getName(),
                                currentStage.getStageOrder(), ExecutionEventType.STAGE_COMPLETED, "RUNNING -> SUCCESS",
                                "Deployment verified to " + depResult.getTargetPath(), depResult.getDurationSeconds(), 0, true);

                        if (onUpdate != null) onUpdate.accept(execution);
                        notifyExecutionUpdate(execution);
                        i++;
                        continue;
                    } else {
                        currentExec.setStatus(StageExecutionStatus.FAILED);
                        currentExec.setExitCode(1);
                        currentExec.setErrorMessage(depResult.getMessage());
                        currentExec.setFinishedAt(new Timestamp(System.currentTimeMillis()));
                        logCollector.append(currentExec.getId(), currentStage.getName(), LogLevel.ERROR, LogStream.STDERR,
                                depResult.getMessage());
                        executionRepository.updateStageExecution(currentExec);
                        executionRepository.update(execution);
                        hasFailure = true;
                        failedStage = currentExec;

                        eventBus.publish(execution.getId(), pipeline.getId(), currentExec.getId(), currentStage.getName(),
                                currentStage.getStageOrder(), ExecutionEventType.STAGE_FAILED, "RUNNING -> FAILED",
                                depResult.getMessage(), null, 1, true);

                        if (onUpdate != null) onUpdate.accept(execution);
                        notifyExecutionUpdate(execution);
                        i++;
                        continue;
                    }
                }
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
                    se.setStatus(StageExecutionStatus.RUNNING);
                    se.setStartedAt(new Timestamp(System.currentTimeMillis()));
                    executionRepository.updateStageExecution(se);
                    parallelGroupStages.add(s);
                    runners.add(new StageRunner(s, se, executionRepository, logCollector, effectiveDir, env, executionPolicy));
                    j++;
                }

                execution.setCurrentPhase(String.format("Running %d stages in parallel (group '%s')", runners.size(), group));
                executionRepository.update(execution);
                notifyExecutionUpdate(execution);

                logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                        String.format("Forking %d stages in parallel group '%s'...", runners.size(), group));

                for (StageRunner r : runners) {
                    eventBus.publish(execution.getId(), pipeline.getId(), r.getStageExecution().getId(),
                            r.getStageExecution().getStageName(), 0, ExecutionEventType.STAGE_STARTED,
                            "QUEUED -> RUNNING", "Forked in parallel group '" + group + "'", null, null, true);
                }

                if (onUpdate != null) onUpdate.accept(execution);

                List<StageExecution> parallelResults = parallelCoordinator.executeParallel(runners);
                for (StageExecution res : parallelResults) {
                    executionRepository.updateStageExecution(res);
                    if (res.getStatus() == StageExecutionStatus.FAILED) {
                        hasFailure = true;
                        if (failedStage == null) failedStage = res;
                        eventBus.publish(execution.getId(), pipeline.getId(), res.getId(), res.getStageName(),
                                0, ExecutionEventType.STAGE_FAILED, "RUNNING -> FAILED",
                                "Parallel stage failed with code " + res.getExitCode(), res.getDurationSeconds(), res.getExitCode(), true);
                    } else if (res.getStatus() == StageExecutionStatus.CANCELLED) {
                        execution.setStatus(ExecutionStatus.CANCELLED);
                        eventBus.publish(execution.getId(), pipeline.getId(), res.getId(), res.getStageName(),
                                0, ExecutionEventType.STAGE_CANCELLED, "RUNNING -> CANCELLED",
                                "Parallel stage cancelled", res.getDurationSeconds(), res.getExitCode(), true);
                    } else if (res.getStatus() == StageExecutionStatus.SUCCESS) {
                        eventBus.publish(execution.getId(), pipeline.getId(), res.getId(), res.getStageName(),
                                0, ExecutionEventType.STAGE_COMPLETED, "RUNNING -> SUCCESS",
                                "Parallel stage succeeded", res.getDurationSeconds(), 0, true);
                    }
                }

                executionRepository.update(execution);
                notifyExecutionUpdate(execution);

                logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                        String.format("Joined parallel group '%s'.", group));

                i = j; // Advance past the group
            } else {
                // Sequential single stage
                currentExec.setStatus(StageExecutionStatus.RUNNING);
                currentExec.setStartedAt(new Timestamp(System.currentTimeMillis()));
                executionRepository.updateStageExecution(currentExec);
                execution.setCurrentPhase(String.format("Running Stage %d of %d: %s", (i + 1), stages.size(), currentStage.getName()));
                executionRepository.update(execution);
                notifyExecutionUpdate(execution);

                StageRunner runner = new StageRunner(currentStage, currentExec, executionRepository, logCollector, effectiveDir, env, executionPolicy);
                if (onUpdate != null) onUpdate.accept(execution);

                StageExecution res = runner.call();
                executionRepository.updateStageExecution(res);
                if (res.getStatus() == StageExecutionStatus.FAILED) {
                    hasFailure = true;
                    failedStage = res;
                    eventBus.publish(execution.getId(), pipeline.getId(), res.getId(), currentStage.getName(),
                            currentStage.getStageOrder(), ExecutionEventType.STAGE_FAILED, "RUNNING -> FAILED",
                            "Stage failed with exit code " + res.getExitCode(), res.getDurationSeconds(), res.getExitCode(), true);
                } else if (res.getStatus() == StageExecutionStatus.CANCELLED) {
                    execution.setStatus(ExecutionStatus.CANCELLED);
                    eventBus.publish(execution.getId(), pipeline.getId(), res.getId(), currentStage.getName(),
                            currentStage.getStageOrder(), ExecutionEventType.STAGE_CANCELLED, "RUNNING -> CANCELLED",
                            "Stage cancelled", res.getDurationSeconds(), res.getExitCode(), true);
                } else if (res.getStatus() == StageExecutionStatus.SUCCESS) {
                    eventBus.publish(execution.getId(), pipeline.getId(), res.getId(), currentStage.getName(),
                            currentStage.getStageOrder(), ExecutionEventType.STAGE_COMPLETED, "RUNNING -> SUCCESS",
                            "Stage succeeded with exit code 0", res.getDurationSeconds(), 0, true);
                }
                executionRepository.update(execution);
                notifyExecutionUpdate(execution);
                i++;
            }

            if (onUpdate != null) onUpdate.accept(execution);
            notifyExecutionUpdate(execution);
        }

        boolean wasInterrupted = Thread.interrupted();
        if (wasInterrupted || execution.getStatus() == ExecutionStatus.CANCELLED) {
            execution.setStatus(ExecutionStatus.CANCELLED);
            execution.setCurrentPhase("Cancelled by user");
        }

        long endTime = System.currentTimeMillis();
        double totalDuration = Math.round(((endTime - startTime) / 1000.0) * 10.0) / 10.0;
        execution.setFinishedAt(new Timestamp(endTime));
        execution.setDurationSeconds(totalDuration);

        if (execution.getStatus() != ExecutionStatus.CANCELLED) {
            execution.setStatus(hasFailure ? ExecutionStatus.FAILED : ExecutionStatus.SUCCESS);
            execution.setCurrentPhase(hasFailure ? "Execution failed" : "Completed successfully");
        }

        // Trigger failure analysis if failed
        if (execution.getStatus() == ExecutionStatus.FAILED && failedStage != null && failureRuleEngine != null) {
            logCollector.append(null, "Intelligence", LogLevel.INFO, LogStream.STDOUT,
                    "Triggering Rule-Based Failure Intelligence Engine...");

            logCollector.flush(); // Ensure logs are saved before querying
            List<ExecutionLog> logs = logRepository != null ? logRepository.findByExecutionId(execution.getId()) : Collections.emptyList();
            FailureDiagnosis diagnosis = failureRuleEngine.analyze(failedStage, logs);
            if (diagnosis != null) {
                if (diagnosisRepository != null) {
                    diagnosisRepository.save(diagnosis);
                }
                execution.setFailureDiagnosis(diagnosis);
                logCollector.append(null, "Intelligence", LogLevel.INFO, LogStream.STDOUT,
                        String.format("Failure Identified: [%s] %s", diagnosis.getCategory().getDisplayName(), diagnosis.getTitle()));
                logCollector.append(null, "Intelligence", LogLevel.INFO, LogStream.STDOUT,
                        "Recommended Next Action: " + diagnosis.getSuggestedAction());

                eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.DIAGNOSIS_CREATED,
                        String.format("Generated diagnosis: [%s] %s (Rule: %s)", diagnosis.getCategory().getDisplayName(), diagnosis.getTitle(), diagnosis.getRuleId()));
            }
        }

        // Record commit hash if available and not yet set
        if ((execution.getCommitHash() == null || execution.getCommitHash().isEmpty()) && effectiveDir != null) {
            String commit = gitService.getCurrentCommitHash(effectiveDir);
            if (commit != null && !commit.isEmpty() && !"UNKNOWN".equals(commit)) {
                execution.setCommitHash(commit);
            }
        }

        // Discover and verify generated build artifacts (Next.js .next, target/*.jar, dist/, etc.)
        ArtifactInfo artifact = inspectBuildArtifact(effectiveDir);
        if (artifact != null) {
            execution.setArtifactPath(artifact.getFilePath());
            logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                    String.format("Verified build artifact: %s (%s, %s, SHA-256: %s)",
                            artifact.getName(), artifact.getType(), artifact.getFormattedSize(), artifact.getSha256Checksum()));

            eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.ARTIFACT_DISCOVERED,
                    "Discovered artifact: " + artifact.getName() + " (" + artifact.getType() + ")");
            eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.ARTIFACT_VERIFIED,
                    String.format("Verified artifact '%s' (%s, SHA-256: %s)", artifact.getName(), artifact.getFormattedSize(), artifact.getSha256Checksum()));
        }

        logCollector.append(null, "System", LogLevel.INFO, LogStream.STDOUT,
                String.format("Pipeline execution finalized with status: %s (Total Duration: %.1fs)",
                        execution.getStatus(), totalDuration));

        logCollector.flush();
        executionRepository.update(execution);

        if (execution.getStatus() == ExecutionStatus.SUCCESS) {
            eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.EXECUTION_COMPLETED,
                    String.format("Pipeline completed successfully in %.1fs", totalDuration));
        } else if (execution.getStatus() == ExecutionStatus.FAILED) {
            eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.EXECUTION_FAILED,
                    String.format("Pipeline execution failed after %.1fs", totalDuration));
        } else if (execution.getStatus() == ExecutionStatus.CANCELLED) {
            eventBus.publish(execution.getId(), pipeline.getId(), ExecutionEventType.EXECUTION_CANCELLED,
                    String.format("Pipeline execution cancelled after %.1fs", totalDuration));
        }

        notifyExecutionUpdate(execution);
        if (onUpdate != null) onUpdate.accept(execution);
    }

    public ArtifactInfo inspectBuildArtifact(File dir) {
        if (dir == null || !dir.exists()) return null;

        // 1. Next.js production build output (.next)
        File nextDir = new File(dir, ".next");
        if (nextDir.exists() && nextDir.isDirectory()) {
            File buildId = new File(nextDir, "BUILD_ID");
            File manifest = new File(nextDir, "build-manifest.json");
            if (buildId.exists() || manifest.exists()) {
                long totalSize = calculateDirectorySize(nextDir);
                String sha = "N/A (Directory Bundle)";
                if (buildId.exists()) {
                    try {
                        sha = DeploymentService.computeSha256(buildId);
                    } catch (Exception ignored) {}
                }
                return new ArtifactInfo("Next.js Production Build (.next)", "NEXTJS_APP", nextDir.getAbsolutePath(), totalSize, sha, "Build / Package", true);
            }
        }

        // 2. Standard JAR / WAR in target/ or build/libs/
        File artifact = findDeploymentArtifact(dir);
        if (artifact != null && artifact.exists() && artifact.isFile()) {
            String sha = "Unknown";
            try {
                sha = DeploymentService.computeSha256(artifact);
            } catch (Exception ignored) {}
            String ext = artifact.getName().substring(artifact.getName().lastIndexOf('.') + 1).toUpperCase();
            return new ArtifactInfo(artifact.getName(), ext + "_BINARY", artifact.getAbsolutePath(), artifact.length(), sha, "Package Artifact", true);
        }

        // 3. Static web distribution directories (dist, out, build)
        File[] webDirs = { new File(dir, "dist"), new File(dir, "out"), new File(dir, "build") };
        for (File d : webDirs) {
            if (d.exists() && d.isDirectory() && d.list() != null && d.list().length > 0) {
                long totalSize = calculateDirectorySize(d);
                return new ArtifactInfo(d.getName() + "/ Production Bundle", "STATIC_WEB_BUNDLE", d.getAbsolutePath(), totalSize, "N/A (Directory)", "Package Artifact", true);
            }
        }

        return null;
    }

    private long calculateDirectorySize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) total += f.length();
                else if (f.isDirectory() && !f.getName().equals(".git") && !f.getName().equals("cache")) {
                    total += calculateDirectorySize(f);
                }
            }
        }
        return total;
    }

    public String discoverBuildArtifactPath(File dir) {
        ArtifactInfo info = inspectBuildArtifact(dir);
        return info != null ? info.getFilePath() : null;
    }

    public boolean cancelExecution(String executionId) {
        Execution execution = activeExecutionEntities.get(executionId);
        if (execution != null) {
            execution.setStatus(ExecutionStatus.CANCELLED);
            execution.setCurrentPhase("Cancelled by user");
            execution.setFinishedAt(new Timestamp(System.currentTimeMillis()));
            executionRepository.update(execution);
            notifyExecutionUpdate(execution);
            eventBus.publish(executionId, execution.getPipelineId(), ExecutionEventType.EXECUTION_CANCELLED,
                    "Execution cancelled upon user request.");
        }
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
        return execution != null;
    }

    public boolean isRunning(String executionId) {
        return activeExecutions.containsKey(executionId);
    }

    public LogCollector getActiveLogCollector(String executionId) {
        return activeLogCollectors.get(executionId);
    }

    public Execution getActiveExecution(String executionId) {
        return activeExecutionEntities.get(executionId);
    }

    private File findDeploymentArtifact(File dir) {
        if (dir == null || !dir.exists()) return null;
        File[] candidateDirs = new File[] {
                new File(dir, "target"),
                new File(dir, "build/libs"),
                new File(dir, "dist"),
                new File(dir, "out"),
                dir
        };

        for (File cDir : candidateDirs) {
            if (cDir.exists() && cDir.isDirectory()) {
                File[] files = cDir.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return (lower.endsWith(".jar") || lower.endsWith(".war") || lower.endsWith(".zip") || lower.endsWith(".tar.gz"))
                            && !lower.startsWith("original-") && !lower.contains("sources") && !lower.contains("javadoc");
                });
                if (files != null && files.length > 0) {
                    // Sort by most recently modified
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    return files[0];
                }
            }
        }
        return null;
    }
}
