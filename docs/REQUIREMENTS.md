# DevOpsHub — Comprehensive Requirements Specification

**Document Version:** 1.0.0  
**Project:** DevOpsHub — Intelligent CI/CD Management Platform  
**Academic Context:** CSE1007 — Java Programming  
**Author / Architect:** DevOpsHub Principal Engineering Team  

---

## 1. Executive Summary & Vision

DevOpsHub is a native JavaFX desktop application designed to execute, monitor, optimize, and diagnose CI/CD delivery pipelines. While traditional CI/CD engines merely report failure codes and raw logs leaving teams to perform manual scavenger hunts, DevOpsHub ingests structured execution evidence and applies transparent, explainable Java rule-based intelligence to pinpoint likely failure causes and recommend actionable recovery steps.

### Product Workflow
$$\text{Create Project} \longrightarrow \text{Configure Pipeline} \longrightarrow \text{Validate Pipeline} \longrightarrow \text{Execute Pipeline} \longrightarrow \text{Monitor Stages} \longrightarrow \text{Collect Logs} \longrightarrow \text{Analyze Failure} \longrightarrow \text{Recommend Action} \longrightarrow \text{Review History} \longrightarrow \text{Recover / Optimize}$$

---

## 2. Requirement Traceability Matrix

| Requirement ID | Module / Area | Source Reference | Expected Behavior | Java Package / Component | JavaFX Screen | Database Entity | Verification Test | Status |
|---|---|---|---|---|---|---|---|---|
| **REQ-PROJ-01** | Project Mgmt | Slide 5, 6 | Project creation with name, description, git repository URL, default branch, language/type | `com.devopshub.service.ProjectService` | `ProjectsView`, `ProjectFormModal` | `projects` | `ProjectServiceTest#testCreateProject` | Verified |
| **REQ-PROJ-02** | Project Mgmt | Slide 6 | List all projects with status, associated repository, pipeline count, and timestamps | `com.devopshub.repository.ProjectRepository` | `ProjectsView` | `projects` | `ProjectRepositoryTest#testFindAll` | Verified |
| **REQ-PROJ-03** | Project Mgmt | Slide 6 | Edit existing project details and update timestamp | `com.devopshub.service.ProjectService` | `ProjectFormModal` | `projects` | `ProjectServiceTest#testUpdateProject` | Verified |
| **REQ-PROJ-04** | Project Mgmt | Slide 6 | Delete project with confirmation and safety checks | `com.devopshub.service.ProjectService` | `ProjectsView`, `ConfirmationDialog` | `projects` | `ProjectServiceTest#testDeleteProject` | Verified |
| **REQ-PROJ-05** | Project Mgmt | Slide 6 | Filter and search projects by name, language, or status | `com.devopshub.service.ProjectService` | `ProjectsView` | `projects` | `ProjectServiceTest#testSearchProjects` | Verified |
| **REQ-PIPE-01** | Pipeline Mgmt | Slide 5, 6 | Create named pipeline bound to a project | `com.devopshub.pipeline.PipelineService` | `PipelinesView`, `PipelineBuilderView` | `pipelines` | `PipelineServiceTest#testCreatePipeline` | Verified |
| **REQ-PIPE-02** | Pipeline Mgmt | Slide 3, 6 | Add/remove stages: Checkout, Build, Test, Security, Package, Deploy | `com.devopshub.pipeline.PipelineDefinition` | `PipelineBuilderView` | `pipeline_stages` | `PipelineValidatorTest#testStageAddition` | Verified |
| **REQ-PIPE-03** | Pipeline Mgmt | Slide 6, 7 | Configure stage parameters, commands, timeouts, execution mode (Sequential / Parallel) | `com.devopshub.pipeline.StageConfig` | `StageConfigDialog` | `pipeline_stages` | `PipelineValidatorTest#testStageConfiguration` | Verified |
| **REQ-PIPE-04** | Pipeline Mgmt | Slide 7 | Define and validate stage dependencies (DAG validation: acyclic, valid prerequisites) | `com.devopshub.pipeline.PipelineValidator` | `PipelineBuilderView` | `stage_dependencies` | `PipelineValidatorTest#testAcyclicDependency` | Verified |
| **REQ-PIPE-05** | Pipeline Mgmt | Slide 6 | Enable or disable individual stages within a pipeline | `com.devopshub.pipeline.Stage` | `PipelineBuilderView` | `pipeline_stages` | `PipelineServiceTest#testEnableDisableStage` | Verified |
| **REQ-EXEC-01** | Execution Engine | Slide 5, 7 | Start execution with unique Execution ID, track states: QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED, SKIPPED | `com.devopshub.execution.ExecutionEngine` | `ExecutionDetailView`, `PipelinesView` | `executions`, `stage_executions` | `ExecutionEngineTest#testExecutionLifecycle` | Verified |
| **REQ-EXEC-02** | Execution Engine | Slide 7 | Sequential stage execution obeying topological order | `com.devopshub.execution.StageRunner` | `ExecutionDetailView` | `stage_executions` | `ExecutionEngineTest#testSequentialExecution` | Verified |
| **REQ-EXEC-03** | Execution Engine | Slide 7 | Fork/Join parallel execution of independent stages (e.g. Test and Security concurrently) | `com.devopshub.execution.ParallelStageCoordinator` | `ExecutionDetailView` | `stage_executions` | `ExecutionEngineTest#testParallelExecution` | Verified |
| **REQ-EXEC-04** | Execution Engine | Slide 7 | Bounded thread pool management via `ExecutorService` with clean termination | `com.devopshub.execution.ExecutionThreadPool` | Application Lifecycle | None | `ExecutionEngineTest#testPoolLimits` | Verified |
| **REQ-EXEC-05** | Execution Engine | Slide 7 | Cancel running execution gracefully terminating worker processes | `com.devopshub.execution.ExecutionEngine` | `ExecutionDetailView` | `executions` | `ExecutionEngineTest#testCancelExecution` | Verified |
| **REQ-EXEC-06** | Execution Engine | Slide 4, 8 | Failure propagation: dependent stages skipped if prerequisite fails | `com.devopshub.execution.ExecutionEngine` | `ExecutionDetailView` | `stage_executions` | `ExecutionEngineTest#testFailurePropagation` | Verified |
| **REQ-LOG-01** | Log Management | Slide 8 | Real-time and persisted stdout/stderr log capture with timestamps and log levels | `com.devopshub.execution.LogCollector` | `LogViewerView` | `execution_logs` | `LogCollectorTest#testCapture` | Verified |
| **REQ-LOG-02** | Log Management | Slide 8 | Bounded in-memory buffering preventing OutOfMemory on voluminous output | `com.devopshub.execution.BoundedLogBuffer` | `LogViewerView` | None | `LogCollectorTest#testBoundedBuffer` | Verified |
| **REQ-LOG-03** | Log Management | Slide 8 | Search, filter by stage, and highlight error tokens in log viewer | `com.devopshub.ui.view.LogViewerView` | `LogViewerView` | `execution_logs` | UI Verification | Verified |
| **REQ-INTEL-01**| Intelligence | Slide 4, 8 | Rule-based failure classification engine matching log patterns and exit codes | `com.devopshub.analysis.FailureRuleEngine` | `ExecutionDetailView`, `AnalyticsView` | `failure_diagnoses` | `FailureRuleEngineTest#testClassification` | Verified |
| **REQ-INTEL-02**| Intelligence | Slide 8 | Actionable recovery recommendations mapped to identified failure categories | `com.devopshub.analysis.RecommendationEngine` | `ExecutionDetailView` | `failure_diagnoses` | `FailureRuleEngineTest#testRecommendation` | Verified |
| **REQ-INTEL-03**| Intelligence | Slide 8 | Diagnosis includes Category, Likely Cause, Evidence Excerpt, Failed Stage, Next Action, Rule ID | `com.devopshub.model.FailureDiagnosis` | `ExecutionDetailView` | `failure_diagnoses` | `FailureRuleEngineTest#testDiagnosisModel` | Verified |
| **REQ-INTEL-04**| Intelligence | Slide 8, 12 | Test assertion failure rule (e.g., expected HTTP 401 received 500 at LoginService) | `com.devopshub.analysis.rules.AssertionFailureRule` | `ExecutionDetailView` | `failure_diagnoses` | `FailureRuleEngineTest#testHttp401vs500` | Verified |
| **REQ-DASH-01** | Dashboard | Slide 9 | Metrics: Total Projects, Total Pipelines, Success Rate %, Avg Build Time (seconds) | `com.devopshub.service.DashboardService` | `DashboardView` | Aggregated | `DashboardServiceTest#testMetricsCalculation` | Verified |
| **REQ-DASH-02** | Dashboard | Slide 9 | Execution count cards: Successful builds count, Failed builds count | `com.devopshub.service.DashboardService` | `DashboardView` | Aggregated | `DashboardServiceTest#testBuildCounts` | Verified |
| **REQ-DASH-03** | Dashboard | Slide 9 | Recent execution feed and recent projects table with live status badges | `com.devopshub.service.DashboardService` | `DashboardView` | `executions`, `projects` | `DashboardServiceTest#testRecentFeed` | Verified |
| **REQ-DB-01**   | Database Layer | Slide 10 | MySQL JDBC connectivity using `PreparedStatement` and connection pooling | `com.devopshub.config.DatabaseManager` | None | All tables | `DatabaseManagerTest#testConnectivity` | Verified |
| **REQ-DB-02**   | Database Layer | Architecture | Automatic schema migration and seed initialization | `com.devopshub.repository.SchemaMigrationService`| None | All tables | `SchemaMigrationTest#testMigration` | Verified |
| **REQ-UI-01**   | JavaFX UI | All Slides | Responsive native JavaFX desktop interface with modern design tokens | `com.devopshub.ui.*` | All screens | None | UI Verification | Verified |
| **REQ-UI-02**   | JavaFX UI | Prompt §5 | Strict SVG-only icon policy across the entire interface (SVGPath geometry) | `com.devopshub.ui.component.SvgIcon` | All screens | None | Icon Audit | Verified |
| **REQ-UI-03**   | JavaFX UI | Prompt §4, 6 | Thread safety: zero UI freezes, worker threads dispatch via `Platform.runLater` | `com.devopshub.ui.*` | All screens | None | Concurrency Verification | Verified |

---

## 3. Scope Boundaries

### In-Scope (MVP)
1. Full JavaFX desktop frontend matching the presentation layout and design tokens.
2. Complete local project and CI/CD pipeline management.
3. Real Java execution engine with local process execution (`ProcessBuilder`) and controlled mock adapters for external deployment.
4. Multithreaded execution: sequential stages and parallel stage forks.
5. Persistent MySQL database storage with connection pooling and automated schema migration.
6. Timestamped logging with in-memory bounded buffering and database durability.
7. Explainable rule-based failure diagnosis and actionable recommendations.
8. Persisted execution history and dashboard metrics calculated from stored records.
9. 100% SVG icons rendered via JavaFX vector path geometry.

### Explicitly Excluded (Out of Scope for MVP)
1. Browser/web frontend (React, Vite, Angular, Vue are strictly prohibited).
2. Remote cloud cluster deployment (Kubernetes, AWS ECS orchestration).
3. External paid black-box AI API calls (OpenAI, Claude API dependencies).
4. Spring Boot web server daemon (pure native JavaFX desktop architecture).
