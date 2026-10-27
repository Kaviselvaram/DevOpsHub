# DevOpsHub — Remediation Engineering Ledger

**Last Updated:** 2026-10-24  
**Project Version:** 1.0.0  
**Current Milestone Focus:** Milestone 1 (Database Safety and Durable Persistence)

---

## Engineering Item Register

### REQ-DB-01: Explicit Persistence Configuration & Transparent Fallback Disclosure
- **Original Finding:** MySQL unavailability reportedly causes a silent fallback to temporary in-memory storage, misleading users that data is durable.
- **Severity / Priority:** CRITICAL / P0
- **Source Files Affected:** `DatabaseManager.java`, `AppConfig.java`, `MainLayoutView.java`, `SettingsView.java`
- **Root Cause:** In earlier versions, if MySQL connection failed, H2 was silently spun up without conspicuous UI disclosure or error explanation.
- **Intended Change:** 
  1. `DatabaseManager` must provide explicit getters for persistence mode (`isH2Fallback`, `getDatabaseType`, `pingLatencyMillis`).
  2. If MySQL connection fails, display actionable connection diagnostic error without exposing raw credentials.
  3. UI (`MainLayoutView` header and `SettingsView`) must display a visible amber badge ("H2 Database (In-Memory Fallback)") with warning tooltip and toast notification to guarantee the user is never misled into assuming data is stored durably.
- **Acceptance Criteria:**
  - Connecting to MySQL 8.0 shows green indicator "MySQL Connected".
  - If MySQL is down, application clearly logs warning, starts H2, flags fallback state, and alerts user that session changes are volatile.
- **Tests Required:** `DatabaseIntegrationTest`, MySQL failure simulation test.
- **Actual Tests Executed:** `DatabaseIntegrationTest`, `EndToEndWorkflowIntegrationTest`.
- **Evidence Locations:** `docs/engineering/PHASE_0_BASELINE_REPORT.md`, `src/test/java/com/devopshub/repository/DatabaseIntegrationTest.java`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** Docker MySQL 8.0.
- **Remaining Risks:** None.

---

### REQ-DB-02: Multi-Step Atomic Transactions & Foreign Key Integrity
- **Original Finding:** Partial pipeline updates or execution record insertions could leave orphaned stages or inconsistent records if a step fails midway.
- **Severity / Priority:** HIGH / P1
- **Source Files Affected:** `PipelineRepository.java`, `ExecutionRepository.java`, `V1__initial_schema.sql`
- **Root Cause:** Autocommit JDBC transactions allowed stage inserts to commit even if stage dependency resolution threw an exception.
- **Intended Change:** Wrap multi-stage pipeline saves, stage dependency mappings, and execution creation in explicit transactions with `conn.setAutoCommit(false)`, `conn.commit()`, and `conn.rollback()` in `catch`.
- **Acceptance Criteria:** Induced failure during stage dependency persistence rolls back the entire pipeline save, leaving no orphaned rows.
- **Tests Required:** `DatabaseIntegrationTest.testAtomicPipelineSaveRollback`.
- **Actual Tests Executed:** `DatabaseIntegrationTest` (14 passing tests).
- **Evidence Locations:** `src/main/java/com/devopshub/repository/PipelineRepository.java`, `src/test/java/com/devopshub/repository/DatabaseIntegrationTest.java`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** REQ-DB-01.
- **Remaining Risks:** None.

---

### REQ-DB-03: Asynchronous Database Operations Off JavaFX Thread
- **Original Finding:** Database queries executed synchronously on the JavaFX Application Thread during view switching, risking UI freeze.
- **Severity / Priority:** HIGH / P1
- **Source Files Affected:** `ProjectsView.java`, `PipelinesView.java`, `ExecutionHistoryView.java`, `DashboardView.java`
- **Root Cause:** Views called `projectService.getAllProjects()` directly on JavaFX thread inside constructor or `refresh()` method.
- **Intended Change:** Wrap repository fetch calls in background worker tasks or asynchronous `CompletableFuture.supplyAsync()` and update UI on `Platform.runLater()`.
- **Acceptance Criteria:** Heavy queries do not block UI rendering or cause JavaFX pulse drop.
- **Tests Required:** View initialization tests, async callback tests.
- **Actual Tests Executed:** JavaFX view smoke tests and unit tests.
- **Evidence Locations:** `src/main/java/com/devopshub/ui/view/ProjectsView.java`, `DashboardView.java`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** REQ-DB-01.
- **Remaining Risks:** Low.

---

### REQ-GIT-01: Autonomous Git Clone & Revision Tracking
- **Original Finding:** Automatic Git repository cloning reportedly was not implemented; application was claimed to be a mock.
- **Severity / Priority:** HIGH / P0
- **Source Files Affected:** `GitService.java`, `ExecutionEngine.java`
- **Root Cause:** Early prototypes had stubbed Git methods.
- **Intended Change:** Implement real `GitService` with CLI `git clone`, `git fetch`, `git checkout <branch|tag|commit>`, `git rev-parse HEAD`, commit author extraction, and clean workspace deletion on failure.
- **Acceptance Criteria:** Clones real repository (local or remote), checks out designated revision, logs commit SHA, and handles invalid URLs honestly with non-zero exit codes.
- **Tests Required:** `GitServiceTest`, `EndToEndWorkflowIntegrationTest`, `RealLairIntegrationTest`.
- **Actual Tests Executed:** `GitServiceTest` (4 tests), `RealLairIntegrationTest` (1 test).
- **Evidence Locations:** `src/main/java/com/devopshub/git/GitService.java`, `src/test/java/com/devopshub/git/GitServiceTest.java`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** Git CLI installed in system path.
- **Remaining Risks:** Network latency on large external remotes (handled by 300s configurable timeout).

---

### REQ-GIT-02: Secure Git Credential Handling & URL Scrubbing
- **Original Finding:** Risk of token or password leakage in logs or exceptions when cloning private repositories.
- **Severity / Priority:** CRITICAL / P0
- **Source Files Affected:** `GitService.java`, `ProcessExecutor.java`
- **Root Cause:** Git URLs containing `https://user:token@github.com/...` could be printed to console or stored in database logs.
- **Intended Change:** Regex scrubbing of sensitive tokens `https://[^@]+@` replaced with `https://***@` in all logged strings and stage outputs.
- **Acceptance Criteria:** Logs never display raw credentials; authorization tokens scrubbed from stderr and stdout.
- **Tests Required:** `GitServiceTest.testScrubCredentialsFromLog`.
- **Actual Tests Executed:** `GitServiceTest.testCloneWithCredentialsScrubbed`.
- **Evidence Locations:** `src/main/java/com/devopshub/git/GitService.java:310`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** None.
- **Remaining Risks:** Low.

---

### REQ-ENG-01: Pipeline DAG Validation, Cycle Detection & Stage Execution
- **Original Finding:** Pipeline stages could run out of order or enter infinite recursion if cyclical dependencies existed.
- **Severity / Priority:** HIGH / P0
- **Source Files Affected:** `PipelineValidator.java`, `ExecutionEngine.java`, `StageRunner.java`
- **Root Cause:** Missing topological validation of stage prerequisite graph before execution.
- **Intended Change:** Kahn's algorithm or DFS cycle detection in `PipelineValidator.validate()`. In `ExecutionEngine`, prerequisites must all have status `SUCCESS` before a dependent stage begins.
- **Acceptance Criteria:** Cyclic pipelines fail validation with explicit error; stage dependencies are strictly respected.
- **Tests Required:** `PipelineValidatorTest`, `LifecycleEndToEndAuditTest`.
- **Actual Tests Executed:** `PipelineValidatorTest` (9 tests), `LifecycleEndToEndAuditTest` (7 tests).
- **Evidence Locations:** `src/main/java/com/devopshub/pipeline/PipelineValidator.java`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** REQ-DB-02.
- **Remaining Risks:** None.

---

### REQ-ENG-02: Deadlock-Free stdout and stderr Streaming
- **Original Finding:** High-volume output could block OS process buffers causing child process execution to hang.
- **Severity / Priority:** HIGH / P0
- **Source Files Affected:** `ProcessExecutor.java`, `LogCollector.java`
- **Root Cause:** Synchronous reading of stdout before stderr without separate consumption threads.
- **Intended Change:** Independent asynchronous reader threads for stdout and stderr using bounded buffers and direct callback streaming into `LogCollector` and database batch logger.
- **Acceptance Criteria:** Long output (10,000+ lines) completes in expected time without process buffer deadlock.
- **Tests Required:** `LifecycleEndToEndAuditTest.testProcessStreaming`.
- **Actual Tests Executed:** `LifecycleEndToEndAuditTest`, `ExecutionEngineTest`.
- **Evidence Locations:** `src/main/java/com/devopshub/execution/ProcessExecutor.java:118`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** None.
- **Remaining Risks:** Memory growth on unbounded logs (mitigated by bounded buffer limit).

---

### REQ-ENG-03: Process Cancellation & Subprocess Tree Termination
- **Original Finding:** Cancelling an execution could leave orphaned background subprocesses running.
- **Severity / Priority:** HIGH / P1
- **Source Files Affected:** `ProcessExecutor.java`, `ExecutionEngine.java`, `ExecutionThreadPool.java`
- **Root Cause:** Calling `process.destroy()` only terminates the top-level shell, leaving child commands alive.
- **Intended Change:** Use Java 9+ `ProcessHandle.descendants()` to recursively destroy all child processes forcible (`destroyForcibly()`), and mark execution as `CANCELLED`.
- **Acceptance Criteria:** Cancellation terminates parent and child processes within 2 seconds.
- **Tests Required:** `LifecycleEndToEndAuditTest.testPipelineProcessCancellation`.
- **Actual Tests Executed:** `LifecycleEndToEndAuditTest.testPipelineProcessCancellation` (PASSED).
- **Evidence Locations:** `src/main/java/com/devopshub/execution/ProcessExecutor.java:214`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** REQ-ENG-01.
- **Remaining Risks:** None.

---

### REQ-BLD-01: Multi-Ecosystem Build & Test Pipeline Execution
- **Original Finding:** Pipelines allegedly executed dummy `echo` commands instead of real compilation and test suites.
- **Severity / Priority:** HIGH / P0
- **Source Files Affected:** `ProjectImportService.java`, `ExecutionEngine.java`
- **Root Cause:** Earlier code intercepted commands or generated placeholder echo steps.
- **Intended Change:** `ProjectImportService` detects Maven (`pom.xml`), Gradle (`build.gradle`), npm (`package.json`), Python (`requirements.txt`, `pyproject.toml`) and constructs actual build and test stages (`mvn clean test`, `npm test`, `pytest`).
- **Acceptance Criteria:** Real compiler / test commands execute, produce real exit codes, and failing tests honestly mark the pipeline as `FAILED`.
- **Tests Required:** `ProjectImportServiceTest`, `EndToEndWorkflowIntegrationTest`, `RealLairIntegrationTest`.
- **Actual Tests Executed:** `ProjectImportServiceTest` (6 tests), `EndToEndWorkflowIntegrationTest`, `RealLairIntegrationTest`.
- **Evidence Locations:** `src/main/java/com/devopshub/service/ProjectImportService.java`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** REQ-ENG-01, REQ-ENG-02.
- **Remaining Risks:** Host machine missing specific toolchains (e.g. Gradle or Python). Fallback: clear error code 127 and diagnostic advice.

---

### REQ-BLD-02: Real Deployment & Artifact SHA-256 Verification
- **Original Finding:** Deploy stage was a dummy command with no artifact verification.
- **Severity / Priority:** HIGH / P1
- **Source Files Affected:** `DeploymentService.java`, `ExecutionEngine.java`
- **Root Cause:** Absence of deployment engine.
- **Intended Change:** Implement `DeploymentService` to locate generated artifacts in `target/`, `build/libs/`, or `dist/`, copy them to `deployments/`, and compute bit-for-bit SHA-256 checksums stored in database.
- **Acceptance Criteria:** If artifact is missing, deploy stage fails. If present, artifact is verified and checksum is logged.
- **Tests Required:** `DeploymentServiceTest`, `EndToEndWorkflowIntegrationTest`.
- **Actual Tests Executed:** `DeploymentServiceTest` (3 tests passed), `EndToEndWorkflowIntegrationTest`.
- **Evidence Locations:** `src/main/java/com/devopshub/deployment/DeploymentService.java`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** REQ-BLD-01.
- **Remaining Risks:** None.

---

### REQ-PKG-01: Standalone Runnable Fat JAR Packaging
- **Original Finding:** Standard Maven package produces thin JAR that fails with `ClassNotFoundException: javafx.application.Application`.
- **Severity / Priority:** CRITICAL / P0
- **Source Files Affected:** `pom.xml`, `Launcher.java`
- **Root Cause:** Standard `maven-jar-plugin` does not bundle transitive dependencies or JavaFX native platform modules.
- **Intended Change:** Configure `maven-assembly-plugin` with `jar-with-dependencies` descriptor, pointing `Main-Class` manifest to `com.devopshub.app.Launcher` (non-JavaFX subclass launcher that bootstraps JavaFX).
- **Acceptance Criteria:** Produces standalone JAR (`target/devopshub-desktop-1.0.0-jar-with-dependencies.jar`) that launches directly via `java -jar` without requiring Maven development classpath.
- **Tests Required:** Clean build and execution via `java -jar`.
- **Actual Tests Executed:** Live execution tested: successfully booted HikariCP, MySQL, and JavaFX scene.
- **Evidence Locations:** `pom.xml:143-167`, `src/main/java/com/devopshub/app/Launcher.java`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** None.
- **Remaining Risks:** Native platform libraries across operating systems (macOS arm64 verified; Windows/Linux require respective JavaFX jars).

---

### REQ-UI-01: Modern Visual Interface & Thread-Safe UI Architecture
- **Original Finding:** Risk of disconnected controls, mock data, or freezing during background executions.
- **Severity / Priority:** HIGH / P1
- **Source Files Affected:** `MainLayoutView.java`, `DashboardView.java`, `ProjectsView.java`, `PipelineBuilderView.java`, `ExecutionHistoryView.java`
- **Root Cause:** Incomplete views or synchronous event handlers.
- **Intended Change:** Full dark mode design system (`devopshub.css`), live stage progress updates via `Platform.runLater()`, dynamic DAG visualizer in `PipelineDetailView`, log filtering by stage, and real database analytics.
- **Acceptance Criteria:** All views load real persisted data, animations and toasts function smoothly, zero UI thread freezing.
- **Tests Required:** Live interactive visual audit (Part 9).
- **Actual Tests Executed:** UI smoke tests and headless startup.
- **Evidence Locations:** `src/main/resources/css/devopshub.css`, `src/main/java/com/devopshub/ui/view/`.
- **Current Status:** IN_PROGRESS (Awaiting Part 9 Live Visual Audit).
- **Dependencies:** REQ-DB-01, REQ-ENG-01.
- **Remaining Risks:** Live visual verification requires interactive inspection.

---

### REQ-SEC-01: Webhook Secret Authentication & Input Sanitization
- **Original Finding:** Webhook endpoint or archive extraction had insufficient input sanitization.
- **Severity / Priority:** HIGH / P0
- **Source Files Affected:** `WebhookServerService.java`, `ProjectImportService.java`
- **Root Cause:** Unvalidated zip extraction paths and unauthenticated webhooks.
- **Intended Change:** Constant-time HMAC SHA-256 secret verification on `/api/webhook/trigger`; canonical path checks to prevent ZipSlip path traversal in `ProjectImportService`.
- **Acceptance Criteria:** Unauthenticated webhooks rejected with 401 Unauthorized; malicious zip paths rejected with `SecurityException`.
- **Tests Required:** `WebhookServerTest`, `ProjectImportServiceTest.testZipSlipVulnerabilityPrevention`.
- **Actual Tests Executed:** `WebhookServerTest` (4 tests), `ProjectImportServiceTest` (6 tests).
- **Evidence Locations:** `src/main/java/com/devopshub/webhook/WebhookServerService.java`, `src/main/java/com/devopshub/service/ProjectImportService.java:232`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** None.
- **Remaining Risks:** None.

---

### REQ-REL-01: Crash Recovery & Startup Orphan Reconciler
- **Original Finding:** If the application crashes during execution, running executions remained marked `RUNNING` forever.
- **Severity / Priority:** HIGH / P1
- **Source Files Affected:** `ExecutionRepository.java`, `MainApp.java`
- **Root Cause:** No startup reconciliation of unfinished executions from prior sessions.
- **Intended Change:** `ExecutionRepository.reconcileInterruptedExecutions()` runs on application startup, updating any stale `RUNNING` executions to `FAILED` with diagnostic note "Execution was interrupted by application shutdown".
- **Acceptance Criteria:** Application startup automatically cleans up dangling running records.
- **Tests Required:** `DatabaseIntegrationTest`, `LifecycleEndToEndAuditTest`.
- **Actual Tests Executed:** `DatabaseIntegrationTest.testReconcileInterruptedExecutions`.
- **Evidence Locations:** `src/main/java/com/devopshub/repository/ExecutionRepository.java:165`.
- **Current Status:** VERIFIED_FIXED
- **Dependencies:** REQ-DB-01.
- **Remaining Risks:** None.
