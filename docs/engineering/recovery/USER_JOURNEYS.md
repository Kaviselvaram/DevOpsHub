# DEVOPSHUB — SOURCE-LEVEL USER JOURNEY TRACEABILITY MATRIX

**Document Version:** 1.0.0  
**Phase:** Recovery and Stabilization  
**Scope:** End-to-End Source Traceability for All 15 Core Workflows  

---

## Traceability Matrix Overview

This matrix establishes the unbroken source-level code paths connecting user interactions to JavaFX views, controllers, backend services, domain models, database persistence, and external OS/Git boundaries.

---

### Journey 1: Create and Save a Pipeline
- **UI Entry Point:** `PipelinesView` -> Click `+ New Pipeline` button -> Opens `PipelineBuilderView`.
- **Controller / View:** `com.devopshub.ui.PipelineBuilderView` (`handleSavePipeline()`).
- **Service Call:** `com.devopshub.service.PipelineService.createPipeline(Pipeline pipeline)` or `updatePipeline(...)`.
- **Domain Transition:** Instantiates `com.devopshub.model.Pipeline` with `Stage` list and DAG validation via `com.devopshub.pipeline.PipelineValidator`.
- **Persistence Operation:** `com.devopshub.repository.PipelineRepository.save(Pipeline)` -> Parameterized `INSERT INTO pipelines` and `INSERT INTO stages`.
- **Process / Git Boundary:** N/A (Configuration only).
- **User-Visible Result:** Success notification banner displayed; redirected to `PipelinesView` showing the new pipeline card.
- **Failure Path:** `PipelineValidationException` (e.g. cycle detected or empty stage command); shows red inline validation banner with error message.
- **Regression Tests:** `com.devopshub.pipeline.PipelineValidatorTest`.
- **Later Runtime Verification:** Verified via TP-10 (Manual Walkthrough Step 1).

---

### Journey 2: Configure a Repository
- **UI Entry Point:** `ProjectsView` -> Click `+ New Project` button -> Opens Project Creation Modal.
- **Controller / View:** `com.devopshub.ui.ProjectsView` (`handleCreateProject()`).
- **Service Call:** `com.devopshub.service.ProjectService.createProject(Project project)`.
- **Domain Transition:** Instantiates `com.devopshub.model.Project` with validated repository URL, branch (`main`), and workspace directory.
- **Persistence Operation:** `com.devopshub.repository.ProjectRepository.save(Project)` -> `INSERT INTO projects`.
- **Process / Git Boundary:** `GitService.validateWorkspaceConfinement()` ensures repository directory is strictly within `./workspaces`.
- **User-Visible Result:** Project card appears in `ProjectsView` grid with branch badge and repository link.
- **Failure Path:** Invalid URL format or path traversal attempt throws `DevOpsHubException`; modal displays actionable error dialog.
- **Regression Tests:** `com.devopshub.git.GitWorkspaceSecurityAuditTest` (`testDestinationOutsideWorkspaceRootRejected`).
- **Later Runtime Verification:** Verified via TP-09 / TP-10.

---

### Journey 3: Trigger an Execution
- **UI Entry Point:** `PipelineDetailView` -> Click `Run Pipeline` button.
- **Controller / View:** `com.devopshub.ui.PipelineDetailView` (`handleRunPipeline()`).
- **Service Call:** `com.devopshub.service.PipelineService.triggerPipeline(String pipelineId, TriggerType.MANUAL)`.
- **Domain Transition:** `ExecutionEngine.startExecution()` creates `Execution` in state `PENDING` -> transitions to `QUEUED` -> `RUNNING`.
- **Persistence Operation:** `ExecutionRepository.save(execution)` -> `INSERT INTO executions (status, trigger_type, started_at)`.
- **Process / Git Boundary:** Resolves `ExecutionTrustLevel` (`TRUSTED_LOCAL` for manual triggers).
- **User-Visible Result:** UI navigates to `LogViewerView` / Execution Monitoring screen showing live spinner, timer, and active stage indicator.
- **Failure Path:** If engine thread pool is full, queued with warning; if pipeline disabled, returns error banner.
- **Regression Tests:** `com.devopshub.execution.ExecutionEngineTest` (`testSequentialSuccessExecution`).
- **Later Runtime Verification:** Verified via TP-06 / TP-10.

---

### Journey 4: Clone or Prepare a Repository
- **UI Entry Point:** Background task dispatched by `ExecutionEngine` during pipeline initialization.
- **Controller / View:** Background thread updates `ExecutionEngine.java` lines 180-240.
- **Service Call:** `com.devopshub.git.GitService.cloneOrFetch(repoUrl, branch, workspaceDir)`.
- **Domain Transition:** Resolves workspace directory; verifies `isImmediateGitRepository` and tracks directory creation ownership.
- **Persistence Operation:** Logs written via `LogRepository.save(ExecutionLog)`.
- **Process / Git Boundary:** Subprocess invokes `git clone --branch <branch> <url> <dir>` with `GIT_CEILING_DIRECTORIES` configured.
- **User-Visible Result:** Log viewer prints `"[GIT] Preparing repository workspace in ./workspaces/..."`.
- **Failure Path:** If clone fails (exit code != 0), `ExecutionEngine` immediately marks execution as `FAILED`, marks all downstream stages as `SKIPPED`, logs error details, and aborts. Pre-existing directories are preserved.
- **Regression Tests:** `com.devopshub.execution.ExecutionEngineCloneFailureTest`, `com.devopshub.git.GitWorkspaceSecurityAuditTest`.
- **Later Runtime Verification:** Verified via TP-03 / TP-05.

---

### Journey 5: Execute a Stage
- **UI Entry Point:** Triggered by `ExecutionEngine.executeSequentialStages()` or `executeParallelForks()`.
- **Controller / View:** Worker thread updates UI via `Platform.runLater()`.
- **Service Call:** `com.devopshub.execution.StageRunner.call()`.
- **Domain Transition:** `StageExecution` status transitions from `PENDING` -> `RUNNING` -> `SUCCESS` or `FAILED`.
- **Persistence Operation:** `ExecutionRepository.saveStageExecution()` -> `INSERT/UPDATE stage_executions`.
- **Process / Git Boundary:** `ProcessExecutor.execute()` wipes environment, passes sanitized variables (`ExecutionPolicy`), validates working directory, and launches subprocess (`/bin/sh -c` or `cmd.exe /c`).
- **User-Visible Result:** Stage row turns blue (running), then green checkmark on success, with live elapsed duration.
- **Failure Path:** Non-zero exit code marks stage `FAILED`, invokes `FailureRuleEngine` to diagnose failure.
- **Regression Tests:** `com.devopshub.execution.SecurityIsolationAuditTest`, `com.devopshub.execution.ExecutionEngineTest`.
- **Later Runtime Verification:** Verified via TP-01 / TP-06.

---

### Journey 6: Capture and Persist Logs
- **UI Entry Point:** Real-time stream in `LogViewerView` dark terminal console.
- **Controller / View:** `com.devopshub.ui.LogViewerView` listening to `BoundedLogBuffer` events.
- **Service Call:** `com.devopshub.execution.LogCollector.appendLog(String line, LogLevel level)`.
- **Domain Transition:** Creates `com.devopshub.model.ExecutionLog` with sequence number and timestamp.
- **Persistence Operation:** Batched or immediate insert via `com.devopshub.repository.LogRepository.saveBatch()`.
- **Process / Git Boundary:** Stdout and Stderr streams consumed asynchronously by dedicated reader threads in `ProcessExecutor`.
- **User-Visible Result:** Colored log lines appear in terminal (white for stdout, red for stderr, blue for system). Buffer caps at 1,000 lines with notice.
- **Failure Path:** Truncation notice injected if buffer is saturated; durable logs remain in database.
- **Regression Tests:** `com.devopshub.execution.LogCollectorTest`.
- **Later Runtime Verification:** Verified via TP-06 / TP-10.

---

### Journey 7: Handle a Stage Failure
- **UI Entry Point:** Subprocess exits with non-zero exit code.
- **Controller / View:** `ExecutionEngine` failure callback -> updates `ExecutionDetailView`.
- **Service Call:** `com.devopshub.analysis.FailureRuleEngine.analyze(List<ExecutionLog>)`.
- **Domain Transition:** `StageExecution` set to `FAILED`; downstream stages marked `SKIPPED`; `Execution` set to `FAILED`. Rule engine generates `Diagnosis` with root cause and actionable recommendations.
- **Persistence Operation:** `DiagnosisRepository.save(Diagnosis)` -> `INSERT INTO diagnoses`.
- **Process / Git Boundary:** Process terminated and exit code captured.
- **User-Visible Result:** Stage row turns red; diagnostic banner appears showing matched failure pattern (e.g. `HTTP 500 in LoginService`) and step-by-step fix recommendations.
- **Failure Path:** If no rule matches, fallback generic diagnostic information is displayed.
- **Regression Tests:** `com.devopshub.analysis.FailureRuleEngineTest`, `com.devopshub.execution.ExecutionEngineTest`.
- **Later Runtime Verification:** Verified via TP-06.

---

### Journey 8: Cancel an Execution
- **UI Entry Point:** Click `Cancel` button in `ExecutionDetailView` or `LogViewerView`.
- **Controller / View:** `ExecutionDetailView.handleCancelExecution()`.
- **Service Call:** `com.devopshub.service.PipelineService.cancelExecution(String executionId)`.
- **Domain Transition:** `ExecutionEngine.cancelExecution()` marks `Execution` as `CANCELLED` and interrupts stage threads.
- **Persistence Operation:** `ExecutionRepository.updateStatus(executionId, ExecutionStatus.CANCELLED)`.
- **Process / Git Boundary:** `ProcessExecutor.destroy()` forcibly terminates the running OS process tree (`process.destroyForcibly()`).
- **User-Visible Result:** Execution status badge updates immediately to orange `CANCELLED`; running stage terminates; stop timer halted.
- **Failure Path:** Late stage completion cannot overwrite terminal `CANCELLED` status due to terminal guard in `Execution.setStatus`.
- **Regression Tests:** `com.devopshub.execution.ExecutionEngineTest` (`testParallelForkCancellation`).
- **Later Runtime Verification:** Verified via TP-06 / TP-10.

---

### Journey 9: Inspect Execution History
- **UI Entry Point:** Navigation Sidebar -> Click `History` item.
- **Controller / View:** `com.devopshub.ui.ExecutionHistoryView`.
- **Service Call:** `com.devopshub.service.DashboardService.getRecentExecutions()`.
- **Domain Transition:** Loads list of `Execution` entities with associated project, pipeline, commit hash, duration, and status.
- **Persistence Operation:** `ExecutionRepository.findAll()` with parameterized ordering `ORDER BY started_at DESC`.
- **Process / Git Boundary:** N/A.
- **User-Visible Result:** Displays table or Kanban view of recent executions with status badges, timestamps, and commit hashes. Clicking a row opens execution details.
- **Failure Path:** Database connection error displays empty state placeholder with retry button.
- **Regression Tests:** `com.devopshub.repository.ExecutionRepositoryTest`.
- **Later Runtime Verification:** Verified via TP-10.

---

### Journey 10: Search and Filter History
- **UI Entry Point:** Header global search input field or view-specific search box in `ExecutionHistoryView`.
- **Controller / View:** `MainLayoutView.handleGlobalSearch()` -> calls `ExecutionHistoryView.setSearchQuery(query)`.
- **Service Call:** View filters observable collection based on pipeline name, branch, commit hash, or status.
- **Domain Transition:** Filtered list dynamically bound to JavaFX `TableView` / Kanban board.
- **Persistence Operation:** Memory-filtered view or parameterized SQL query.
- **Process / Git Boundary:** N/A.
- **User-Visible Result:** Visible rows filter in real time as the user types; clearing search restores full list.
- **Failure Path:** If no records match, "No matching executions found" empty state is displayed.
- **Regression Tests:** Static review completed (UI-02).
- **Later Runtime Verification:** Verified via TP-10 (Manual Walkthrough Step 1).

---

### Journey 11: Reopen Persisted History After Application Restart
- **UI Entry Point:** Application cold start (`Launcher.main()` -> `App.start()`).
- **Controller / View:** `MainLayoutView` initializes and loads `DashboardView`.
- **Service Call:** `DatabaseManager.getInstance().getConnection()`.
- **Domain Transition:** Initializes schema via `DatabaseManager.runMigrations()` on `./data/devopshub.mv.db` (or MySQL).
- **Persistence Operation:** `SELECT` queries across projects, pipelines, executions, and diagnoses.
- **Process / Git Boundary:** Disk file I/O on database files.
- **User-Visible Result:** All historical executions, created pipelines, and diagnostics are restored and displayed immediately.
- **Failure Path:** Corrupt file triggers automated recovery or falls back with actionable dialog.
- **Regression Tests:** `com.devopshub.config.DatabaseManagerPersistenceAuditTest`.
- **Later Runtime Verification:** Verified via TP-09 (Process Restart Procedure).

---

### Journey 12: Receive a Valid Webhook
- **UI Entry Point:** External HTTP request received on `http://localhost:8080/webhook`.
- **Controller / View:** `com.devopshub.webhook.WebhookServerService.handleRequest()`.
- **Service Call:** Validates HMAC-SHA256 signature against `WEBHOOK_SECRET_KEY` and checks timestamp drift.
- **Domain Transition:** Submits execution with `TriggerType.WEBHOOK` and `ExecutionTrustLevel.UNTRUSTED_EXTERNAL`.
- **Persistence Operation:** `ExecutionRepository.save()` records trigger as `WEBHOOK`.
- **Process / Git Boundary:** If container engine active, dispatches container; if host execution configured, applies sanitized environment.
- **User-Visible Result:** Server returns HTTP 200 OK `{"status": "TRIGGERED"}`; new execution card appears in UI.
- **Failure Path:** Handled in Journey 13.
- **Regression Tests:** `com.devopshub.webhook.WebhookHmacVerificationTest` (`testValidHmacSignatureVerification`).
- **Later Runtime Verification:** Verified via TP-08.

---

### Journey 13: Reject an Invalid Webhook
- **UI Entry Point:** External HTTP request with altered payload, invalid signature, expired timestamp, or replayed delivery ID.
- **Controller / View:** `com.devopshub.webhook.WebhookServerService.handleRequest()`.
- **Service Call:** `verifyHmacSha256()` fails or timestamp drift > 300s or nonce exists in cache.
- **Domain Transition:** Request rejected; **zero** executions or processes are dispatched.
- **Persistence Operation:** Warning logged; no pipeline state created.
- **Process / Git Boundary:** No subprocess spawned.
- **User-Visible Result:** Server responds with HTTP 401 Unauthorized or HTTP 403 Forbidden `{"error": "Unauthorized / Replay Detected"}`.
- **Failure Path:** Fails closed safely.
- **Regression Tests:** `com.devopshub.webhook.WebhookHmacVerificationTest` (`testAlteredPayloadFailsVerification`, `testInvalidSignatureRejection`).
- **Later Runtime Verification:** Verified via TP-08.

---

### Journey 14: Create and Manage a Schedule
- **UI Entry Point:** `PipelineDetailView` -> Click `Schedule` button.
- **Controller / View:** `PipelineDetailView.showScheduleDialog()` and `SettingsView` active schedules table.
- **Service Call:** `com.devopshub.service.PipelineSchedulerService.schedulePipeline(pipelineId, cronOrInterval)`.
- **Domain Transition:** Registers task in `ScheduledExecutorService` with next execution calculation.
- **Persistence Operation:** Schedule persisted in database/configuration.
- **Process / Git Boundary:** N/A.
- **User-Visible Result:** Schedule dialog closes; active schedule appears in `SettingsView` showing next run time; triggers background execution when timer fires.
- **Failure Path:** Unschedule button in `SettingsView` cancels task and updates table.
- **Regression Tests:** Static review completed (UI-01).
- **Later Runtime Verification:** Verified via TP-10 (Manual Walkthrough Step 2).

---

### Journey 15: Shut Down the Application Safely
- **UI Entry Point:** Window close button (`X`) or File -> Exit.
- **Controller / View:** `com.devopshub.app.App.stop()`.
- **Service Call:** Graceful shutdown orchestrated across subsystems:
  - `ExecutionThreadPool.getInstance().shutdown()`
  - `PipelineSchedulerService.getInstance().shutdown()`
  - `WebhookServerService.getInstance().stop()`
  - `DatabaseManager.getInstance().close()` (releases connection pool and H2 disk locks)
- **Domain Transition:** Running executions marked as interrupted/aborted if ungraceful; clean drain on normal exit.
- **Persistence Operation:** Connection pool closed cleanly; WAL flushed to disk.
- **Process / Git Boundary:** Running child processes terminated; orphaned files prevented.
- **User-Visible Result:** Application window closes promptly without hanging JVM processes.
- **Failure Path:** Shutdown hook forces cleanup if window manager forcibly terminates process.
- **Regression Tests:** Clean shutdown verified statically in `App.java`.
- **Later Runtime Verification:** Verified via TP-09.
