# DevOpsHub — Module Architecture & Specifications

This document details the functional specifications, classes, and responsibilities of the five primary architectural modules defined in Slide 5 of the DevOpsHub specification.

---

## Module 1: User & Project Management

### Functional Responsibilities
- Create, read, update, and delete (CRUD) projects.
- Bind projects to remote or local Git repository URLs.
- Track project delivery health, active status, and pipeline count.

### Implementation Classes
- **Domain Model:** `com.devopshub.model.Project`
- **Data Access:** `com.devopshub.repository.ProjectRepository`
- **Business Logic:** `com.devopshub.service.ProjectService`
- **UI Screen:** `com.devopshub.ui.view.ProjectsView`

---

## Module 2: Pipeline Configuration & Validation

### Functional Responsibilities
- Define pipelines associated with projects.
- Configure individual stages: Name, Type, Command, Timeout, Enabled state.
- Establish dependencies between stages (Prerequisites).
- Validate stage graph acyclicity using Depth-First Search (DFS).

### Implementation Classes
- **Domain Model:** `com.devopshub.model.Pipeline`, `com.devopshub.model.Stage`
- **Validation Engine:** `com.devopshub.pipeline.PipelineValidator`
- **Data Access:** `com.devopshub.repository.PipelineRepository`
- **Business Logic:** `com.devopshub.service.PipelineService`
- **UI Screens:** `com.devopshub.ui.view.PipelinesView`, `com.devopshub.ui.view.PipelineBuilderView`

---

## Module 3: Execution Engine & Concurrency

### Functional Responsibilities
- Transition execution state: `QUEUED` ➔ `RUNNING` ➔ `SUCCESS` / `FAILED` / `CANCELLED`.
- Execute stages in topological dependency order.
- Orchestrate concurrent stages via `ParallelStageCoordinator` (Fork/Join).
- Safely execute processes via `ProcessExecutor`, enforcing timeouts and capturing exit codes.
- Handle graceful user aborts and cooperative cancellation.

### Implementation Classes
- **Orchestrator:** `com.devopshub.execution.ExecutionEngine`
- **Thread Pools:** `com.devopshub.execution.ExecutionThreadPool`
- **Parallel Coordinator:** `com.devopshub.execution.ParallelStageCoordinator`
- **Stage Runner:** `com.devopshub.execution.StageRunner`
- **Process Management:** `com.devopshub.execution.ProcessExecutor`
- **Data Access:** `com.devopshub.repository.ExecutionRepository`
- **UI Screen:** `com.devopshub.ui.view.ExecutionDetailView`

---

## Module 4: Build, Log & Configuration Management

### Functional Responsibilities
- Capture stdout and stderr output concurrently from running child processes.
- Format log entries with microsecond timestamps and log severity levels.
- Cap memory usage with circular bounded buffers (`BoundedLogBuffer`).
- Flush batches to durable MySQL storage.

### Implementation Classes
- **Log Collector:** `com.devopshub.execution.LogCollector`
- **Buffer:** `com.devopshub.execution.BoundedLogBuffer`
- **Domain Model:** `com.devopshub.model.ExecutionLog`
- **Data Access:** `com.devopshub.repository.LogRepository`
- **UI Screen:** `com.devopshub.ui.view.LogViewerView`

---

## Module 5: Rule-Based Intelligence & Dashboard Analytics

### Functional Responsibilities
- Inspect failed stages and error log streams automatically.
- Match patterns against transparent Java rule implementations.
- Generate structured diagnoses and actionable next steps.
- Compute real-time KPI metrics (Success Rate, Avg Duration, Incident Categories) directly from MySQL.

### Implementation Classes
- **Rule Engine:** `com.devopshub.analysis.FailureRuleEngine`
- **Rule Contract:** `com.devopshub.analysis.FailureRule`
- **Domain Models:** `com.devopshub.model.FailureDiagnosis`, `com.devopshub.model.DashboardMetrics`
- **Data Access:** `com.devopshub.repository.DiagnosisRepository`
- **Business Logic:** `com.devopshub.service.DashboardService`
- **UI Screens:** `com.devopshub.ui.view.DashboardView`, `com.devopshub.ui.view.AnalyticsView`
