# DevOpsHub — End-to-End Data Flow Architecture

This document maps the flow of data through DevOpsHub during pipeline configuration, execution, logging, intelligence analysis, and visualization.

---

## 1. End-to-End Execution & Diagnostic Data Flow

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant UI as ExecutionDetailView
    participant Engine as ExecutionEngine
    participant Coord as ParallelCoordinator
    participant Runner as StageRunner
    participant Proc as ProcessExecutor
    participant Log as LogCollector
    participant DB as MySQL Database
    participant Intel as FailureRuleEngine

    Dev->>UI: Click "Run Pipeline"
    UI->>Engine: startExecution(pipeline, MANUAL)
    Engine->>DB: INSERT into executions (status=RUNNING)
    Engine->>UI: Return active Execution
    
    loop For Each Stage / Parallel Fork
        alt Sequential Stage
            Engine->>Runner: call()
            Runner->>Proc: execute(command, timeout)
            Proc-->>Log: Stream stdout & stderr lines
            Log->>DB: Batch INSERT into execution_logs
            Log-->>UI: Real-time Platform.runLater() append
            Proc-->>Runner: Exit code (e.g. 1)
            Runner->>DB: UPDATE stage_executions (status=FAILED)
        else Parallel Fork Group
            Engine->>Coord: executeParallel([RunnerA, RunnerB])
            Coord-->>Proc: Concurrently execute subprocesses
            Proc-->>Log: Concurrent log stream collection
            Coord-->>Engine: Join all stage futures
        end
    end

    opt Stage Failed
        Engine->>Intel: analyze(failedStage, logs)
        Intel->>Intel: Match rules (e.g. AssertionFailureRule)
        Intel->>DB: INSERT into failure_diagnoses
        Engine->>UI: Attach FailureDiagnosis
    end

    Engine->>DB: UPDATE executions (status=SUCCESS/FAILED, duration)
    Engine-->>UI: Final Execution update event
    UI->>Dev: Render Stage Cards, Diagnosis Panel & Next Action
```

---

## 2. Log Data Flow Pipeline

1. **Generation:** Subprocesses launched by `ProcessExecutor` emit raw character bytes to their stdout and stderr output streams.
2. **Concurrent Capture:** Two dedicated reader threads in `ProcessExecutor` drain the input streams simultaneously using `BufferedReader` configured with `StandardCharsets.UTF_8`.
3. **Normalization & Timestamping:** `LogCollector` prefixes each message with millisecond-precision timestamps (`[HH:mm:ss.SSS]`) and assigns an appropriate severity level (`INFO` or `ERROR`).
4. **Circular In-Memory Buffering:** Formatted records are stored in `BoundedLogBuffer` (max capacity: 5,000 entries). When full, the oldest lines are evicted, preventing memory leaks.
5. **UI Streaming:** Registered UI listeners receive instant push updates dispatched via JavaFX `Platform.runLater()`, keeping the desktop terminal at 60fps without lag.
6. **Durable Persistence:** Every 50 entries (or immediately upon stage termination), logs are committed to the `execution_logs` MySQL table in bulk batches using JDBC transactions.
