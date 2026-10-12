# DevOpsHub Concurrency & Multithreading Model

## 1. Concurrency Architecture
DevOpsHub is a multi-threaded desktop application designed to handle high-throughput CI/CD workflows without degrading UI responsiveness. Concurrency is applied across three distinct layers:
1. **The JavaFX Application Thread:** Dedicated solely to rendering, scene graph updates, and user event handling.
2. **The Execution Thread Pool:** Dedicated to orchestrating pipeline runs and waiting on subprocess completion.
3. **The Subprocess Stream Readers:** Dedicated daemon threads capturing standard output and error without deadlocking OS pipe buffers.

---

## 2. Thread Pools & Executors
Managed by `com.devopshub.execution.ExecutionThreadPool`:

| Pool Name | Implementation | Size / Policy | Purpose |
|---|---|---|---|
| Pipeline Pool | `ThreadPoolExecutor` | Fixed (4 threads) | Orchestrates concurrent pipeline executions |
| Stage Pool | `ForkJoinPool` / `CompletableFuture` | Work-stealing (up to CPU cores) | Executes parallel stages within a single pipeline |
| Log Streaming | Cached daemon threads | Unbounded daemon | Reads process stdout/stderr streams concurrently |

### Clean Shutdown:
When the application closes (`MainApp.stop()`), `ExecutionThreadPool.shutdown()` is called:
1. Rejects new pipeline submissions.
2. Awaits termination of ongoing stages for up to 5 seconds.
3. Issues `shutdownNow()` and interrupts active subprocesses if graceful timeout expires.

---

## 3. Parallel Stage Execution via `CompletableFuture`
When a pipeline contains stages marked with `ExecutionMode.PARALLEL` and mutual independence (neither stage depends on the other), `ParallelStageCoordinator` executes them concurrently:

```java
List<CompletableFuture<StageExecution>> futures = parallelStages.stream()
    .map(stage -> CompletableFuture.supplyAsync(() -> stageRunner.runStage(execution, stage), executor))
    .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

If any parallel task fails, the remaining dependent stages are safely skipped, and the failure status propagates deterministically.

---

## 4. Race Condition Prevention & Synchronization
- **Atomic Operations:** Monotonic build numbers and execution counters use atomic integers and database transactions.
- **CopyOnWriteArrayList:** In-memory execution listeners use `CopyOnWriteArrayList` to allow safe registration and unregistration of UI listeners while events are being dispatched.
- **Bounded Log Buffers:** `BoundedLogBuffer` wraps thread-safe concurrent queues with a strict upper bound to prevent heap exhaustion.
- **UI Marshalling:** All callbacks originating from worker threads marshal state transitions to the JavaFX Application Thread via `Platform.runLater()`.
