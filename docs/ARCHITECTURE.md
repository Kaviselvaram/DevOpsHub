# DevOpsHub — System Architecture Specification

**Product:** DevOpsHub — Intelligent CI/CD Management Platform  
**Architecture:** Layered Native JavaFX Desktop with Direct JDBC / MySQL Persistence  

---

## 1. Architectural Philosophy

DevOpsHub is architected as a cohesive, single-binary Java desktop application designed to provide instantaneous feedback, resilient local process orchestration, and deterministic root-cause analysis.

By eschewing complex distributed web frameworks (such as Spring Boot, Node.js, or React), DevOpsHub runs natively on the Java Virtual Machine. This provides direct, unhindered access to OS process management (`ProcessBuilder`), high-throughput multithreading (`ExecutorService`), and zero-latency local JDBC database connectivity.

---

## 2. System Architecture Diagram

```mermaid
graph TD
    subgraph JavaFX Desktop UI Layer
        UI_Shell[MainLayoutView - App Shell & Nav]
        DashView[DashboardView - Live Metrics & Shortcuts]
        ProjView[ProjectsView - Repository CRUD]
        PipeView[PipelinesView & PipelineBuilderView]
        ExecView[ExecutionDetailView - Pipeline Visualizer]
        LogView[LogViewerView - Terminal Console]
        AnalyticsView[AnalyticsView - Failure Trends]
        SettingsView[SettingsView - Diagnostics]
    end

    subgraph Presentation & Controller Layer
        SvgComp[SvgIcon - Strict 100% Vector System]
        BadgeComp[StatusBadge & MetricCard]
        ToastComp[ToastNotification & Dialogs]
        PlatformDispatcher[Platform.runLater UI Dispatcher]
    end

    subgraph Application Services Layer
        ProjectService[ProjectService]
        PipelineService[PipelineService]
        DashboardService[DashboardService]
        Validator[PipelineValidator - DAG Cycle Checker]
    end

    subgraph Execution & Intelligence Engine
        ExecEngine[ExecutionEngine - Master Coordinator]
        ThreadPool[ExecutionThreadPool - Bounded Pools]
        ParallelCoord[ParallelStageCoordinator - Fork/Join]
        StageRunner[StageRunner - Callable Task]
        ProcessExec[ProcessExecutor - ProcessBuilder]
        LogColl[LogCollector - Bounded In-Memory Buffer]
        RuleEngine[FailureRuleEngine - Transparent Matcher]
    end

    subgraph Data Access Layer JDBC
        DBManager[DatabaseManager - HikariCP Pool]
        ProjRepo[ProjectRepository]
        PipeRepo[PipelineRepository]
        ExecRepo[ExecutionRepository]
        LogRepo[LogRepository]
        DiagRepo[DiagnosisRepository]
    end

    subgraph Storage
        MySQL[(MySQL 8.0 Database)]
    end

    UI_Shell --> Presentation & Controller Layer
    Presentation & Controller Layer --> Application Services Layer
    Application Services Layer --> Execution & Intelligence Engine
    Application Services Layer --> Data Access Layer JDBC
    Execution & Intelligence Engine --> Data Access Layer JDBC
    Execution & Intelligence Engine -.->|Async UI Updates| PlatformDispatcher
    PlatformDispatcher --> UI_Shell
    Data Access Layer JDBC --> DBManager
    DBManager --> MySQL
```

---

## 3. Structural Layers

### 3.1 Presentation Layer (`com.devopshub.ui`)
- Implements the complete user interface using native JavaFX controls (`BorderPane`, `TableView`, `ScrollPane`, `StackPane`, `GridPane`).
- **Design Tokens:** Modern dark sidebar navigation (`#0F172A`) coupled with light content canvas (`#F8FAFC`) and crisp Sky Blue primary accents (`#0284C7`).
- **Vector Icons:** Strictly renders vector paths using JavaFX `SVGPath` geometry. Zero icon fonts, raster images, or emojis.

### 3.2 Application Services Layer (`com.devopshub.service`, `com.devopshub.pipeline`)
- Encapsulates domain logic, validation constraints, and business rules.
- `PipelineValidator` applies depth-first search (DFS) cycle detection algorithms to ensure stage dependencies form a valid Directed Acyclic Graph (DAG).

### 3.3 Execution & Concurrency Engine (`com.devopshub.execution`)
- Manages local process execution via `ProcessBuilder`.
- Bounded thread pools prevent resource exhaustion.
- Stages are evaluated sequentially or in concurrent parallel groups (`Fork/Join`) using `CompletableFuture.allOf()`.
- Collects live stdout/stderr streams into a memory-bounded circular buffer (`BoundedLogBuffer`).

### 3.4 Failure Intelligence Layer (`com.devopshub.analysis`)
- Triggers automatically when a stage reports an abnormal exit code.
- Evaluates log evidence against deterministic rule sets (`AssertionFailureRule`, `CompilationErrorRule`, `MissingDependencyRule`, etc.).
- Produces structured diagnoses with likely cause, log excerpts, and recommended next actions.

### 3.5 Persistence Layer (`com.devopshub.config`, `com.devopshub.repository`)
- Centralized `DatabaseManager` manages a `HikariCP` connection pool connected to MySQL 8.0 on port 3306.
- Safe parameterized queries (`PreparedStatement`) prevent SQL injection attacks.
- Automatic schema creation and migrations on application startup.
