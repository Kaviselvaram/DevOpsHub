# DevOpsHub — Strict Full-System Integrity Baseline Report

**Execution Timestamp:** 2026-10-27T00:05:00+05:30  
**Environment:** macOS (Darwin Apple Silicon `aarch64`), Java 21 LTS (OpenJDK 21.0.6), Maven 3.6.3 / 3.9+  
**Operating Persona:** Senior Java Architect, Database Reliability Engineer, CI/CD Platform Engineer, Independent Code Auditor  

---

## 1. Repository & Working Tree State

### 1.1 Git Inventory
- **Repository Root:** `/Users/kaviselvaramkathirvel/javacicd`
- **Active Branch:** `main`
- **Head Commit:** `da93012` (*"feat: complete master UI/UX revision, ProjectDetailView, PipelineDetailView, visual DAG flow, stage log filtering, and audit reports"*)
- **Prior Commit:** `2483f41` (*"feat: complete DevOpsHub JavaFX MVP, MySQL JDBC persistence, execution engine, tests and documentation"*)
- **Working Tree State:** Dirty. Extensive uncommitted changes and untracked files representing active UI/UX improvements, integration test fixtures, and security/isolation audits.

### 1.2 Preservation Inventory (Strict Rule Adherence)
In strict accordance with Preservation Rules 1–12, zero uncommitted files, branches, databases, workspaces, or logs have been discarded or reset:
- **Modified Tracked Files (49):**
  - Configurations & Dependencies: `.env.example`, `.gitignore`, `pom.xml`, `README.md`
  - Core Architecture & Persistence: `DatabaseManager.java`, `AppConfig.java`, `PipelineRepository.java`, `ExecutionRepository.java`
  - Execution Engine & Runners: `ExecutionEngine.java`, `StageRunner.java`, `ProcessExecutor.java`, `ParallelStageCoordinator.java`, `LogCollector.java`, `BoundedLogBuffer.java`
  - Domain Models & Validation: `Stage.java`, `StageExecution.java`, `Execution.java`, `FailureCategory.java`, `PipelineValidator.java`
  - Services: `PipelineService.java`, `DashboardService.java`, `FailureRuleEngine.java`
  - UI Views & Controllers: `MainLayoutView.java`, `HomeView.java`, `DashboardView.java`, `ProjectsView.java`, `PipelinesView.java`, `PipelineBuilderView.java`, `PipelineDetailView.java`, `ProjectDetailView.java`, `ExecutionDetailView.java`, `ExecutionHistoryView.java`, `LogViewerView.java`, `AnalyticsView.java`, `SettingsView.java`
  - Stylesheet: `src/main/resources/css/devopshub.css` (Dark theme design system tokens)
  - Migrations: `database/migrations/V1__initial_schema.sql`, `src/main/resources/db/migrations/V1__initial_schema.sql`
- **Untracked Directories & Files:**
  - `data/devopshub.mv.db` (Durable H2 disk fallback database file)
  - `workspaces/projects/` (Local build workspaces for imported repositories)
  - `database/migrations/V3...`, `V4...` and `src/main/resources/db/migrations/V3...`, `V4...`
  - `src/main/java/com/devopshub/deployment/` (Genuine deployment engine and providers)
  - `src/main/java/com/devopshub/security/` (Security scanner and vulnerability models)
  - `src/main/java/com/devopshub/scheduler/` (Persistent automated pipeline scheduler)
  - `src/main/java/com/devopshub/webhook/` (HMAC-SHA256 authenticated webhook server)
  - `src/main/java/com/devopshub/execution/backend/` (Hardened Docker sandboxing & Host execution backends)
  - `src/main/java/com/devopshub/git/` (Real Git operations & traversal protection)

---

## 2. Technology Stack & Dependency Baseline

| Component | Specification / Version | Source / Reference |
| :--- | :--- | :--- |
| **Java Platform** | Java 21 LTS (OpenJDK 64-Bit Server VM, runtime 21.0.6) | `java.version` / `pom.xml` `<release>21</release>` |
| **UI Framework** | OpenJFX / JavaFX 21.0.6 (`javafx-controls`, `javafx-fxml`, `javafx-graphics`) | `pom.xml` lines 27–42 |
| **Persistence Pool** | HikariCP 5.1.0 (`com.zaxxer:HikariCP`) | `pom.xml` lines 67–71 |
| **Primary Driver** | MySQL Connector/J 8.3.0 (`com.mysql:mysql-connector-j`) | `pom.xml` lines 73–77 |
| **Fallback Driver** | H2 Database Engine 2.2.224 (`com.h2database:h2`) | `pom.xml` lines 79–83 |
| **Logging Facade** | SLF4J 2.0.12 with Logback Classic 1.5.3 | `pom.xml` lines 85–94 |
| **Testing Harness** | JUnit Jupiter 5.10.2 (`org.junit.jupiter:junit-jupiter`) | `pom.xml` lines 96–101 |
| **Build Tool** | Apache Maven (Compiler 3.12.1, Surefire 3.2.5, JavaFX plugin 0.0.8) | `pom.xml` build section |

---

## 3. Application Entry Point & Runtime Architecture

### 3.1 Entry Point Chain
1. **Desktop Main:** `com.devopshub.app.MainApp.java` (JavaFX Application lifecycle: `init()`, `start(Stage)`, `stop()`).
2. **Fat JAR Launcher:** `com.devopshub.app.Launcher.java` (Bootstrap wrapper preventing module-path errors when launching outside Maven).
3. **Configuration Loader:** `com.devopshub.config.AppConfig.java` (Precedence: defaults $\rightarrow$ `.env` $\rightarrow$ System Environment Variables $\rightarrow$ JVM System Properties `-D`).
4. **Database Manager:** `com.devopshub.config.DatabaseManager.java` (HikariCP pool initialization, primary MySQL probe with automated fallback to H2).
5. **Thread Pools:** `com.devopshub.execution.ExecutionThreadPool.java` (Max 4 concurrent pipelines, max 16 concurrent stages).
6. **Background Services:**
   - `WebhookServerService` listening on HTTP port `8089` (`/api/webhook/trigger`) with HMAC-SHA256 signature verification.
   - `PipelineSchedulerService` running scheduled triggers via `ScheduledExecutorService` backed by `pipeline_schedules` table.

---

## 4. Discovered Database Instances & Reconciliation Matrix

Investigation into the discrepancy between observed `H2 In-Memory Active` / `H2 Fallback (Ephemeral)` UI labels and the existing MySQL database reveals **two distinct database instances**:

### 4.1 Database Reconciliation Matrix

| Attribute | Instance A: Primary Authoritative (MySQL) | Instance B: Local Fallback (H2 Embedded Disk) |
| :--- | :--- | :--- |
| **Engine & Version** | MySQL Community Server 8.0.46 | H2 Database 2.2.224 (`MODE=MySQL`) |
| **Storage Topology** | Docker Container `devopshub-mysql` (`15757d37a748`) | Local filesystem file: `./data/devopshub.mv.db` |
| **Volume Mount** | Docker volume `e375552b...` mounted to `/var/lib/mysql` | Single-file embedded PageStore/MVStore (`90,112 bytes`) |
| **Network Endpoint** | `127.0.0.1:3306` (`0.0.0.0:3306` bound on host) | Embedded JVM in-process (JDBC file connection) |
| **Authoritative Role** | **PRIMARY AUTHORITATIVE SOURCE OF TRUTH** | **EMERGENCY OFFLINE FALLBACK ONLY** |
| **Persistence Mode** | Persistent (Disk-backed InnoDB engine) | Persistent (Disk-backed MVStore engine) |
| **Schema Catalog** | `devopshub` | `devopshub` (public schema) |
| **Applied Migrations** | V1, V2, V3, V4 | V1, V2, V3, V4 |
| **Active Connections** | HikariCP pool `DevOpsHub-MySQL-Pool` (10 connections max) | Dormant when MySQL is reachable |
| **Root Cause of UI Discrepancy** | UI labels hardcoded `"H2 In-Memory Active"` whenever fallback tripped | File `./data/devopshub.mv.db` was created Oct 26 23:51 when Docker was stopped |

### 4.2 Exact Record Count Verification (Preserved Unaltered)

Read-only inspection via `docker exec -i devopshub-mysql mysql -e "..."` and H2 Shell tool reveals exact live row counts:

| Database Table | MySQL 8.0 Live Count | H2 Disk Fallback Count | Data Status |
| :--- | :---: | :---: | :--- |
| `projects` | **41** | **1** | Preserved. MySQL contains all active imported projects. |
| `pipelines` | **50** | **1** | Preserved. Includes user-created and imported CI pipelines. |
| `pipeline_stages` | **106** | **5** | Preserved. Full multi-stage DAGs mapped to pipelines. |
| `stage_dependencies` | **47** | **0** | Preserved. Prerequisite DAG execution edges. |
| `executions` | **23** | **1** | Preserved. Complete lifecycle execution records. |
| `stage_executions` | **50** | **5** | Preserved. Individual stage run status, exit codes, durations. |
| `execution_logs` | **372** | **2** | Preserved. Live stdout/stderr log lines. |
| `execution_events` | **572** | **14** | Preserved. Timeline and stage transition events. |
| `failure_diagnoses` | **10** | **1** | Preserved. Automated rule-engine root cause analyses. |
| `deployment_records` | **2** | **0** | Preserved. Staging and production artifact records. |
| `pipeline_schedules` | **0** | **0** | Clean. Table initialized via V4 migration. |
| `security_findings` | **0** | **0** | Clean. Ready for vulnerability ingestion. |
| `schema_version` | **4** | **4** | V1, V2, V3, V4 registered in both instances. |

---

## 5. Pipeline Architecture & Stage Contract Baseline

### 5.1 Pipeline Stage Types
The application defines 6 canonical stage types in `StageType.java`:
1. `CHECKOUT`: Clones/fetches source repository using hardened `GitService`.
2. `BUILD`: Compiles code (Maven, npm, Gradle, Python). Hardened container isolation for untrusted sources.
3. `TEST`: Executes test suite (`mvn test`, `npm test`, `pytest`).
4. `SECURITY`: Executes static code and secret scanning via `CompositeSecurityScanner`.
5. `PACKAGE`: Assembles distribution archive (`.jar`, `.war`, `.zip`, `.tar.gz`, `npm pack`).
6. `DEPLOY`: Dispatches built artifact to local staging or remote SSH/SCP target via `DeploymentService`.

### 5.2 Execution Isolation Model
- **`ExecutionTrustLevel.UNTRUSTED_EXTERNAL`:**
  - Applied to remote Git repositories (e.g., GitHub, GitLab).
  - Executed via `DockerExecutionBackend` (`node:20-alpine` or `alpine:latest`).
  - Security flags enforced: `--security-opt no-new-privileges`, `--cap-drop ALL`, `--memory 2048m`, `--cpus 4.0`, `--pids-limit 2048`, mounted `/workspace` only.
  - Fail-closed posture: If Docker daemon is stopped, untrusted workloads are immediately blocked (`RULE_SECURITY_POLICY_VIOLATION`), never falling back to host execution.
- **`ExecutionTrustLevel.TRUSTED_LOCAL`:**
  - Applied to local repositories where host build tools are available.
  - Executed via `HostExecutionBackend` with environment variable sanitization.
