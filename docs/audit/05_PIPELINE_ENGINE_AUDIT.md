# DevOpsHub — Pipeline Engine Reality Audit

**Audit Date:** 2026-10-23  
**Auditor:** Principal Software Architect, Senior Java Engineer, CI/CD Platform Engineer  

---

## 1. Domain Representation & Lifecycle Transitions

### Pipeline Entity & Data Model
- **Schema:** Stored in MySQL `pipelines`, `pipeline_stages`, and `stage_dependencies`.
- **Model:** Java entity `com.devopshub.model.Pipeline` with stable UUID identifier `id`, `project_id`, `name`, `description`, `execution_mode` (`SEQUENTIAL` or `PARALLEL_FORK`), `is_active`, and an ordered list of `Stage` entities.
- **Stage Model:** Java entity `com.devopshub.model.Stage` with `id`, `pipeline_id`, `name`, `stage_type` (`CHECKOUT`, `BUILD`, `TEST`, `SECURITY_SCAN`, `PACKAGE`, `DEPLOY`, `CUSTOM`), `stage_order`, `command`, `timeout_seconds`, `is_enabled`, and `depends_on_stage_ids`.

### Full Execution Lifecycle

```
[Create] -> [Validate DAG] -> [Save to MySQL] -> [Select Run] -> [Init Execution #N (RUNNING)]
    |
    v
[Git Clone / Fetch] -> [Stage 1: ProcessBuilder] -> [Stream Logs & Check Exit Code]
    |                                                      |
    |---- If Exit Code != 0 --------------------------------
    |       |
    |       v
    |   [Stage FAILED] -> [Intelligent Failure Rule Engine] -> [Save Diagnosis]
    |       |
    |       v
    |   [Downstream Stages SKIPPED] -> [Final Execution Status: FAILED]
    |
    +---- If Exit Code == 0
            |
            v
        [Stage SUCCESS] -> [Evaluate Next DAG Stage] -> ... -> [Final Status: SUCCESS]
```

---

## 2. Process Execution Architecture

### Process Invocation Mechanism
- **Class:** `com.devopshub.execution.ProcessExecutor` (`ProcessExecutor.java:30-144`).
- **OS Command Wrapper:**
  - Unix / macOS: `/bin/sh -c "<command>"`
  - Windows: `cmd.exe /c "<command>"`
- **Environment Management:** Automatically prepends essential system paths (`/opt/homebrew/bin`, `/usr/local/bin`, `/usr/bin`, `/bin`) to `PATH` so node, npm, mvn, javac, and git are always discoverable across macOS and Linux.
- **Working Directory:** Strictly set to `workspaces/projects/<projectId>/`. All stage commands operate against the checked-out codebase.

### Standard Output & Standard Error Isolation
- **Dual Async Stream Readers:** Two dedicated reader threads read stdout and stderr concurrently with `StandardCharsets.UTF_8`.
- **No Stream Deadlock:** `ProcessBuilder.redirectErrorStream(false)` ensures stdout and stderr are tagged distinctly in log entries (`LogStream.STDOUT` vs `LogStream.STDERR`).
- **Secret Redaction:** Commands and logs pass through `SecretMasker.mask()` to prevent bearer tokens, AWS keys, or private SSH keys from being written to stdout or persisted to MySQL.

---

## 3. Success & Failure Integrity

### Exit Code Inspection
- **Actual Process Status Checked:** Final status is directly derived from `process.exitValue()`.
- **Zero Exit Code = SUCCESS:** Only when exit code is `0` (and build artifacts are verified) is a stage marked `SUCCESS`.
- **Non-Zero Exit Code = FAILED:** If a compiler fails, a test fails, or a command is missing, the non-zero exit code (e.g. 1, 23, 127) immediately marks the stage `FAILED`.
- **Timeout = Code 124:** If execution exceeds `timeoutSeconds`, `ProcessExecutor` terminates the process tree, records code `124`, and triggers `RULE_TIMEOUT_05` in `FailureRuleEngine`.
- **Cancellation = Code 130:** If cancelled by user, `ProcessExecutor` kills all descendants, records code `130`, and marks status `CANCELLED`.
- **Downstream Skip Enforcement:** When a stage fails in `SEQUENTIAL` mode, all remaining stages are set to `SKIPPED` without running unvalidated commands.

---

## 4. Logging & Observability

- **Collector:** `com.devopshub.execution.LogCollector` maintains thread-safe bounded buffers.
- **UI Decoupling:** UI logs are batched via `Platform.runLater()` every 50ms, eliminating JavaFX UI thread freezing even under heavy build log bursts.
- **Persistence:** Logs are flushed to MySQL `execution_logs` using batched JDBC prepared statements with foreign key cascading to the parent execution.
- **Post-Restart Integrity:** Execution logs are durably saved in MySQL. Selecting any past execution in `ExecutionHistoryView` loads full historical logs from `execution_logs`.

---

## 5. Concurrency & Isolation

- **Pipeline Thread Pool:** Managed by `ExecutionThreadPool` with a fixed pool of 4 threads (`DevOpsHub-Pipeline-1..4`).
- **Stage Concurrency:** Up to 16 concurrent stage runners (`DevOpsHub-Stage-1..16`) for parallel DAG branches.
- **Isolation:** Each execution has a unique UUID `id`, its own `LogCollector`, and its own `StageExecution` records. Simultaneous runs do not cross-contaminate stdout/stderr streams or database records.
