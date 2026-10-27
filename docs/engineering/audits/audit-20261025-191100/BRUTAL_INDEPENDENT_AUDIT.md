# DEVOPSHUB — BRUTAL INDEPENDENT ENGINEERING AUDIT
## Zero-Compromise Verification of Actual Product Behavior

**Audit Authority:** Adversarial Principal Software Architect, CI/CD Platform Engineer, Application Security Auditor, Test Infrastructure Engineer, and Independent Release Assessor  
**Audit Run ID:** `audit-20261025-191100`  
**Date of Audit:** October 25, 2026  
**Repository Branch:** `main` (commit [`da9301275fbe82414ec53cef9bd0706ed8458d93`](file:///Users/kaviselvaramkathirvel/javacicd))  
**Host Platform:** macOS 25.6.0 Apple Silicon (Darwin Kernel 25.6.0 arm64), Java SE 25.0.1 LTS, Maven 3.6.3, Git 2.50.1, Docker MySQL 8.0.46 on `localhost:3306`, Node.js v24.15.0, npm 11.12.1  
**External Integration Target:** `https://github.com/Kaviselvaram/lair.git` (Strictly READ-ONLY target; tested in isolated disposable workspaces)

---

## A. Executive Verdict

### **CONDITIONAL MVP — CORE WORKFLOW VERIFIED, BUT MATERIAL RELEASE LIMITATIONS REMAIN**

> [!WARNING]
> **REJECTED FOR PRODUCTION / MULTI-TENANT / UNTRUSTED REPOSITORY SCOPE.**  
> DevOpsHub executes pipeline subprocesses with unrestricted host desktop user permissions (`uid=501(kaviselvaramkathirvel) gid=20(staff)`) without containerization, chroot, or user namespace sandboxing. Furthermore, earlier claims of "webhook HMAC-SHA256 signature verification" were completely disproven: the webhook receiver performs a simple plaintext token comparison. DevOpsHub is approved **ONLY as an internal developer desktop utility for trusted local projects backed by a live MySQL instance.**

This audit was conducted under adversarial standards where source inspection establishes implementation intent (E1), unit tests establish component isolation (E2), but only live runtime execution (E4) and negative adversarial testing (E5) establish product truth. Arbitrary scoring rubrics (e.g. 87/100 or 98/100) are rejected in favor of empirical evidence.

```
========================================================================================
                                VERIFICATION SCORECARD
========================================================================================
Core UI-to-Execution Workflow:        VERIFIED (E4) [Real processes, exit codes, logs]
External Next.js (L/Air) Build:       VERIFIED (E4) [Clone, lint, build, .next verified]
MySQL Database Durability:            VERIFIED (E4) [Durability across pool restarts]
H2 In-Memory Fallback Persistence:    DISPROVEN (E4) [Volatile; all data lost on exit]
Webhook HMAC-SHA256 Verification:     DISPROVEN (E1/E5) [Fabricated claim; plaintext token]
Workspace Subprocess Sandboxing:      DISPROVEN (E5) [Unrestricted desktop host privileges]
Automated Scheduler UI Integration:   DISPROVEN (E1) [Dead code; no UI controls exist]
Standard Maven JAR Runnable:          DISPROVEN (E4) [Throws ClassNotFoundException]
Fat Assembly JAR Runnable:            VERIFIED (E4) [macOS ARM64 only; platform locked]
========================================================================================
```

---

## B. What Actually Works (Empirical Runtime Evidence)

Every capability listed below was executed and verified during this audit run:

1. **Real Subprocess Execution Engine:** `ProcessExecutor` and `StageRunner` launch actual operating system processes (`ProcessBuilder`), capturing standard output, standard error, exit codes (0, 42, 127, 124, 130), duration metrics, and streaming logs via `LogCollector` into persistent storage.
2. **External L/Air Integration:** DevOpsHub inspected the remote repository `https://github.com/Kaviselvaram/lair.git`, identified it as a Next.js (NodeJS) project with `npm ci`, cloned commit [`d2b7ca779a76f036d7a89f565a99c727db6c591a`](file:///Users/kaviselvaramkathirvel/javacicd), executed real linting (`npm run lint`) and production compilation (`npm run build`), and verified that `.next/BUILD_ID` and build manifests were generated on the physical filesystem.
3. **Stage Dependency DAG & Downstream Skipping:** In sequential pipelines, if an upstream stage fails (e.g., exit code 42), all downstream dependent stages are immediately marked `SKIPPED` without executing their commands.
4. **Timeout Enforcement:** Stages configured with a timeout (e.g. `sleep 10` with timeout 1s) are forcefully terminated via `ProcessHandle.descendants()`, exiting cleanly with code 124 in 1097ms.
5. **Interactive Cancellation:** Active long-running child processes (`sleep 20`) are terminated upon cancellation request, transitioning execution status to `CANCELLED` and destroying child process trees.
6. **Rule-Based Failure Intelligence:** When a stage fails with non-zero exit codes, `FailureRuleEngine` inspects stdout/stderr streams and categorizes failures into structured diagnosis records (`RULE_TIMEOUT_05`, `ASSERTION_FAILURE`, `COMPILATION_ERROR`, etc.) with remediation recommendations.
7. **MySQL Database Persistence:** When connected to MySQL 8.0 on `localhost:3306`, entity records (280 projects, 527 pipelines, 1078 stages, executions, and logs) persist durably. Simulating an application restart by terminating the HikariCP pool and re-opening it confirmed 100% data retention and atomic transaction rollback on constraint failure.
8. **Direct JavaFX UI Rendering:** All 9 primary views render cleanly at 1280x800 resolution without window occlusion. Project creation modals, pipeline builder DAG canvases, log viewer stage filtering, and execution detail monitors are interactive and connected to backing services.
9. **Credential Scrubbing in Logs:** `SecretMasker` intercepts URLs and commands containing embedded credentials (`https://oauth2:token@...`) and masks secrets into `***` before persisting to database logs or terminal views.
10. **ZipSlip Protection:** `ProjectImportService.extractArchiveSafely()` enforces canonical path checks, rejecting directory traversal payloads (`../`).

---

## C. What Does Not Work (Defects, Disconnections & Incomplete Logic)

1. **Webhook HMAC-SHA256 Authentication is Non-Existent:**  
   Earlier reports repeatedly asserted that webhooks implement HMAC-SHA256 signature verification via `X-Hub-Signature-256`. In reality, [`WebhookServerService.java:133`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/webhook/WebhookServerService.java#L133) does not parse HTTP headers or compute HMAC hashes. It checks `configuredSecretToken.equals(token)` on a plaintext query parameter or JSON body string.
2. **Git Traversal Attributing DevOpsHub Commit Hash to User Executions:**  
   When a pipeline runs against a project without a separate cloned Git repository or when workspace initialization fails, `ExecutionEngine` defaults `effectiveDir` to `./workspaces`. Because `./workspaces` contains no `.git` directory, `git rev-parse HEAD` walks up the directory tree into `/Users/kaviselvaramkathirvel/javacicd` and stamps the DevOpsHub repository's HEAD commit (`da9301275fbe82414ec53cef9bd0706ed8458d93`) onto user execution records. Furthermore, any subprocess invoking `git` inside `./workspaces` operates directly on the host application repository.
3. **Failed Workspace Clones Do Not Abort Pipeline Execution:**  
   In [`ExecutionEngine.java:224-228`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L224-L228), if `gitService.cloneOrFetch` fails (e.g. invalid URL or branch), the engine logs a warning to `logCollector`, but does not mark the execution as failed or abort. It immediately continues into the stage execution loop, running build stages against a non-existent or deleted workspace.
4. **Workspace Deletion upon Clone Failure:**  
   When `git clone` fails, [`GitService.java:139`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/git/GitService.java#L139) calls `cleanupDirectory(destinationDir)`. If `destinationDir` was an existing directory or shared workspace, it recursively wipes all pre-existing files.
5. **Automated Scheduler is Dead Code from UI:**  
   [`PipelineSchedulerService.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/scheduler/PipelineSchedulerService.java) implements interval scheduling, but `schedulePipeline(...)` is never called by any UI controller. [`SettingsView.java:199`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/SettingsView.java#L199) merely displays `scheduler.getScheduledJobs().size()` (always 0). There is no form or button to schedule a pipeline.
6. **In-Memory H2 Fallback Wipes All Data on Exit:**  
   When MySQL is unreachable, DevOpsHub falls back to `jdbc:h2:mem:devopshub`. All projects, pipelines, executions, and logs created in this mode are lost immediately upon application exit.
7. **Standard Maven Thin JAR Fails to Launch:**  
   `target/devopshub-desktop-1.0.0.jar` fails with `ClassNotFoundException: javafx.application.Application` because OpenJFX dependencies are not bundled. Only the 16.9 MB assembly fat JAR runs.
8. **Packaging Platform Lock-In (macOS Apple Silicon Only):**  
   The fat JAR bundles `libglass.dylib` with classifier `mac-aarch64`. It cannot run on Linux x86_64 or Windows.
9. **UI Cosmetic Stubs:**  
   - Header search bar (`searchInput` in [`MainLayoutView.java:90`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/MainLayoutView.java#L90)) displays an informational toast (`"Filtering workspace for: ..."`) but performs no filtering on active views.
   - Kanban board in [`ExecutionHistoryView.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/ExecutionHistoryView.java) is read-only; dragging cards between columns is not supported.
   - Settings gear icon in [`AlertsSidebarView.java:45`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/AlertsSidebarView.java#L45) has no attached event handler.

---

## D. What Remains Unknown / Unverified

1. **Private Git SSH Authentication:** Testing was conducted against public HTTPS repositories. Private Git repositories requiring SSH keys (`git@github.com:...`) or SSH passphrase prompts were not verified in runtime tests.
2. **Cross-Platform Linux & Windows Execution:** Testing was executed exclusively on macOS 25.6.0 Apple Silicon (arm64). Packaging and execution on Ubuntu 24.04 or Windows 11 remain untested.
3. **Long-Running Memory Stability under Massive Log Ingestion:** `LogCollector` flushes logs to the database in batches, but runs generating >100,000 log lines were not tested for heap exhaustion or JavaFX UI rendering stutter.

---

## E. Contradiction Register: Previous Claims vs Current Evidence

| # | Previous Claim | Current Evidence | Contradiction | Severity | Required Action |
|---|---|---|---|:---:|---|
| 1 | "Webhooks validate HMAC-SHA256 signatures (`X-Hub-Signature-256`) using shared secret." | [`WebhookServerService.java:133`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/webhook/WebhookServerService.java#L133) only compares `configuredSecretToken.equals(token)`. No `Mac.getInstance("HmacSHA256")` exists. | **FABRICATED CLAIM.** HMAC authentication does not exist in the product. | **CRITICAL** | Implement true HMAC-SHA256 signature verification over raw request body with constant-time equality check. |
| 2 | "Verified commit hash recorded on execution." | Pipelines executing without an explicit cloned repo or after failed clone run in `./workspaces`, where `git rev-parse HEAD` returns DevOpsHub's commit hash `da93012...`. | **MISLEADING ATTRIBUTION.** DevOpsHub's commit hash was attributed to unrelated test pipelines. | **HIGH** | Restrict `rev-parse` strictly to directories containing an immediate `.git` child; isolate workspaces outside the repo root. |
| 3 | "Git clone failure cleanly caught and handled." | In [`ExecutionEngine.java:224`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L224), failed clone logs a warning but allows stage execution to continue. | **INCOMPLETE ERROR HANDLING.** Pipeline continues executing after workspace initialization failure. | **HIGH** | Immediately abort execution and transition status to `FAILED` if workspace clone/checkout fails. |
| 4 | "Automated Pipeline Scheduler verified." | `PipelineSchedulerService` is never invoked from UI controllers; no UI exists to schedule pipelines. | **DISCONNECTED CODE.** Scheduler is an internal library with tests, not an accessible application feature. | **MEDIUM** | Wire schedule creation forms into `PipelineBuilderView` and `PipelineDetailView`. |
| 5 | "H2 fallback database provides persistence." | In-memory H2 (`jdbc:h2:mem:devopshub`) loses 100% of data upon pool termination or application restart. | **ACCIDENTAL DATA LOSS.** Earlier claims of persistence do not apply to fallback mode. | **HIGH** | Migrate H2 fallback to embedded file-based storage (`jdbc:h2:file:./data/devopshub`). |
| 6 | "Universal cross-platform Java package." | Fat JAR contains macOS ARM64 `libglass.dylib` native libraries. Fails on Linux and Windows. | **PLATFORM DEPENDENCY.** Release artifact is machine-specific to Apple Silicon. | **HIGH** | Build platform-specific distributions via Maven profiles and `jpackage`. |
| 7 | "Full Kanban drag-and-drop workflow." | Cards in `ExecutionHistoryView` are placed into columns by database status; no drag handlers exist. | **READ-ONLY DISPLAY.** UI looks like an interactive board but is a static status viewer. | **LOW** | Document Kanban board as read-only audit view or implement drag handlers. |
| 8 | "Overall Maturity Score: 87.2 / 100" | Arbitrary score without standardized formula, masking critical security and attribution gaps. | **UNSUPPORTED METRIC.** Violates Rule 18. | **MEDIUM** | Abolish arbitrary numerical maturity scores; use capability matrix verdicts. |

---

## F. Complete Capability Matrix

| Capability | User-Facing Promise | Actual Implementation | Evidence Level | Verification Test Performed | Expected Result | Observed Result | Final Verdict | Severity |
|---|---|---|:---:|---|---|---|:---:|:---:|
| **Repository URL Input** | Enter Git URL or local folder in Project modal | [`ProjectsView.java:602`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/ProjectsView.java#L602) validates URL format | **E4** | Validated URLs in UI modal | Reject invalid; accept valid | Validated correctly via `GitService.isValidRepositoryUrl` | **VERIFIED** | - |
| **Git Clone (Public)** | Automated clone of remote repository | [`GitService.java:83`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/git/GitService.java#L83) invokes `git clone --single-branch` | **E4** | Cloned `https://github.com/Kaviselvaram/lair.git` | Exit 0, real `.git` created | Cloned in 3.38s; commit `d2b7ca7...` confirmed | **VERIFIED** | - |
| **Branch Resolution** | Check out selected branch | `git checkout <branch>` in `GitService.java` | **E4** | Cloned `main` branch of L/Air | Head matches remote branch | Head confirmed `d2b7ca7...` | **VERIFIED** | - |
| **Git Failure Cleanup** | Purge incomplete directory on failure | [`GitService.java:139`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/git/GitService.java#L139) deletes workspace on non-zero exit | **E5** | Cloned non-existent repo URL | Directory purged, non-zero code | Directory deleted; code 128 returned | **VERIFIED** | - |
| **Clone Failure Propagation** | Abort pipeline if clone fails | [`ExecutionEngine.java:224`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L224) logs warning only | **E5** | Executed pipeline with invalid repo | Execution immediately fails | Engine continued to execute stages | **FAILED** | **HIGH** |
| **Workspace Isolation** | Isolated directories per run/project | `projects/<id>` subdirectories under `./workspaces` | **E5** | Inspected execution working dir | No cross-project collision | Shared `./workspaces` leaks parent Git | **PARTIALLY VERIFIED** | **HIGH** |
| **Project Type Detection** | Auto-detect project framework | [`ProjectImportService.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/service/ProjectImportService.java) inspects package.json/pom.xml | **E4** | Inspected cloned L/Air workspace | Detected Next.js, npm, npm ci | Correctly detected Next.js (NodeJS) | **VERIFIED** | - |
| **Pipeline DAG Composition** | Build multi-stage execution flows | [`PipelineBuilderView.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/PipelineBuilderView.java) & [`PipelineValidator.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/pipeline/PipelineValidator.java) | **E4** | Composed 4-stage DAG; cycle detection | Cycles blocked; valid saved | Cycle validation passed; saved to DB | **VERIFIED** | - |
| **Real Subprocess Execution** | Run actual shell/Maven/Node commands | [`ProcessExecutor.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ProcessExecutor.java) via `ProcessBuilder` | **E4** | Executed `java`, `npm`, `sleep`, `sh` | Exit codes captured correctly | 0, 42, 127, 124, 130 captured accurately | **VERIFIED** | - |
| **Dependency Skipping** | Skip downstream stages on upstream failure | [`ExecutionEngine.java:261`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L261) checks prereq status | **E4** | Stage 1 exit 42; Stage 2 depends on 1 | Stage 2 skipped | Stage 1 FAILED (42); Stage 2 SKIPPED | **VERIFIED** | - |
| **Execution Timeout** | Kill processes exceeding timeout limit | [`ProcessExecutor.java:99`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ProcessExecutor.java#L99) `waitFor` & `destroyForcibly` | **E5** | `sleep 10` with timeout 1s | Process killed; exit 124 | Terminated in 1097ms; exit 124 | **VERIFIED** | - |
| **User Cancellation** | Stop active running pipeline | [`ExecutionEngine.java:497`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L497) cancel task & destroy tree | **E5** | Cancelled active `sleep 20` | Child killed; status CANCELLED | Status CANCELLED; process terminated | **VERIFIED** | - |
| **Artifact Collection** | Discover and verify built artifacts | [`ExecutionEngine.java:467`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L467) checks `.next`, `target/*.jar` | **E4** | Next.js build on L/Air | `.next/BUILD_ID` verified | Discovered `.next`; path persisted | **VERIFIED** | - |
| **Failure Rule Intelligence** | Diagnose root cause of failure | [`FailureRuleEngine.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/analysis/FailureRuleEngine.java) regex rule analysis | **E4** | Analyzed exit 124 timeout log | Matched `RULE_TIMEOUT_05` | Diagnosis & suggested action persisted | **VERIFIED** | - |
| **MySQL Durability** | Retain data across restart | HikariCP pool to MySQL 8.0 | **E4** | Closed and recreated connection pool | Entities reload identical | 100% data retention confirmed | **VERIFIED** | - |
| **H2 Fallback Durability** | Fallback persistence when MySQL down | `jdbc:h2:mem:devopshub` in-memory pool | **E4** | Tested entity reload on pool restart | Data preserved | 100% data loss on pool reset | **FAILED** | **HIGH** |
| **UI Navigation & Theme** | Modern dark theme, 9 primary screens | JavaFX CSS [`devopshub.css`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/resources/css/devopshub.css) | **E4** | Navigated all 9 screens via runner | Renders without visual error | All 9 views captured cleanly | **VERIFIED** | - |
| **Header Search Field** | Global search across workspace | [`MainLayoutView.java:90`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/MainLayoutView.java#L90) | **E1/E4** | Entered query in header search | Filters current view data | Cosmetic toast stub only; no filtering | **PARTIALLY VERIFIED** | **LOW** |
| **Automated Scheduler** | Trigger pipelines on time intervals | [`PipelineSchedulerService.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/scheduler/PipelineSchedulerService.java) | **E2** | Unit tested in service suite | Scheduled runs trigger | Tested in isolation; NO UI WIRING | **NOT VERIFIED (UI)** | **MEDIUM** |
| **Webhook HTTP Ingestion** | Trigger pipeline runs via HTTP POST | Sun `HttpServer` in [`WebhookServerService.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/webhook/WebhookServerService.java) | **E4** | Sent POST request to port 8089 | Triggers execution; returns 200 | Triggered execution #1; returned JSON | **VERIFIED** | - |
| **Webhook HMAC Verification** | Cryptographic payload verification | Fabricated claim; plaintext comparison | **E1/E5** | Inspected source code | HMAC-SHA256 calculation | Zero HMAC code; simple string check | **FAILED** | **CRITICAL** |
| **Webhook Replay Defense** | Reject replayed webhook deliveries | Nonce cache / timestamp validation | **E1/E5** | Sent duplicate POST payload | Duplicate rejected | Payload accepted repeatedly | **FAILED** | **HIGH** |
| **Host Security Isolation** | Contain pipeline commands | None; executes as desktop host user | **E5** | Executed `whoami`, `id`, `ls ~/.ssh` | Commands restricted to sandbox | Full access to host user files | **FAILED** | **CRITICAL** |
| **Fat JAR Packaging** | Standalone executable deployment | `maven-assembly-plugin` fat JAR | **E4** | Executed `java -jar ...fat.jar` | App starts standalone | Successfully started & connected | **VERIFIED (macOS)** | **MEDIUM** |
| **Thin JAR Packaging** | Standard Maven artifact deployment | `devopshub-desktop-1.0.0.jar` | **E4** | Executed `java -jar ...thin.jar` | App starts | Fails: `ClassNotFoundException` | **FAILED** | **MEDIUM** |

---

## G. Critical & High-Severity Defect Register

### **SEC-01 (CRITICAL): Unrestricted Host Desktop User Subprocess Execution**
- **Affected Capability:** Pipeline Execution Engine / Security Isolation
- **Evidence:** Runtime audit log confirmed pipeline executed `whoami` as `kaviselvaramkathirvel`, `id` as `uid=501(kaviselvaramkathirvel) gid=20(staff)`, and read `~/.ssh/known_hosts`.
- **Reproduction:** Create a pipeline stage with command `cat ~/.env` or `ls ~/.ssh`. Execute pipeline.
- **Impact:** Any pipeline script can read, alter, or exfiltrate private developer SSH keys, AWS credentials, database passwords, or personal files. Untrusted repository PRs can execute arbitrary malware on the developer's workstation.
- **Remediation:** Execute pipeline stages inside isolated Docker containers (`docker run --rm -v ...`) or macOS sandbox profiles (`sandbox-exec -f ...`) with dropped privileges and non-root users.
- **Regression Test Required:** `SecurityIsolationAuditTest.assertCannotAccessHostHomeDirectory()`.

### **SEC-02 (CRITICAL): Fabricated HMAC Authentication & Plaintext Webhook Token Comparison**
- **Affected Capability:** Webhook HTTP Server / Authentication
- **Evidence:** [`WebhookServerService.java:133`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/webhook/WebhookServerService.java#L133) contains only `configuredSecretToken.equals(token)`. No `X-Hub-Signature-256` or HMAC-SHA256 implementation exists.
- **Reproduction:** Send `POST http://localhost:8089/api/webhook/trigger?pipelineId=<id>&token=devopshub-secret-token`. The server accepts the request and triggers a pipeline run without any signature header.
- **Impact:** An attacker on the local network can trigger arbitrary pipeline executions using the default token `"devopshub-secret-token"`.
- **Remediation:** Implement standard GitHub/GitLab webhook signature verification using `javax.crypto.Mac` (`HmacSHA256`) over raw body bytes, compared using constant-time `MessageDigest.isEqual()`.
- **Regression Test Required:** `WebhookHmacVerificationTest.testValidAndInvalidHmacSignatures()`.

### **ARCH-01 (HIGH): DevOpsHub Repository Traversal Stamping Host Commit on Pipeline Executions**
- **Affected Capability:** Workspace Management / Git Commit Attribution
- **Evidence:** Executions created without a separate clone in `./workspaces` executed `git rev-parse HEAD` and stamped DevOpsHub's commit hash (`da9301275fbe82414ec53cef9bd0706ed8458d93`) into the database.
- **Reproduction:** Create a project with empty repository URL or run `LifecycleEndToEndAuditTest`. Query `SELECT commit_hash FROM executions`.
- **Impact:** Audit logs contain false attribution; commands executed in `./workspaces` can inadvertently modify the host DevOpsHub application repository.
- **Remediation:** Modify [`GitService.java:191`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/git/GitService.java#L191) to verify that `repoDir` contains an immediate child `.git` directory (`new File(repoDir, ".git").isDirectory()`) before running `git rev-parse HEAD`. If absent, return `"UNKNOWN"`. Store workspaces in an isolated temporary location outside the repository tree.
- **Regression Test Required:** `GitServiceTest.testSubdirectoryWithoutGitDoesNotTraverseToParent()`.

### **ARCH-02 (HIGH): Execution Engine Ignores Workspace Initialization / Clone Failure**
- **Affected Capability:** Pipeline Execution Engine / Error Recovery
- **Evidence:** In `AuditAdversarialRunner` Scenario 3.1, `cloneOrFetch` failed with exit code 128. `ExecutionEngine` logged a warning and proceeded to execute stage 1 against the deleted directory.
- **Reproduction:** Set project repository URL to an invalid URL (`https://invalid.example.com/repo.git`). Run pipeline.
- **Impact:** Pipelines run against missing workspaces, producing confusing downstream failures or operating against stale files from older runs.
- **Remediation:** In [`ExecutionEngine.java:224`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L224), if `gitRes.getExitCode() != 0`, set `execution.setStatus(ExecutionStatus.FAILED)`, mark the first stage failed, and abort execution immediately.
- **Regression Test Required:** `ExecutionEngineTest.testPipelineAbortsImmediatelyWhenCloneFails()`.

### **DAT-01 (HIGH): Ephemeral In-Memory H2 Fallback Causes Silent Data Loss on Exit**
- **Affected Capability:** Database Persistence / Fallback Mode
- **Evidence:** Verified in `IndependentPersistenceAuditTest` and `AuditAdversarialRunner`: resetting the database pool in H2 mode permanently wipes all created projects, pipelines, and logs.
- **Reproduction:** Stop MySQL. Start DevOpsHub. Create a project and pipeline. Quit the application. Re-launch. All data is gone.
- **Impact:** Users running DevOpsHub without a pre-configured MySQL Docker container lose all work upon application restart.
- **Remediation:** Configure H2 fallback as an embedded disk-backed database: `jdbc:h2:file:~/.devopshub/data/devopshub;MODE=MySQL;DB_CLOSE_DELAY=-1`.
- **Regression Test Required:** `DatabaseManagerTest.testFileBackedH2PersistenceAcrossRestarts()`.

---

## H. End-to-End Primary Workflow Trace

```mermaid
flowchart TD
    UI["1. User submits Repo URL & Name in UI"] -->|Input Validation| VAL["2. ProjectsView validates format"]
    VAL -->|Project Creation| DB1["3. Saved to MySQL projects table"]
    DB1 -->|Inspect Directory/Git| IMP["4. ProjectImportService detects project type"]
    IMP -->|Generate Stages| PIP["5. Auto-generates stages & saves pipeline"]
    PIP -->|Run Pipeline Click| ENG["6. ExecutionEngine.startExecution()"]
    ENG -->|Check Repository| CHK{"Has Git URL & no .git?"}
    CHK -->|Yes| CLN["7. GitService.cloneOrFetch()"]
    CHK -->|No| WRK["7b. Uses existing workspace or ./workspaces"]
    CLN -->|Exit != 0| FLW["BUG: Logs warning, continues!"]
    CLN -->|Exit == 0| REV["8. GitService.getCurrentCommitHash()"]
    FLW --> RUN
    REV --> RUN["9. StageRunner invokes ProcessExecutor"]
    RUN -->|Spawn OS Process| SUB["10. ProcessBuilder executes command"]
    SUB -->|Stream Output| LOG["11. LogCollector captures stdout/stderr"]
    SUB -->|Process Finishes| EXT["12. Captures exit code (0, 42, 124, 127)"]
    EXT -->|Non-Zero Exit| DIAG["13. FailureRuleEngine diagnoses failure"]
    EXT -->|Exit == 0| ART["14. Discovers artifact (.next, .jar)"]
    DIAG --> UPD["15. ExecutionRepository updates DB"]
    ART --> UPD
    UPD --> UI_VIEW["16. ExecutionDetailView & LogViewerView update"]
```

### Trace Step Evaluation

| Step | Operation | Implementation Reference | Observed Result | Status |
|:---:|---|---|---|:---:|
| 1 | URL Input & Project Creation | [`ProjectsView.java:621`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/ProjectsView.java#L621) | Form collects name, URL, branch, type | **VERIFIED** |
| 2 | Input Validation | [`GitService.java:62`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/git/GitService.java#L62) | Rejects empty strings and malformed formats | **VERIFIED** |
| 3 | Project Persistence | [`ProjectRepository.java:31`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/repository/ProjectRepository.java#L31) | Project record saved to MySQL `projects` table | **VERIFIED** |
| 4 | Project Type Detection | [`ProjectImportService.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/service/ProjectImportService.java) | Accurately identifies Next.js / Java / Python | **VERIFIED** |
| 5 | Pipeline Stage Generation | [`ProjectImportService.java:189`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/service/ProjectImportService.java#L189) | Generates `npm ci`, `npm run lint`, `npm run build` | **VERIFIED** |
| 6 | Execution Engine Launch | [`ExecutionEngine.java:75`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L75) | Creates `Execution` and `StageExecution` records | **VERIFIED** |
| 7 | Git Clone / Synchronization | [`GitService.java:83`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/git/GitService.java#L83) | Clones remote repo; deletes directory on failure | **PARTIALLY VERIFIED** |
| 8 | Commit Hash Resolution | [`GitService.java:190`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/git/GitService.java#L190) | Resolves commit; traverses to host repo if empty | **PARTIALLY VERIFIED** |
| 9 | Subprocess Command Execution | [`ProcessExecutor.java:34`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ProcessExecutor.java#L34) | Spawns `/bin/sh -c <cmd>` with host user rights | **VERIFIED** |
| 10 | Real-Time Log Collection | [`LogCollector.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/LogCollector.java) | Streams lines to memory buffer & DB | **VERIFIED** |
| 11 | Exit Code & Timeout Handling | [`ProcessExecutor.java:99`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ProcessExecutor.java#L99) | Correctly handles 0, 42, 127, 124, 130 | **VERIFIED** |
| 12 | Failure Diagnosis | [`FailureRuleEngine.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/analysis/FailureRuleEngine.java) | Identifies rule match and recommends remediation | **VERIFIED** |
| 13 | Artifact Discovery | [`ExecutionEngine.java:467`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/execution/ExecutionEngine.java#L467) | Verifies physical `.next` or target `.jar` | **VERIFIED** |
| 14 | Database State Finalization | [`ExecutionRepository.java:115`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/repository/ExecutionRepository.java#L115) | Updates status, duration, artifact path, commit | **VERIFIED** |
| 15 | UI Real-Time Presentation | [`ExecutionDetailView.java`](file:///Users/kaviselvaramkathirvel/javacicd/src/main/java/com/devopshub/ui/view/ExecutionDetailView.java) | Live terminal log update via listeners | **VERIFIED** |

---

## I. Visual and User Interface Verification

Direct un-occluded snapshots of all 9 primary application screens were captured and preserved:

````carousel
![01 Home View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/01_home_view.png)
<!-- slide -->
![02 Dashboard View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/02_dashboard_view.png)
<!-- slide -->
![03 Projects View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/03_projects_view.png)
<!-- slide -->
![04 Pipelines View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/04_pipelines_view.png)
<!-- slide -->
![05 Pipeline Builder View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/05_pipeline_builder_view.png)
<!-- slide -->
![06 Execution History View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/06_execution_history_view.png)
<!-- slide -->
![07 Log Viewer View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/07_log_viewer_view.png)
<!-- slide -->
![08 Analytics View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/08_analytics_view.png)
<!-- slide -->
![09 Settings View](/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/09_settings_view.png)
````

### UI Inspection Breakdown

1. **Home Screen ([`01_home_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/01_home_view.png)):** Welcome hero banner, environment health telemetry (MySQL ping latency, runtime pool sizes), and quick project shortcuts are fully functional.
2. **Dashboard ([`02_dashboard_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/02_dashboard_view.png)):** KPI cards (280 Projects, 527 Pipelines, Success Rate) are derived dynamically from MySQL aggregate queries. Team operational cards reflect actual stage execution records.
3. **Projects Grid & Table ([`03_projects_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/03_projects_view.png)):** Card view with project avatars, type filtering, search bar, and CRUD modals. Double-clicking a card opens `ProjectDetailView`.
4. **Pipelines Table ([`04_pipelines_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/04_pipelines_view.png)):** Lists pipelines with stage count, run history, and last execution status pills. Clicking "Run" triggers asynchronous pipeline execution and navigates to live monitoring.
5. **Pipeline Builder ([`05_pipeline_builder_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/05_pipeline_builder_view.png)):** Visual DAG stage hierarchy, timeline scale tabs, command parameter configuration, and dependency checklist.
6. **Execution History Kanban ([`06_execution_history_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/06_execution_history_view.png)):** Four status columns ("User story:", "To do:", "In progress:", "Done:"). Cards link directly to `ExecutionDetailView`. (Drag-and-drop is read-only).
7. **Log Viewer ([`07_log_viewer_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/07_log_viewer_view.png)):** Two-row toolbar with execution dropdown, stage filter, log level filter, keyword search, copy to clipboard, and export to `.log` file.
8. **Analytics ([`08_analytics_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/08_analytics_view.png)):** Pie chart of execution outcomes, bar chart of failure rules, and failing pipeline hotspots.
9. **Settings ([`09_settings_view.png`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/09_settings_view.png)):** Database host/port configuration, pool settings, workspace root, webhook server toggle, and scheduler status.

---

## J. Test Suite & Testing Infrastructure Audit

### Surefire Test Execution Metrics
- **Clean Execution Command:** `mvn test`
- **Exit Code:** `0`
- **Execution Duration:** `32.419 seconds`
- **Total Tests Run:** `83`
- **Total Failures:** `0`
- **Total Errors:** `0`
- **Total Skipped:** `0`
- **Test Discovery Framework:** JUnit Platform Provider / Maven Surefire Plugin 3.2.5

### Reconciled Test Inventory Across All 19 Test Classes

| # | Test Suite Class | Tests Run | Failures | Errors | Skips | Time (s) | Execution Nature |
|:---:|---|:---:|:---:|:---:|:---:|:---:|---|
| 1 | `com.devopshub.analysis.FailureRuleEngineTest` | 6 | 0 | 0 | 0 | 0.004 | Rule Regex & Diagnosis Parsing |
| 2 | `com.devopshub.config.AppConfigTest` | 2 | 0 | 0 | 0 | 0.001 | `.env` Loading & Fallbacks |
| 3 | `com.devopshub.deployment.DeploymentServiceTest` | 3 | 0 | 0 | 0 | 0.009 | Real File Copy & SHA-256 Checksum |
| 4 | `com.devopshub.execution.EndToEndWorkflowIntegrationTest` | 3 | 0 | 0 | 0 | 0.543 | Real Process Execution in Temp Dirs |
| 5 | `com.devopshub.execution.ExecutionEngineTest` | 5 | 0 | 0 | 0 | 1.798 | Real Subprocesses & DAG Validation |
| 6 | `com.devopshub.execution.LifecycleEndToEndAuditTest` | 7 | 0 | 0 | 0 | 1.615 | Real OS Subprocesses (Exit 0, 42, 127, 124, 130) |
| 7 | `com.devopshub.execution.RealLairIntegrationTest` | 4 | 0 | 0 | 0 | 24.420 | Real Next.js 16 Clone, Lint, Build & Artifact |
| 8 | `com.devopshub.git.GitServiceTest` | 6 | 0 | 0 | 0 | 1.119 | Real Git CLI (clone, diff, branch, scrub) |
| 9 | `com.devopshub.model.DomainModelTest` | 5 | 0 | 0 | 0 | 0.003 | Domain Object Serialization & Enums |
| 10 | `com.devopshub.pipeline.PipelineValidatorTest` | 6 | 0 | 0 | 0 | 0.006 | DAG Cycle Detection & Stage Ordering |
| 11 | `com.devopshub.repository.DatabaseIntegrationTest` | 10 | 0 | 0 | 0 | 0.081 | Real MySQL Transactions & Constraint Rollback |
| 12 | `com.devopshub.repository.IndependentPersistenceAuditTest` | 3 | 0 | 0 | 0 | 0.108 | Connection Pool Reset Durability Checks |
| 13 | `com.devopshub.scheduler.PipelineSchedulerServiceTest` | 2 | 0 | 0 | 0 | 0.027 | Thread Scheduling (Isolated Service Test) |
| 14 | `com.devopshub.service.DashboardServiceTest` | 2 | 0 | 0 | 0 | 0.019 | DB Metric Aggregations |
| 15 | `com.devopshub.service.ProjectImportServiceTest` | 6 | 0 | 0 | 0 | 0.012 | Project Inspector & ZipSlip Security Check |
| 16 | `com.devopshub.service.ProjectServiceTest` | 4 | 0 | 0 | 0 | 0.011 | CRUD Service Validation |
| 17 | `com.devopshub.ui.ModalSnapshotTest` | 1 | 0 | 0 | 0 | 1.221 | Headless JavaFX Scene Rendering |
| 18 | `com.devopshub.util.SecretMaskerTest` | 4 | 0 | 0 | 0 | 0.004 | Credential Scrubbing Regex Checks |
| 19 | `com.devopshub.webhook.WebhookServerServiceTest` | 4 | 0 | 0 | 0 | 0.445 | Real HTTP Server (POST /api/webhook/trigger) |
| **TOTAL** | **19 Test Classes** | **83** | **0** | **0** | **0** | **32.4s** | **100% Pass Rate** |

### Test Rigor Assessment
- **Zero Mock Injection in Core Runners:** `ProcessExecutor`, `StageRunner`, and `GitService` spawn genuine OS subprocesses. No mock processes or simulated exit codes are injected.
- **Critical Gap in Test Assertions:** `ExecutionEngineTest` never asserted that a failed Git clone aborts the pipeline. That gap allowed the engine defect (continuing after failed clone) to remain hidden until this audit.

---

## K. Release Decision & Independent Release Gate

### Evaluation Against Mandatory Release Gate Requirements

| # | Mandatory Release Gate Criterion | Verification Result | Verdict |
|:---:|---|---|:---:|
| 1 | Real UI can initiate Git clone into correct workspace | Clones into `projects/<id>` under workspace root | **PASS** |
| 2 | Checked-out commit is independently confirmed | Matches remote HEAD (`d2b7ca7...` on L/Air) | **PASS** |
| 3 | Application executes actual commands in repository | Executes real `npm`, `sh`, `java` commands via `ProcessBuilder` | **PASS** |
| 4 | Real stdout, stderr, exit codes, timeouts, cancellation handled | Handled correctly across all controlled test scenarios | **PASS** |
| 5 | Controlled successful pipeline reports success | Verified in `LifecycleEndToEndAuditTest` & `AuditAdversarialRunner` | **PASS** |
| 6 | Controlled failing pipeline reports failure | Verified with deliberate exit codes (23, 42, 127) | **PASS** |
| 7 | Downstream stages follow actual dependency rules | Verified: Stage 2 skipped when Stage 1 fails | **PASS** |
| 8 | Artifact collection verifies real physical files | Verified: `.next/BUILD_ID` and `target/*.jar` checked on disk | **PASS** |
| 9 | Results and logs persist across application restarts | Verified for MySQL 8.0; fails for in-memory H2 fallback | **CONDITIONAL PASS (MySQL only)** |
| 10 | UI displays truthful results matching execution engine | Verified: UI status badges match DB execution status | **PASS** |
| 11 | Packaged application launches on supported platforms | Verified standalone on macOS ARM64; fails on thin JAR | **CONDITIONAL PASS (Fat JAR, macOS only)** |
| 12 | No unresolved P0 security issue for declared scope | **FAILED:** Unrestricted host command execution exists | **FAIL (BLOCKED FOR MULTI-TENANT)** |
| 13 | All mandatory tests actually ran without skips | 83/83 tests executed; 0 skipped | **PASS** |
| 14 | Existing user work & external test repo remain untouched | External L/Air repo strictly read-only; user repo pristine | **PASS** |
| 15 | Every release claim has reproducible evidence trail | Backed by raw logs, transcripts, and database queries | **PASS** |

### Final Release Determination: **CONDITIONAL RELEASE FOR TRUSTED LOCAL DEVELOPER WORKSTATIONS**

#### Authorized Scope:
- **Local Single-User Desktop Workstation:** Permitted for individual developers running trusted repositories on macOS Apple Silicon with a local MySQL instance.
- **Controlled Demonstrations:** Permitted for internal demonstrations and evaluation of Next.js/Java automated pipelines.

#### Strictly Prohibited Scope:
- **Multi-Tenant or Public SaaS Deployments:** **STRICTLY PROHIBITED.** The application has no container sandboxing. Any user with pipeline access has full control over the host workstation.
- **Untrusted Repositories / Third-Party PRs:** **STRICTLY PROHIBITED.** Malicious repository code will execute with the full privileges of the host user.
- **Production Webhook Exposure:** **STRICTLY PROHIBITED.** The webhook server lacks HMAC verification and replay protection. Exposing port 8089 to the public internet enables unauthorized command execution.

---

## L. Prioritized Remediation Plan

### **Priority 0 (P0) — Critical Security & Integrity Boundaries**
1. **Containerized Worker Isolation:** Wrap `ProcessExecutor` in a containerized runner (e.g. Docker CLI or containerd) executing inside disposable containers with non-root privileges, read-only root filesystems, and bounded CPU/memory limits.
2. **True HMAC-SHA256 Webhook Verification:** Replace plaintext token check in `WebhookServerService` with constant-time HMAC-SHA256 verification of the raw request payload against the `X-Hub-Signature-256` header, and enforce a 5-minute timestamp replay window.

### **Priority 1 (P1) — Core Pipeline Workflow Integrity**
3. **Strict Git Directory Isolation:** Enforce that `git rev-parse HEAD` only executes if an immediate `.git` child folder exists in the target directory, preventing upward traversal into the DevOpsHub repository.
4. **Immediate Pipeline Abort on Clone Failure:** In `ExecutionEngine.java`, immediately abort execution and mark status as `FAILED` if `cloneOrFetch` returns a non-zero exit code.
5. **Persistent File-Backed H2 Fallback:** Reconfigure H2 fallback to embedded disk storage (`jdbc:h2:file:~/.devopshub/data/devopshub`) so users without MySQL do not suffer total data loss upon quitting the application.

### **Priority 2 (P2) — Functional & UI Wire-Up**
6. **Connect Automated Scheduler to UI:** Add schedule configuration inputs (interval / cron expression) to `PipelineBuilderView` and `PipelineDetailView` that invoke `PipelineSchedulerService.schedulePipeline()`.
7. **Cross-Platform Release Profiles:** Configure Maven build profiles to assemble native OpenJFX binaries for Windows x86_64, Linux x86_64, and macOS Intel.

### **Priority 3 (P3) — UI Usability & Polish**
8. **Wire Header Search Bar:** Connect `searchInput` in `MainLayoutView` to active view filter models.
9. **Analytics View Dark Mode Contrast:** Update text-fill styling of `summary1..3` in `AnalyticsView.java` from dark slate (`#334155`) to readable muted slate (`#94A3B8`).

---

## M. Evidence Index & Reproducibility Guide

All raw artifacts, logs, and evidence supporting this audit are cataloged below:

| Evidence File | Path | Description |
|---|---|---|
| **Baseline Environment Manifest** | [`baseline_manifest.txt`](file:///Users/kaviselvaramkathirvel/javacicd/docs/engineering/audits/audit-20261025-191100/evidence/baseline_manifest.txt) | Host OS, Git HEAD, tool versions, and pre-existing diff state |
| **Maven Test Log** | [`mvn_test_full.log`](file:///Users/kaviselvaramkathirvel/javacicd/docs/engineering/audits/audit-20261025-191100/evidence/mvn_test_full.log) | Complete terminal transcript of 83 passing tests across 19 suites |
| **Surefire Reports Archive** | [`surefire_reports.tar.gz`](file:///Users/kaviselvaramkathirvel/javacicd/docs/engineering/audits/audit-20261025-191100/evidence/surefire_reports.tar.gz) | Compressed XML and text test summaries from Surefire |
| **Adversarial Runtime Evidence** | [`runtime_audit_execution_evidence.txt`](file:///Users/kaviselvaramkathirvel/javacicd/docs/engineering/audits/audit-20261025-191100/evidence/runtime_audit_execution_evidence.txt) | Output of `AuditAdversarialRunner` (MySQL durability, Git clone, exit codes, security probe) |
| **Adversarial Test Script** | [`AuditAdversarialRunner.java`](file:///Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/scratch/AuditAdversarialRunner.java) | Standalone harness executing controlled scenarios |
| **UI Screenshots (Archive)** | [`evidence/screenshots/`](file:///Users/kaviselvaramkathirvel/javacicd/docs/engineering/audits/audit-20261025-191100/evidence/screenshots) | 9 un-occluded JavaFX scene graph snapshots |

### How to Reproduce Core Results

1. **Reproduce the 83/83 Test Suite:**
   ```bash
   mvn test
   ```
2. **Reproduce the Standalone Executable Packaging & Launch:**
   ```bash
   mvn package -DskipTests
   java -jar target/devopshub-desktop-1.0.0-jar-with-dependencies.jar
   ```
3. **Reproduce the Adversarial Runtime Audit:**
   ```bash
   javac -cp target/devopshub-desktop-1.0.0-jar-with-dependencies.jar /Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/scratch/AuditAdversarialRunner.java -d /Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/scratch
   java -cp target/devopshub-desktop-1.0.0-jar-with-dependencies.jar:/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/89b8c290-024b-4280-a70a-9565503b64d5/scratch com.devopshub.audit.AuditAdversarialRunner
   ```
4. **Reproduce the Webhook Plaintext Token Vulnerability:**
   ```bash
   # Test invalid token: Returns 401
   curl -i -X POST http://localhost:8089/api/webhook/trigger -H "Content-Type: application/json" -d '{"pipelineId": "test", "token": "wrong"}'
   # Test default secret token without HMAC: Accepted (Returns 404 for pipeline lookup)
   curl -i -X POST http://localhost:8089/api/webhook/trigger -H "Content-Type: application/json" -d '{"pipelineId": "nonexistent", "token": "devopshub-secret-token"}'
   ```
5. **Reproduce the Parent Git Traversal Bug:**
   ```bash
   git -C ./workspaces rev-parse HEAD
   # Output: da9301275fbe82414ec53cef9bd0706ed8458d93 (DevOpsHub's commit hash)
   ```

---
*Report certified by Independent Systems Auditor on October 25, 2026.*
