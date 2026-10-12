# DevOpsHub Acceptance Criteria & Verification Matrix

## 1. Traceability & Acceptance Sign-off

| ID | Feature / Requirement | Acceptance Criteria | Verification Method | Status |
|---|---|---|---|---|
| **AC-01** | **JavaFX Desktop UI** | Frontend is 100% native JavaFX; zero React or web framework dependencies in the build. | Inspected `pom.xml` and runtime scene graph. | **PASSED** |
| **AC-02** | **SVG Icon Policy** | 100% of icons rendered via vector `SVGPath` geometry and `.svg` files; zero emojis or PNGs. | Audited `SvgIcon.java`, `icons/svg/`, and rendered views. | **PASSED** |
| **AC-03** | **Project Management** | User can list, create, edit, and delete projects with repository URL validation. Data persists in MySQL. | `ProjectServiceTest`, `ProjectRepositoryTest`, UI testing. | **PASSED** |
| **AC-04** | **Pipeline Validation** | Pipeline configurations must have valid stages and reject dependency cycles. | `PipelineValidatorTest` (DFS cycle check). | **PASSED** |
| **AC-05** | **Execution Engine** | Executes real subprocesses (`ProcessBuilder`), tracks timeouts, exit codes, and durations. | `ExecutionEngineTest` running echo/exit commands. | **PASSED** |
| **AC-06** | **Parallel Execution** | Concurrent stages execute simultaneously via `CompletableFuture` / Fork-Join pool. | `ExecutionEngineTest` parallel multi-stage tests. | **PASSED** |
| **AC-07** | **Failure Intelligence** | Diagnoses failures with explainable rule-based engine and provides actionable recovery steps. | `FailureRuleEngineTest` (HTTP 401 vs 500 test). | **PASSED** |
| **AC-08** | **MySQL Persistence** | All projects, pipelines, executions, logs, and diagnoses persist across app restarts using JDBC. | Verified against live MySQL Docker instance on port 3306. | **PASSED** |
| **AC-09** | **Dashboard Metrics** | Live metrics (Total Projects, Pipelines, Executions, Success Rate) calculated dynamically from DB. | `DashboardService` live calculations verified. | **PASSED** |
| **AC-10** | **Responsive Design** | Resizes gracefully from `1024x700` up to 4K resolutions without clipped controls or broken layouts. | `devopshub.css` and layout containers verified. | **PASSED** |
