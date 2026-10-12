# DevOpsHub Pipeline Execution Engine

## 1. Engine Architecture & Responsibilities
The Pipeline Execution Engine (`com.devopshub.execution.ExecutionEngine`) is the orchestration core of DevOpsHub. It is responsible for taking a validated pipeline definition and executing its stages across real subprocesses on the host system while enforcing timeouts, dependency sequencing, parallel branching, log streaming, and persistence.

```
┌──────────────────────────────────────────────────────────┐
│                   Pipeline Definition                    │
│   Stage 1 (Checkout) ──> Stage 2 & Stage 3 (Parallel)    │
│                        └──> Stage 4 (Deploy)             │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│                     ExecutionEngine                      │
│ - Topological sort & stage partitioning                  │
│ - Creates top-level Execution record in MySQL            │
│ - Submits stages to ExecutionThreadPool                  │
└────────────────────────────┬─────────────────────────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
┌───────────────────────────┐ ┌───────────────────────────┐
│     StageRunner (Seq)     │ │ ParallelStageCoordinator  │
│  - Executes Process       │ │  - CompletableFuture.     │
│  - Captures exit code     │ │    allOf()                │
│  - Streams logs           │ │  - Concurrent StageRunners│
└─────────────┬─────────────┘ └─────────────┬─────────────┘
              │                             │
              └──────────────┬──────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│                  FailureRuleEngine (if exit != 0)         │
│ - Ingests stdout/stderr bounded logs                     │
│ - Matches diagnostic pattern rules                       │
│ - Produces FailureDiagnosis & Recovery Recommendation    │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Stage Execution Flow
1. **Pre-execution Validation:**
   - `PipelineValidator` verifies that the pipeline has at least one stage and that no circular dependencies exist using a Depth-First Search (DFS) graph cycle detector.
2. **Execution Initialization:**
   - An execution ID is generated (`UUID.randomUUID()`).
   - A monotonic `build_number` is assigned.
   - Initial status is recorded as `RUNNING` in `executions`.
3. **Stage Processing:**
   - Stages are topologically sorted and partitioned into sequential and parallel groups based on their dependencies.
   - For sequential stages, each stage runs to completion before the next begins.
   - For parallel stages, `ParallelStageCoordinator` launches concurrent `StageRunner` instances using Java's `CompletableFuture` on a bounded Fork/Join thread pool.
4. **Log Streaming:**
   - Subprocess stdout and stderr are ingested line-by-line via `ProcessExecutor`.
   - Lines are broadcast to in-memory listeners for live JavaFX rendering and saved to `execution_logs` in MySQL.
5. **Post-execution Lifecycle:**
   - If any stage exits with a non-zero code or times out:
     - The stage status is marked `FAILED`.
     - Remaining dependent stages are marked `SKIPPED`.
     - The top-level execution status is marked `FAILED`.
     - `FailureRuleEngine.analyze()` is invoked to diagnose the failure.
   - If all stages complete with exit code 0:
     - The execution status is marked `SUCCESS`.

---

## 3. Subprocess Sandboxing & Safety
- Commands are executed via Java's `ProcessBuilder`.
- Each process has an enforced timeout (default 300 seconds, customizable per stage).
- Process streams are bounded in memory by `BoundedLogBuffer` (max 5,000 lines per stage) to prevent out-of-memory errors from runaway process loops.
- Interrupted or timed-out processes are forcefully destroyed via `Process.destroyForcibly()`.
