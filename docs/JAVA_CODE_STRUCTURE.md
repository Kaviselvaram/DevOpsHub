# DevOpsHub Java Code Structure & Package Organization

## 1. Package Overview
DevOpsHub follows a layered, domain-centric Java architecture located in `src/main/java/com/devopshub/`:

```
com.devopshub/
├── app/                  # Application startup & lifecycle
│   ├── Launcher.java     # Entry point without JavaFX bootstrap restrictions
│   └── MainApp.java      # JavaFX Application instance, DI setup, Stage management
│
├── config/               # Configuration management & environment parsing
│   ├── AppConfig.java    # Type-safe property access (.env + defaults)
│   └── DatabaseConfig.java # HikariCP DataSource factory & migration executor
│
├── model/                # Immutable domain entities & enums
│   ├── Project.java      # CI/CD Project representation
│   ├── Pipeline.java     # Pipeline definition & metadata
│   ├── Stage.java        # Single stage configuration & commands
│   ├── Execution.java    # Top-level pipeline execution record
│   ├── StageExecution.java # Granular stage execution record
│   ├── ExecutionLog.java # Timestamped stdout/stderr log entry
│   ├── FailureDiagnosis.java # Root cause analysis & recommendation
│   └── enums/            # Domain enums
│       ├── ExecutionStatus.java
│       ├── StageType.java
│       ├── ExecutionMode.java
│       ├── LogLevel.java
│       └── FailureCategory.java
│
├── repository/           # Persistence abstraction (JDBC + SQL)
│   ├── ProjectRepository.java
│   ├── PipelineRepository.java
│   ├── ExecutionRepository.java
│   ├── LogRepository.java
│   └── DiagnosisRepository.java
│
├── service/              # Core business services
│   ├── ProjectService.java
│   ├── PipelineService.java
│   ├── DashboardService.java
│   └── ServiceFactory.java
│
├── pipeline/             # Pipeline validation & topological sorting
│   └── PipelineValidator.java # Cycle detection & dependency graph validation
│
├── execution/            # Real subprocess execution engine & orchestration
│   ├── ExecutionEngine.java       # Coordinates pipeline stages & lifecycle
│   ├── StageRunner.java           # Runs single stage process safely
│   ├── ParallelStageCoordinator.java # Coordinates parallel stages via CompletableFuture
│   ├── ProcessExecutor.java       # ProcessBuilder wrapper with timeouts & sandboxing
│   ├── ExecutionThreadPool.java   # Thread pool management & graceful shutdown
│   ├── LogCollector.java          # Streams and collects log events
│   └── BoundedLogBuffer.java      # In-memory memory-bounded log ring buffer
│
├── analysis/             # Rule-based failure intelligence engine
│   ├── FailureRuleEngine.java     # Orchestrates rule matching & diagnosis creation
│   ├── FailureRule.java           # Interface for modular rule implementations
│   └── rules/                     # Concrete failure rules
│       ├── AssertionFailureRule.java
│       ├── CompilationErrorRule.java
│       ├── MissingDependencyRule.java
│       ├── DatabaseConnectionRule.java
│       ├── TimeoutRule.java
│       └── PermissionDeniedRule.java
│
├── exception/            # Custom domain exception hierarchy
│   ├── DatabaseException.java
│   ├── ValidationException.java
│   ├── ExecutionException.java
│   └── ResourceNotFoundException.java
│
├── util/                 # Reusable helpers
│   └── SvgIconUtil.java  # Vector SVG path registry and helpers
│
└── view/                 # JavaFX desktop UI views & components
    ├── MainLayoutView.java        # Master app shell & navigation sidebar
    ├── DashboardView.java         # High-level operational metrics & recent activity
    ├── ProjectsView.java          # Project list, creation modal, and actions
    ├── PipelinesView.java         # Pipeline list & trigger actions
    ├── PipelineBuilderView.java   # Stage builder, order, & dependency editor
    ├── ExecutionHistoryView.java  # Historical execution table & status filters
    ├── ExecutionDetailView.java   # Stage progress, real-time log, & diagnosis card
    ├── LogViewerView.java         # Standalone log browser
    ├── AnalyticsView.java         # Failure category breakdowns & performance stats
    ├── SettingsView.java          # Database connection, thread pool, & version info
    └── component/                 # Reusable UI widgets
        ├── SvgIcon.java
        ├── StatusBadge.java
        ├── MetricCard.java
        ├── ToastNotification.java
        └── ConfirmationDialog.java
```

---

## 2. Key Design Patterns Applied
1. **Repository Pattern:** Separates database access (`PreparedStatement`, connection handling) from business logic.
2. **Service Layer Pattern:** Encapsulates business rules, transactions, and validation.
3. **Factory Pattern:** `ServiceFactory` manages dependency injection and singleton lifecycle of repositories and services.
4. **Strategy / Rule Pattern:** `FailureRuleEngine` and `FailureRule` allow pluggable failure analysis rules without modifying core engine logic.
5. **Producer-Consumer Pattern:** `ProcessExecutor` and `LogCollector` stream subprocess standard out/err without blocking process execution.
6. **Composite / Component Pattern:** JavaFX view components (`MetricCard`, `StatusBadge`, `SvgIcon`) compose cleanly into parent views.
