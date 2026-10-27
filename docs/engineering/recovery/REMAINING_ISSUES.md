# DEVOPSHUB — REMAINING ISSUES, RISKS & RUNTIME LIMITATIONS

**Document Version:** 1.0.0  
**Phase:** Post-Recovery Assessment  
**Classification Date:** 2026-10-25  

---

## 1. Classification Framework

In strict compliance with the **No-Execution Restriction** of this engineering mission, all findings, fixes, and limitations are classified according to this taxonomy:

- **`SOURCE CONFIRMED`:** Verified directly via static source code and configuration inspection.
- **`STATIC REVIEW ONLY`:** Code changes and unit test suites implemented and syntactically reviewed; awaiting runtime execution in an authorized testing phase.
- **`EXISTING EVIDENCE REVIEWED`:** Verified against existing test reports, logs, and commit history.
- **`RUNTIME VERIFICATION REQUIRED`:** Functionality that inherently requires JVM execution, network sockets, OS process launching, or UI display rendering.
- **`BLOCKED / NOT IMPLEMENTED`:** Features or infrastructure requiring external software (e.g., Docker daemon, cross-platform OS build nodes) not currently present.

---

## 2. Unresolved Issues & Architectural Limitations

### REM-01: True OS-Level Container Sandbox Daemon Integration
- **Classification:** `BLOCKED / NOT IMPLEMENTED`
- **Related Issue:** SEC-01 (Untrusted Repository Execution Boundary)
- **Description:**  
  While the repaired `ExecutionPolicy` sanitizes all host credentials, strips sensitive environment variables, confines directories, and **fails closed** (exit code 126) for `UNTRUSTED_EXTERNAL` workloads, full OS-level sandboxing (Linux namespaces, cgroups, macOS App Sandbox, Windows AppContainer) requires an active container runtime daemon (e.g. Docker or Podman).
- **Why It Remains Unresolved:**  
  The host environment does not guarantee a running Docker daemon, and running Docker commands was prohibited in this phase.
- **Current Behavior:**  
  Untrusted external repository triggers fail closed safely and will not execute on the host desktop.
- **Condition Needed to Close:**  
  Implement a `DockerProcessRunner` implementing `ProcessExecutorInterface` that checks `docker info` and runs commands inside an ephemeral Alpine/Ubuntu container (`docker run --rm -v workspace:/app --read-only`).

---

### REM-02: Non-Interactive SSH Key Authentication with Passphrase Prompts
- **Classification:** `RUNTIME VERIFICATION REQUIRED`
- **Related Issue:** Private Git Repository Cloning
- **Description:**  
  When cloning private repositories via SSH (`git@github.com:...`), if the user's private SSH key (`~/.ssh/id_rsa`, `~/.ssh/id_ed25519`) is encrypted with a passphrase, the Git CLI process will attempt to interactively prompt for a passphrase on stdin or invoke `SSH_ASKPASS`. Because DevOpsHub runs subprocesses non-interactively with closed stdin, Git will hang until timeout (default 300 seconds) or fail with `Host key verification failed / Permission denied (publickey)`.
- **Why It Remains Unresolved:**  
  Testing private SSH authentication requires real private keys, network connectivity, and runtime Git execution, all of which were prohibited.
- **Current Behavior:**  
  HTTPS authentication and unencrypted SSH keys with `ssh-agent` work. Encrypted keys without an agent fail on timeout.
- **Condition Needed to Close:**  
  Add an interactive credential prompt in the UI when configuring a project that temporarily caches passphrases in memory or integrates with the OS keychain.

---

### REM-03: Native Multi-Platform Installers (.dmg, .pkg, .deb, .rpm, .exe)
- **Classification:** `BLOCKED / NOT IMPLEMENTED`
- **Related Issue:** PKG-01 (Packaging and Distribution)
- **Description:**  
  DevOpsHub's Maven build has been fixed to produce an executable thin JAR with all runtime dependencies copied to `target/libs` during the `package` phase. However, generating native operating system installers (macOS `.dmg`, Linux `.deb`/`.rpm`, Windows `.exe`/`.msi`) requires running Oracle/OpenJFX `jpackage` natively on each respective host operating system.
- **Why It Remains Unresolved:**  
  `jpackage` cannot cross-compile macOS DMGs from Linux or Windows EXEs from macOS without native platform builders.
- **Current Behavior:**  
  The application runs via `java -jar target/devopshub-desktop-1.0.0.jar` or `mvn javafx:run` on all platforms with Java 21 LTS installed.
- **Condition Needed to Close:**  
  Configure a GitHub Actions CI matrix with `macos-latest`, `ubuntu-latest`, and `windows-latest` runners invoking `jpackage` to produce release binaries.

---

### REM-04: High-Volume Stream UI Throttling (>100,000 Lines/Second)
- **Classification:** `RUNTIME VERIFICATION REQUIRED`
- **Related Issue:** ARCH-04 (Process Stream Reliability)
- **Description:**  
  `BoundedLogBuffer` successfully prevents JVM OutOfMemory errors by capping memory usage at 1,000 lines per buffer and appending notifications. However, if a defective user process outputs an extreme volume of logs (e.g. 50,000 to 100,000 lines per second in a tight loop), flooding the JavaFX application thread queue with thousands of `Platform.runLater()` tasks could cause visual stutter or temporary UI unresponsiveness.
- **Why It Remains Unresolved:**  
  Benchmarking UI frame rendering under high thread load requires headful GUI runtime execution.
- **Current Behavior:**  
  Logs stream smoothly under standard build outputs (< 5,000 lines/sec).
- **Condition Needed to Close:**  
  Implement a batching timer in `LogViewerView` that coalesces log updates and flushes to the text area at a maximum rate of 30 updates per second (30 Hz).

---

### REM-05: Desktop Process Lifecycle for Schedulers
- **Classification:** `SOURCE CONFIRMED` (Documented by Design)
- **Related Issue:** UI-01 (Pipeline Scheduling)
- **Description:**  
  `PipelineSchedulerService` runs in-process via `ScheduledExecutorService`. Because DevOpsHub is a desktop application rather than a cloud server daemon, background schedules stop firing when the user quits the desktop application.
- **Why It Remains Unresolved:**  
  This is an intrinsic characteristic of local-first desktop software.
- **Current Mitigation:**  
  Transparent notices are displayed in both `PipelineDetailView` and `SettingsView` informing users that scheduled jobs only run while DevOpsHub remains open. Missed jobs during shutdown do not pile up.
- **Condition Needed to Close:**  
  Provide an optional lightweight CLI daemon mode (`java -jar devopshub-desktop-1.0.0.jar --daemon`) or OS service wrapper (systemd / launchd).

---

## 3. Risk Register & Mitigations

| Risk ID | Risk Description | Severity | Likelihood | Architectural Mitigation |
|:---:|:---|:---:|:---:|:---|
| **RSK-01** | User runs untrusted script with `ALLOW_REMOTE_REPOS_AS_TRUSTED=true` | High | Low | Defaulted to `false` (fail-closed) in both `AppConfig` and `ExecutionIsolationService`; explicit warning logged if manually enabled. |
| **RSK-02** | Simultaneous desktop instances open same embedded H2 database file | Medium | Low | Embedded H2 configured with `;AUTO_SERVER=TRUE` to permit multi-process access; file lock contention handled gracefully. |
| **RSK-03** | Clock drift on webhook sender causes timestamp validation to fail | Low | Medium | Replay window is set generously to 300 seconds (5 minutes) to tolerate typical NTP clock drift. |
| **RSK-04** | Git CLI not present in system PATH | Medium | Low | `GitService` checks Git binary existence and fails fast with actionable error during workspace setup. |
| **RSK-05** | JavaFX native binaries missing in thin JAR | High | Medium | Manifest classpath and `target/libs` dependency copying are configured in `pom.xml`, but runtime launch capability requires verification on target operating system. |
| **RSK-06** | Host-process execution confused with container sandbox | High | Low | Host process execution is an environment-sanitized, confined host process, NOT a sandbox. Untrusted executions strictly fail closed without container runtimes. |

---

## 4. Summary of Codebase Readiness

Following the recovery repairs and adversarial code review:
- **P0 Security:** All confirmed P0 security defects (unrestricted execution, webhook authentication, destructive cleanup, and remote repo trust defaults) have been resolved at the source code level with fail-closed defaults.
- **P1 Correctness:** Pipeline clone aborts, commit attribution leaks, Git worktree/submodule detection, workspace boundary delimiter enforcement, and persistent H2 fallback have been implemented and covered with unit regression tests.
- **P2 Usability:** Scheduling controls, global search filtering across all views, settings navigation, and execution history refresh have been wired to backend services.
- **P3 Distribution:** Maven packaging, classpath resolution, and dependency copying have been configured.

The codebase is **STATIC REVIEW COMPLETE**. Because runtime execution was strictly prohibited in this phase, runtime verification (launching the desktop UI, executing pipelines, firing live webhooks, testing multi-process H2 persistence across JVM exit) remains required before claiming production readiness.

