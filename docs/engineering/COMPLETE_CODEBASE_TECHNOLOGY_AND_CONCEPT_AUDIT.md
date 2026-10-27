# DevOpsHub — Complete Codebase Reverse Engineering, Technology Stack & Implementation Mapping Audit

**Date:** 2026-10-26  
**Auditor:** Principal Enterprise Java Architect, Systems Engineer & Security Analyst  
**Repository:** `/Users/kaviselvaramkathirvel/javacicd`  
**Git Baseline:** Branch `main`, Commit `da93012`  
**Execution Platform:** macOS (Darwin arm64 26.6.2), Oracle JDK 25.0.1 LTS (Language level 21), Maven 3.6.3, JavaFX 21.0.6  
**Active Database Engine:** MySQL 8.0.46 (Docker container `devopshub-mysql` via port 3306) with durable disk-backed embedded H2 fallback (`./data/devopshub.mv.db`)

---

## 1. Executive Summary

DevOpsHub is a standalone Java desktop application built on **JavaFX 21** that provides an intelligent Continuous Integration and Continuous Delivery (CI/CD) control plane. It integrates real local process and container orchestration, Git repository management, topological DAG pipeline execution, static security scanning, multi-target artifact deployment, cryptographic webhook ingestion, automated cron-like scheduling, and rule-based failure diagnosis.

A forensic source-code and runtime investigation of the repository reveals the following foundational truths:

1. **Technology Stack:** Built purely in **Java 21/25** without heavy full-stack web frameworks (no Spring Boot, no Jakarta EE container). JavaFX 21 is used for the desktop GUI, rendered entirely through programmatic scene graph construction and custom CSS (no FXML).
2. **Database & JDBC:** Persistence is implemented using **raw JDBC** over a **HikariCP (5.1.0)** connection pool. There is no JPA, Hibernate, or MyBatis. All SQL queries are hand-written, parameterized `PreparedStatement` operations encapsulated within explicit Repository classes. Database migrations are applied automatically at startup via an internal custom SQL migration runner.
3. **Physical Storage Location:** The application is currently connected to a live **MySQL 8.0.46** container named `devopshub-mysql`. Data is physically stored in Docker Desktop’s virtual machine volume (`e375552b694725b3b3559e46ef77d4dddee41555c731ad8ad584a72c4efccc0a` mounted to `/var/lib/mysql`), **not** in the repository root. A secondary fallback exists using **embedded disk-backed H2 (2.2.224)** at `./data/devopshub.mv.db`.
4. **Execution & Isolation:** Process execution is real. Host processes are spawned using `java.lang.ProcessBuilder` wrapped in background streaming threads with strict environment sanitization and process-tree termination (`ProcessHandle.descendants()`). Untrusted external workloads are routed to a hardened **Docker execution backend** with capability drops (`--cap-drop ALL`), `no-new-privileges`, resource quotas (`--memory`, `--cpus`, `--pids-limit`), and isolated workspace mounts (`/workspace:rw`).
5. **Security & Cryptography:** Webhooks run on an embedded HTTP server (`com.sun.net.httpserver.HttpServer`) enforcing **HMAC-SHA256** signature verification (`X-Hub-Signature-256`), constant-time byte comparisons (`MessageDigest.isEqual`), replay attack protection (5-minute timestamp window), and delivery ID deduplication. Log output is dynamically scrubbed of API keys, passwords, bearer tokens, and private keys via `SecretMasker`.
6. **Failure Intelligence:** A rule-based diagnosis engine (`FailureRuleEngine`) evaluates execution logs and stage exit codes against 12 prioritized heuristic rules to classify failures (e.g., compilation errors, missing dependencies, security policy rejections, build artifact omissions) and generate structured, actionable recommendations.
7. **Test Quality:** The test suite contains **32 test classes** with **135 automated unit, integration, and security tests**, all passing with zero errors and zero failures (57.3s full execution).

---

## 2. Repository and Environment Baseline

| Parameter | Observed Value | Forensic Evidence / Command Source |
|---|---|---|
| **Repository Path** | `/Users/kaviselvaramkathirvel/javacicd` | `pwd` |
| **Active Git Branch** | `main` | `git branch -v` (`da93012`) |
| **HEAD Commit** | `da93012` (*feat: complete master UI/UX revision...*) | `git log -1 --oneline` |
| **Working-Tree Status** | Modified tracked files + untracked test/doc assets | `git status -s` |
| **Java Runtime** | `25.0.1+8-LTS-27` (Oracle JDK) | `java -version` |
| **Compiler Target** | Java 21 (`<maven.compiler.release>21</maven.compiler.release>`) | `pom.xml:19` |
| **Maven Version** | Apache Maven 3.6.3 | `mvn -version` |
| **JavaFX Version** | `21.0.6` | `pom.xml:20` |
| **Main Entry Point** | `com.devopshub.app.Launcher` (Fat JAR) / `com.devopshub.app.MainApp` (JavaFX App) | `pom.xml:125,137`, `Launcher.java:6`, `MainApp.java:15` |
| **Active OS** | macOS Darwin 26.6.2 (Apple Silicon `aarch64`) | `uname -a` |
| **Running PID** | PID `39814` (`com.devopshub.app.MainApp` on JavaFX module-path) | `ps aux \| grep MainApp` |
| **Running MySQL** | Container `devopshub-mysql` (Image `mysql:8.0`, Up 2+ hours, Port `3306->3306`) | `docker ps -a` |

---

## 3. Complete Technology Stack

### 3.1 Programming Languages

| Language | Actual Role in DevOpsHub | Representative Source Files |
|---|---|---|
| **Java** (JDK 21/25) | Core application logic, JavaFX UI, threading, execution engine, security scanners, HTTP server, and data access. | `src/main/java/com/devopshub/**/*.java` (64 files), `src/test/java/**/*.java` (32 files) |
| **SQL** (MySQL/H2 dialect) | Schema definition, foreign key relations, audit tables, indexes, and seed datasets. | `database/migrations/V1__initial_schema.sql`, `V4__pipeline_schedules.sql` |
| **CSS** (JavaFX CSS dialect) | Dark-theme styling, glassmorphism, typography, layout properties, and visual controls. | `src/main/resources/css/devopshub.css` (798 lines) |
| **XML** | Maven project build configuration (`pom.xml`) and Logback logging layout (`logback.xml`). | `pom.xml`, `src/main/resources/logback.xml` |
| **Shell (Bash)** | Container lifecycle orchestration, application launch automation, test harness runners. | `scripts/start_mysql.sh`, `scripts/run_app.sh`, `scripts/run_tests.sh` |
| **Python** | Automated PDF catalog generation script using ReportLab for UI spec documentation. | `scripts/generate_ui_pdf.py` |

### 3.2 Frameworks and Libraries (Dependency Inventory)

```
DevOpsHub Desktop MVP (pom.xml)
├── org.openjfx:javafx-controls:21.0.6 (Direct, Compile) -> Native UI Controls
├── org.openjfx:javafx-graphics:21.0.6 (Direct, Compile) -> Scene Graph & Hardware Acceleration
├── org.openjfx:javafx-fxml:21.0.6 (Direct, Compile) -> Declared for future FXML (unused)
├── com.mysql:mysql-connector-j:8.3.0 (Direct, Compile) -> JDBC Type 4 MySQL Network Driver
├── com.zaxxer:HikariCP:5.1.0 (Direct, Compile) -> High-Performance JDBC Connection Pool
├── com.h2database:h2:2.2.224 (Direct, Compile) -> Embedded SQL Database (Persistent Fallback & Tests)
├── com.google.code.gson:gson:2.10.1 (Direct, Compile) -> JSON Parsing for Webhooks & Project Inspection
├── org.slf4j:slf4j-api:2.0.12 (Direct, Compile) -> Logging Facade
├── ch.qos.logback:logback-classic:1.5.3 (Direct, Compile) -> Logging Implementation
└── org.junit.jupiter:junit-jupiter:5.10.2 (Direct, Test) -> Testing Framework
```

| Dependency | Version | Declared Scope | Role in Application | Actual Usage Verification |
|---|---|---|---|---|
| `javafx-controls` | `21.0.6` | Compile | UI widgets: `Button`, `TableView`, `Label`, `TextField`, `ProgressBar`, `ComboBox` | Direct: Imported across all 14 views in `com.devopshub.ui.view` |
| `javafx-graphics` | `21.0.6` | Compile | Layout panes (`VBox`, `HBox`, `BorderPane`, `StackPane`), `Scene`, `Stage`, Canvas | Direct: `MainLayoutView`, `MainApp`, custom SVG rendering |
| `javafx-fxml` | `21.0.6` | Compile | FXML markup loader | **Declared but Unused:** Entire UI is programmatic Java. No `.fxml` files exist in `src/main/resources`. |
| `mysql-connector-j` | `8.3.0` | Compile | MySQL JDBC Driver | Direct: Loaded dynamically by HikariCP when connecting to MySQL (`com.mysql.cj.jdbc.Driver`) |
| `HikariCP` | `5.1.0` | Compile | JDBC Connection Pooling | Direct: Initialized in `DatabaseManager.java:31-83` for both MySQL and H2 |
| `h2` | `2.2.224` | Compile | Embedded SQL database | Direct: Fallback persistent engine in `DatabaseManager.java:62-84` and automated test fixtures |
| `gson` | `2.10.1` | Compile | JSON Serialization | Direct: In `WebhookServerService.java:197`, `ProjectImportService.java:105`, and `ExecutionEngine.java:829` |
| `slf4j-api` | `2.0.12` | Compile | Logging abstraction | Direct: Used in 35+ core engine, service, and repository classes |
| `logback-classic` | `1.5.3` | Compile | SLF4J binding & appender | Direct: Configured via `src/main/resources/logback.xml` |
| `junit-jupiter` | `5.10.2` | Test | Unit & integration tests | Direct: Powers all 32 test suites in `src/test/java` |

### 3.3 Build and Development Tools

1. **Maven Compiler Plugin (`3.12.1`):** Configured for `<release>21</release>`. Enforces strict Java 21 bytecode compliance on JDK 25.
2. **Maven Surefire Plugin (`3.2.5`):** Configured with `--add-opens java.base/java.lang=ALL-UNNAMED` to allow reflective test inspection and isolated test runners.
3. **JavaFX Maven Plugin (`0.0.8`):** Binds to `com.devopshub.app.MainApp` to resolve modular dependencies (`--module-path`) during local execution via `mvn javafx:run`.
4. **Maven JAR Plugin (`3.3.0`):** Packages `Launcher.java` as the entry point in `MANIFEST.MF` to bypass module restrictions when launching non-modular fat JARs.
5. **Maven Dependency Plugin (`3.6.1`):** Copies runtime dependencies into `target/libs` during the `package` phase to support thin distribution.
6. **Maven Assembly Plugin (`3.6.0`):** Configured with `jar-with-dependencies` descriptor to build a standalone single-file executable JAR (`devopshub-desktop-1.0.0-jar-with-dependencies.jar`).
7. **Docker Engine (`29.6.1`):** Hosts the MySQL container (`devopshub-mysql`) and serves as the secure runtime sandbox for untrusted project builds.

---

## 4. Project Structure Map & Layer Architecture

```
/Users/kaviselvaramkathirvel/javacicd
├── database/
│   └── migrations/               # Canonical SQL migration scripts (V1, V2, V3, V4)
├── data/                         # Durable disk-backed H2 storage folder (fallback mode)
├── docs/                         # Specifications, audits, UI reference, guides
│   ├── engineering/              # Technical audit reports, completion ledgers, architecture
│   └── audit/                    # Forensic reports and UI screenshots
├── scripts/                      # Lifecycle and orchestration shell scripts
├── src/
│   ├── main/
│   │   ├── java/com/devopshub/
│   │   │   ├── analysis/         # Failure diagnosis engine & heuristic rule implementations
│   │   │   │   └── rules/        # 12 concrete failure diagnosis rules
│   │   │   ├── app/              # Application entry points (Launcher, MainApp)
│   │   │   ├── config/           # Central configuration (AppConfig, DatabaseManager)
│   │   │   ├── deployment/       # Multi-target deployment engine & verification
│   │   │   ├── exception/        # Custom domain exceptions
│   │   │   ├── execution/        # Pipeline execution engine, stage runner, log collectors
│   │   │   │   ├── backend/      # Isolation backends (Host, Docker, Unavailable)
│   │   │   │   ├── event/        # Execution lifecycle event bus & event models
│   │   │   │   └── isolation/    # Security policies, trust level classification
│   │   │   ├── git/              # Git CLI integration service & workspace confinement
│   │   │   ├── model/            # Domain entities, enums, DTOs
│   │   │   ├── pipeline/         # Topological DAG validator & cycle detection
│   │   │   ├── repository/       # Raw JDBC repository implementations
│   │   │   ├── scheduler/        # Automated cron/interval pipeline scheduling
│   │   │   ├── security/         # Static secret & dependency audit scanners
│   │   │   ├── service/          # Business service layer (Project, Pipeline, Dashboard)
│   │   │   ├── ui/               # JavaFX visual interface
│   │   │   │   ├── component/    # Reusable widgets (StatusBadge, SvgIcon, ToastNotification)
│   │   │   │   └── view/         # 14 primary view layouts (Dashboard, Detail, History, etc.)
│   │   │   ├── util/             # Utility classes (SecretMasker)
│   │   │   └── webhook/          # HTTP server, HMAC-SHA256 signature verification
│   │   └── resources/
│   │       ├── css/              # JavaFX stylesheet (devopshub.css)
│   │       ├── db/migrations/    # Classpath-packaged migration scripts
│   │       ├── icons/svg/        # 20 vector SVG icons
│   │       └── logback.xml       # SLF4J console logging config
│   └── test/java/com/devopshub/  # 32 JUnit 5 test classes (135 tests)
├── workspaces/                   # Build workspace parent directory
│   └── projects/                 # Cloned Git repository workspaces (<project-id>)
├── pom.xml                       # Maven build descriptor
└── .env                          # Local environment variable overrides
```

### 4.1 Layer Dependency Flow

```mermaid
graph TD
    UI[JavaFX UI Layer\ncom.devopshub.ui.view] --> Service[Service Layer\nProjectService, PipelineService, DashboardService]
    UI --> Engine[Execution Engine\nExecutionEngine, StageRunner]
    UI --> Repo[Repository Layer\nProjectRepo, PipelineRepo, ExecutionRepo]
    
    Service --> Repo
    Service --> Validator[DAG Validator\nPipelineValidator]
    Service --> Git[Git Service\nGitService]
    
    Engine --> Isolation[Execution Isolation Service\nExecutionPolicy, TrustLevel]
    Engine --> Backends[Execution Backends\nDockerExecutionBackend, HostExecutionBackend]
    Engine --> EventBus[Event Bus\nExecutionEventBus]
    Engine --> RuleEngine[Failure Rule Engine\nFailureRuleEngine]
    Engine --> DeployService[Deployment Service\nDeploymentService]
    Engine --> Repo
    Engine --> Git
    
    Backends --> OS[Host OS Process / Docker Daemon]
    DeployService --> Repo
    RuleEngine --> Repo
    
    Webhook[Webhook Server\nWebhookServerService] --> Engine
    Scheduler[Pipeline Scheduler\nPipelineSchedulerService] --> Engine
    Scheduler --> Repo
    
    Repo --> Pool[Connection Pool\nHikariCP]
    Pool --> DB[(MySQL 8.0 Primary / H2 Fallback)]
```

### 4.2 Architectural Assessment & Boundary Violations

1. **Clean Separation of Execution Engine:** The `ExecutionEngine` operates completely asynchronously from the JavaFX UI. It receives `Consumer<Execution>` and `Consumer<ExecutionLog>` callbacks and publishes fine-grained events onto `ExecutionEventBus`. UI controllers never manage child processes or execute background build commands directly.
2. **Repository Abstraction:** SQL queries are strictly confined to `com.devopshub.repository`. Views and services never call `java.sql` directly.
3. **Slight Service Bypassing in Views:** In certain read-heavy views (e.g., `LogViewerView`, `ExecutionHistoryView`), views query repositories directly rather than routing through a dedicated service layer. While this is common in desktop MVP architectures, introducing formal query services for logs and execution events would further enforce layered purity.

---

## 5. Application Architecture & Startup Flow

### 5.1 Startup Sequence

```mermaid
sequenceDiagram
    autonumber
    participant JVM as JVM / OS
    participant Launcher as Launcher.main()
    participant MainApp as MainApp.start()
    participant Config as AppConfig
    participant DB as DatabaseManager
    participant Migrations as SQL Migrations
    participant Webhook as WebhookServerService
    participant Scheduler as PipelineSchedulerService
    participant Shell as MainLayoutView
    participant UI as PrimaryStage

    JVM->>Launcher: Invoke main(args)
    Note over Launcher: Evaluates CLI flags (--version, --health, --help)
    Launcher->>MainApp: MainApp.main(args) -> launch(args)
    MainApp->>MainApp: start(primaryStage)
    MainApp->>Config: AppConfig.getInstance()
    Note over Config: Loads defaults -> .env -> System.getenv()
    MainApp->>DB: DatabaseManager.getInstance()
    Note over DB: Probes MySQL connection via HikariCP (5s timeout)
    alt MySQL Available
        DB->>DB: Initialize MySQL Pool (10 connections)
    else MySQL Unavailable
        DB->>DB: Initialize H2 Disk-Backed Pool (./data/devopshub)
    end
    DB->>Migrations: applyMigrations()
    Note over Migrations: Verifies schema_version; executes V1 & V4 scripts; ensures columns
    DB->>DB: reconcileInterruptedExecutions() (sets RUNNING -> FAILED)
    MainApp->>Webhook: WebhookServerService.getInstance().startServer()
    Note over Webhook: Binds HttpServer on configured port (8089/8080)
    MainApp->>Scheduler: PipelineSchedulerService.getInstance()
    Note over Scheduler: Loads persisted schedules from DB & arms ScheduledExecutor
    MainApp->>Shell: new MainLayoutView(primaryStage)
    Note over Shell: Constructs Sidebar, TopHeader, ContentStack, AlertsSidebar
    Shell->>Shell: navigateToHome()
    MainApp->>UI: scene.getStylesheets().add("/css/devopshub.css")
    MainApp->>UI: primaryStage.show()
    Note over UI: Window displayed to user
```

### 5.2 Graceful Shutdown Flow

When the window close request (`primaryStage.setOnCloseRequest`) is triggered:
1. `WebhookServerService.stopServer()` stops the HTTP listener immediately (`server.stop(1)`).
2. `PipelineSchedulerService.shutdown()` terminates scheduled executors (`scheduler.shutdownNow()`).
3. `ExecutionThreadPool.shutdown()` gracefully halts pipeline executors and stage workers with a 3-second timeout before forcible shutdown.
4. `DatabaseManager.close()` safely flushes HikariCP pools, closes active physical connections, and unregisters JDBC drivers.

---

## 6. End-to-End Operational Workflows

### 6.1 Workflow A — Project Creation & Import

```
User (ProjectsView / Modal) 
  ──> ProjectImportService.inspectPath(dirOrUrl)
        ├── Validates existence & canonical path
        ├── Detects build system (Maven pom.xml, Gradle build.gradle, npm package.json, Next.js, Python, Go)
        └── Synthesizes default pipeline stages (Checkout -> Build -> Test -> Package -> Deploy)
  ──> ProjectService.createProject(name, desc, repoUrl, branch, projectType)
        ├── Validates name (non-empty, <=100 chars) & URL format
        └── ProjectRepository.save(project) [INSERT INTO projects]
  ──> MainLayoutView.showToast("Project created successfully", SUCCESS)
  ──> ProjectsView.refresh() -> ProjectRepository.findAll() -> TableView update
```

### 6.2 Workflow B — Pipeline Execution

```
User clicks 'Run' (PipelinesView / ProjectDetailView / PipelineDetailView)
  ──> ExecutionEngine.startExecution(pipeline, TriggerType.MANUAL, onUpdate, onLog)
        ├── ExecutionRepository.getNextExecutionNumber(pipelineId)
        ├── Creates Execution entity (Status: RUNNING, Phase: 'Queued')
        ├── ExecutionRepository.save(execution) [INSERT INTO executions]
        ├── Emits ExecutionEventType.EXECUTION_QUEUED onto ExecutionEventBus
        ├── Creates & saves StageExecution entities (Status: QUEUED)
        ├── Submits task to ExecutionThreadPool.getPipelineExecutor()
  ──> Worker Thread: ExecutionEngine.runPipelineWorkflow(...)
        ├── Resolves project & workspace: GitService.getProjectWorkspace(projectId) -> ./workspaces/projects/<id>
        ├── Evaluates Security Boundary: ExecutionIsolationService.resolveTrustLevel(...)
        │     ├── TRUSTED_LOCAL -> HostExecutionBackend (Restricted & Sanitized)
        │     └── UNTRUSTED_EXTERNAL -> DockerExecutionBackend (Fail-closed if Docker absent)
        ├── Checks out Git repository (GitService.cloneOrFetch):
        │     ├── If pre-existing: git remote check, git fetch, git checkout <branch>, git pull
        │     └── If fresh: git clone --branch <branch> --single-branch <url> <dest>
        ├── Resolves commit hash: git rev-parse HEAD -> Execution.setCommitHash()
        ├── Loops through Pipeline Stages:
        │     ├── Sequential: Submits StageRunner.call()
        │     ├── Parallel Fork: ParallelStageCoordinator.executeParallel(runners) via stageExecutor
        │     ├── Command execution via Backend (Host ProcessBuilder or Docker run)
        │     ├── Log streaming: stdout/stderr threads -> LogCollector -> BoundedLogBuffer -> UI + DB batch insert
        │     └── Exit code evaluation: 0 = SUCCESS, non-zero = FAILED, 130 = CANCELLED
        ├── Discovers generated build artifacts: ExecutionEngine.inspectBuildArtifact(workspace)
        │     ├── Next.js (.next/BUILD_ID), JAR/WAR (target/*.jar), or web bundle (dist/)
        │     └── Calculates size and verifies SHA-256 checksum
        ├── If Failed: FailureRuleEngine.analyze(failedStage, logs)
        │     ├── Evaluates 12 heuristic rules
        │     └── DiagnosisRepository.save(diagnosis) [INSERT INTO failure_diagnoses]
        ├── Finalizes execution status (SUCCESS/FAILED/CANCELLED) & duration
        ├── ExecutionRepository.update(execution)
        └── Emits EXECUTION_COMPLETED / EXECUTION_FAILED events -> UI refreshes
```

### 6.3 Workflow C — Database Persistence

```
Application Configuration (AppConfig)
  ──> DatabaseManager (HikariDataSource)
        ├── Connection borrowed from HikariCP pool
        ├── Repository (e.g. ExecutionRepository) prepares PreparedStatement
        ├── Parameters bound safely via ps.setString(), ps.setInt(), ps.setTimestamp()
        ├── Network dispatch to MySQL (tcp://localhost:3306/devopshub)
        ├── ResultSet read and converted into immutable or POJO domain model
        └── Connection returned to pool via try-with-resources auto-close
```

### 6.4 Workflow D — Webhook-Triggered Execution

```
External System (GitHub / GitLab / curl)
  ──> HTTP POST http://localhost:8089/api/webhook/trigger?pipelineId=<id>
        ├── WebhookServerService.WebhookHandler.handle(HttpExchange)
        ├── Reads raw request bytes (max 1MB limit)
        ├── Verifies HMAC-SHA256 signature (X-Hub-Signature-256 header):
        │     ├── Calculates HMAC-SHA256(rawBytes, WEBHOOK_SECRET_KEY)
        │     └── Constant-time comparison: MessageDigest.isEqual(expected, incoming)
        ├── Replay Protection:
        │     ├── Validates timestamp (X-Hub-Timestamp) within 300,000ms (5 min) window
        │     └── Deduplicates delivery ID (X-GitHub-Delivery) against in-memory cache
        ├── Resolves pipeline via PipelineService.getPipelineById(pipelineId)
        ├── Triggers ExecutionEngine.startExecution(pipeline, TriggerType.WEBHOOK)
        └── Responds with 200 OK: {"status":"QUEUED","executionId":"...","executionNumber":1}
```

### 6.5 Workflow E — Artifact Deployment

```
Execution Engine encounters StageType.DEPLOY
  ──> ExecutionEngine.findDeploymentArtifact(workspace)
        ├── Discovers built artifact in target/, build/libs/, or dist/
        └── Validates existence and non-zero byte size
  ──> DeploymentService.deployArtifact(artifact, config, executionId, pipelineId)
        ├── Target: LOCAL_STAGING
        │     ├── LocalStagingDeploymentProvider.deploy(...)
        │     ├── Backs up existing deployment (*.rollback-backup)
        │     ├── Computes source SHA-256: DeploymentService.computeSha256(source)
        │     ├── Files.copy(source, destination, REPLACE_EXISTING, COPY_ATTRIBUTES)
        │     ├── Computes destination SHA-256 & verifies exact match
        │     └── DeploymentRepository.save(record) [INSERT INTO deployment_records]
        └── Target: SSH_REMOTE
              ├── SshRemoteDeploymentProvider.deploy(...)
              ├── Invokes non-interactive 'scp -B -P port ...' child process
              └── Verifies exit code 0 and logs remote destination
```

---

## 7. JDBC Implementation — Exact Source Mapping

DevOpsHub implements data access via **raw JDBC** over a **HikariCP** connection pool. It uses no ORM, no bytecode enhancement, and no third-party query builders.

### 7.1 JDBC Architecture Summary

- **Connection Provider:** `com.zaxxer.hikari.HikariDataSource` managed as a singleton in `com.devopshub.config.DatabaseManager`.
- **Driver Discovery:** Automatically loaded from classpath via JDBC Service Provider Interface (SPI):
  - MySQL: `com.mysql.cj.jdbc.Driver` (from `com.mysql:mysql-connector-j:8.3.0`)
  - H2: `org.h2.Driver` (from `com.h2database:h2:2.2.224`)
- **Statement Preparation:** 100% of parameterized queries use `java.sql.PreparedStatement` with explicit positional parameter binding (`ps.setString(1, ...)`, `ps.setInt(2, ...)`). No string concatenation is used for user-supplied SQL values.
- **Resource Management:** Strict idiomatic Java `try (Connection conn = ...; PreparedStatement ps = ...; ResultSet rs = ...)` blocks guarantee connection and cursor closure even upon unhandled runtime exceptions.

### 7.2 Method-Level Repository Mapping

| Repository Class & File Path | Method Name | Source Lines | Primary Tables Accessed | SQL Operation & Implementation Details |
|---|---|---|---|---|
| `DatabaseManager.java` (`src/main/java/com/devopshub/config/DatabaseManager.java`) | `<init>` | L29–92 | Connection Pool | Initializes HikariCP. Attempts MySQL connection; falls back to embedded H2 if MySQL is unavailable. |
| `DatabaseManager.java` | `applyMigrations` | L172–209 | `schema_version`, all tables | Applies V1 and V4 migrations from classpath resources if not previously recorded. |
| `DatabaseManager.java` | `ensureExecutionMetadataColumns` | L211–274 | `executions`, `stage_executions` | Inspects `DatabaseMetaData.getColumns()`; issues `ALTER TABLE ADD COLUMN` for `commit_hash`, `artifact_path`, `command`. |
| `DatabaseManager.java` | `reconcileInterruptedExecutions` | L323–335 | `executions`, `stage_executions` | Executes `UPDATE executions SET status='FAILED' WHERE status IN ('RUNNING','QUEUED')` to clear orphaned runs. |
| `ProjectRepository.java` (`src/main/java/com/devopshub/repository/ProjectRepository.java`) | `findAll` | L26–43 | `projects`, `pipelines`, `executions` | Correlated subqueries calculate `pipeline_count` and latest execution status. Maps results to `Project` objects. |
| `ProjectRepository.java` | `findById` | L45–63 | `projects`, `pipelines`, `executions` | Parameterized query `WHERE p.id = ?`. Returns `Optional<Project>`. |
| `ProjectRepository.java` | `save` | L90–110 | `projects` | `INSERT INTO projects (...) VALUES (?, ?, ...)`. Explicit timestamps. |
| `ProjectRepository.java` | `update` | L112–130 | `projects` | `UPDATE projects SET name=?, description=?, ... WHERE id=?`. |
| `ProjectRepository.java` | `delete` | L132–143 | `projects` | `DELETE FROM projects WHERE id=?`. Cascades to child pipelines and executions. |
| `PipelineRepository.java` (`src/main/java/com/devopshub/repository/PipelineRepository.java`) | `findAll` | L27–52 | `pipelines`, `projects`, `executions`, `pipeline_stages` | Joins `projects`, calculates execution counts, and attaches child stages via `findStagesByPipelineId`. |
| `PipelineRepository.java` | `save` | L107–145 | `pipelines`, `pipeline_stages`, `stage_dependencies` | **Explicit Transaction Management:** Calls `conn.setAutoCommit(false)`, inserts pipeline, saves stages, calls `conn.commit()`, with `conn.rollback()` on error. |
| `PipelineRepository.java` | `findStagesByPipelineId` | L231–264 | `pipeline_stages`, `stage_dependencies` | Queries stages sorted by `stage_order ASC` and queries prerequisite stage IDs. |
| `ExecutionRepository.java` (`src/main/java/com/devopshub/repository/ExecutionRepository.java`) | `save` | L173–196 | `executions` | `INSERT INTO executions (id, pipeline_id, project_id, execution_number, status, ...) VALUES (...)`. |
| `ExecutionRepository.java` | `update` | L198–214 | `executions` | Updates execution status, timestamps, duration, commit hash, and artifact path. |
| `ExecutionRepository.java` | `saveStageExecution` | L247–270 | `stage_executions` | Inserts stage execution record with `ON DUPLICATE KEY UPDATE` to track live status transitions. |
| `ExecutionRepository.java` | `findStageExecutionsByExecutionId` | L216–245 | `stage_executions`, `pipeline_stages` | Resolves stage execution status, duration, error message, and stage command via `LEFT JOIN`. |
| `LogRepository.java` (`src/main/java/com/devopshub/repository/LogRepository.java`) | `save` | L27–48 | `execution_logs` | Inserts individual log line using `Statement.RETURN_GENERATED_KEYS` to obtain auto-incremented ID. |
| `LogRepository.java` | `saveBatch` | L50–72 | `execution_logs` | **Batch JDBC Execution:** Disables autocommit (`conn.setAutoCommit(false)`), loops lines calling `ps.addBatch()`, invokes `ps.executeBatch()`, commits. |
| `LogRepository.java` | `findByExecutionId` | L74–90 | `execution_logs` | Queries logs ordered by `id ASC` with a safety `LIMIT 5000` to prevent UI memory exhaustion. |
| `DiagnosisRepository.java` (`src/main/java/com/devopshub/repository/DiagnosisRepository.java`) | `save` | L25–49 | `failure_diagnoses` | Upserts diagnosis with `ON DUPLICATE KEY UPDATE` to associate root cause analysis with execution. |
| `ScheduleRepository.java` (`src/main/java/com/devopshub/repository/ScheduleRepository.java`) | `save` | L29–58 | `pipeline_schedules` | Persists scheduled pipeline interval, execution count, last execution ID, and next run timestamp. |
| `DeploymentRepository.java` (`src/main/java/com/devopshub/repository/DeploymentRepository.java`) | `save` | L31–57 | `deployment_records` | Persists deployment target, environment, status, artifact path, duration, and SHA-256 checksum. |
| `SecurityFindingRepository.java` (`src/main/java/com/devopshub/repository/SecurityFindingRepository.java`) | `saveAll` | L63–97 | `security_findings` | Batch inserts security scanner findings (`ps.addBatch()`, `ps.executeBatch()`). |
| `ExecutionEventRepository.java` (`src/main/java/com/devopshub/repository/ExecutionEventRepository.java`) | `save` | L64–98 | `execution_events` | Persists fine-grained lifecycle events with monotonic sequence numbers and status transitions. |

---

## 8. Physical Database Storage & Runtime Backend

### 8.1 Primary Engine: MySQL 8.0

- **Configuration:** Defined in `.env` / `.env.example` as `DB_HOST=localhost`, `DB_PORT=3306`, `DB_NAME=devopshub`, `DB_USER=root`, `DB_PASSWORD=[REDACTED]`.
- **JDBC Endpoint:** `jdbc:mysql://localhost:3306/devopshub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` (`AppConfig.java:112–115`).
- **Container Instance:** Verified active container named `devopshub-mysql` running image `mysql:8.0.46-1.el9`.
- **Physical Data Location on Host:** The container does not use a host bind mount. It uses a Docker-managed named volume:
  ```json
  [{"Type":"volume","Name":"e375552b694725b3b3559e46ef77d4dddee41555c731ad8ad584a72c4efccc0a","Destination":"/var/lib/mysql"}]
  ```
  On macOS Docker Desktop, this volume resides within the Docker virtual machine disk image (`/var/lib/docker/volumes/.../_data`). It is **not** stored inside the project workspace or `./data`.
- **Data Durability:** Fully durable across application and container restarts. Verified live data in MySQL currently contains 1 project (`testair`), 1 pipeline, 1 completed execution, 5 stage executions, 103 execution logs, and 26 lifecycle events.

### 8.2 Fallback Engine: Embedded H2 Database

- **Fallback Trigger:** If MySQL connection to `localhost:3306` fails during `DatabaseManager` initialization (e.g. Docker daemon is down or container is stopped), a 5000ms timeout occurs, catching `SQLException` (`DatabaseManager.java:56–60`).
- **Storage Mode Selection:**
  - If `H2_IN_MEMORY_ONLY=true` (used only for ephemeral unit tests): URL is `jdbc:h2:mem:devopshub;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1`. Data exists only in JVM RAM and is lost on process termination.
  - If `H2_IN_MEMORY_ONLY=false` (standard fallback): URL is `jdbc:h2:file:<absolute-path>/data/devopshub;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`.
- **Resolved Physical File Path:** Resolved to `/Users/kaviselvaramkathirvel/javacicd/data/devopshub.mv.db`.
- **Dataset Synchronization:** **There is no dual-write or automatic synchronization between MySQL and H2.** If the application switches from MySQL to H2, it reads from the local `.mv.db` file, which is independent of the MySQL Docker volume.

### 8.3 Runtime Verification Matrix

| Question | Forensic Answer | Evidence |
|---|---|---|
| **What database is the running application using?** | **MySQL 8.0.46** (Primary Persistent) | Live PID 39814 log: `Successfully connected to MySQL database: devopshub`. Container `devopshub-mysql` port 3306 open and healthy. |
| **Where is MySQL physically stored?** | Inside Docker VM volume `e375552b...` at `/var/lib/mysql` | `docker inspect devopshub-mysql` |
| **Where is H2 physically stored?** | `/Users/kaviselvaramkathirvel/javacicd/data/devopshub.mv.db` (when fallback is active) | `DatabaseManager.java:68–76` |
| **What data disappears upon restart?** | **None** under normal MySQL operation. If running in `H2_IN_MEMORY_ONLY=true`, all records disappear upon exit. Under disk-backed H2 or MySQL, all data persists. | Verified in `DatabaseManagerPersistenceAuditTest.java` |

---

## 9. Database Schema & Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    projects ||--o{ pipelines : "has"
    projects ||--o{ executions : "owns"
    pipelines ||--o{ pipeline_stages : "contains"
    pipelines ||--o{ executions : "instantiates"
    pipelines ||--o{ pipeline_schedules : "scheduled by"
    pipeline_stages ||--o{ stage_dependencies : "target of"
    pipeline_stages ||--o{ stage_dependencies : "depends on"
    pipeline_stages ||--o{ stage_executions : "template for"
    executions ||--o{ stage_executions : "composed of"
    executions ||--o{ execution_logs : "records"
    executions ||--o{ execution_events : "publishes"
    executions ||--o{ failure_diagnoses : "analyzed by"
    executions ||--o{ security_findings : "identifies"
    executions ||--o{ deployment_records : "triggers"

    projects {
        varchar(100) id PK
        varchar(100) name UK
        text description
        varchar(255) repository_url
        varchar(50) default_branch
        varchar(50) project_type
        varchar(20) status
        timestamp created_at
        timestamp updated_at
    }

    pipelines {
        varchar(100) id PK
        varchar(100) project_id FK
        varchar(100) name
        text description
        varchar(20) execution_mode
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    pipeline_stages {
        varchar(100) id PK
        varchar(100) pipeline_id FK
        varchar(100) name
        varchar(50) stage_type
        int stage_order
        text command
        int timeout_seconds
        boolean is_enabled
        varchar(50) parallel_group
        timestamp created_at
    }

    stage_dependencies {
        varchar(100) id PK
        varchar(100) stage_id FK
        varchar(100) depends_on_stage_id FK
    }

    executions {
        varchar(100) id PK
        varchar(100) pipeline_id FK
        varchar(100) project_id FK
        int execution_number
        varchar(20) status
        varchar(50) trigger_type
        varchar(100) commit_hash
        varchar(500) artifact_path
        timestamp started_at
        timestamp finished_at
        double duration_seconds
        timestamp created_at
    }

    stage_executions {
        varchar(100) id PK
        varchar(100) execution_id FK
        varchar(100) stage_id FK
        varchar(100) stage_name
        varchar(50) stage_type
        text command
        varchar(20) status
        int exit_code
        timestamp started_at
        timestamp finished_at
        double duration_seconds
        text error_message
    }

    execution_logs {
        bigint id PK
        varchar(100) execution_id FK
        varchar(100) stage_execution_id
        varchar(100) stage_name
        varchar(10) log_level
        varchar(10) stream
        text message
        timestamp timestamp
    }

    failure_diagnoses {
        varchar(100) id PK
        varchar(100) execution_id FK
        varchar(100) stage_execution_id
        varchar(100) failed_stage
        varchar(50) category
        varchar(255) title
        text likely_cause
        text evidence_excerpt
        text suggested_action
        varchar(50) rule_id
        varchar(20) rule_version
        timestamp analyzed_at
    }

    pipeline_schedules {
        varchar(100) id PK
        varchar(100) pipeline_id FK, UK
        bigint interval_seconds
        boolean is_enabled
        int execution_count
        varchar(100) last_execution_id
        timestamp last_run_at
        timestamp next_run_at
        timestamp created_at
        timestamp updated_at
    }

    security_findings {
        varchar(100) id PK
        varchar(100) execution_id FK
        varchar(100) stage_execution_id
        varchar(100) rule_id
        varchar(20) severity
        varchar(50) category
        varchar(255) title
        varchar(500) file_path
        int line_number
        text description
        text remediation
        timestamp created_at
    }

    deployment_records {
        varchar(100) id PK
        varchar(100) execution_id FK
        varchar(100) pipeline_id FK
        varchar(50) target_type
        varchar(50) environment
        varchar(50) status
        varchar(500) source_artifact
        varchar(500) target_destination
        varchar(64) sha256_checksum
        double duration_seconds
        timestamp deployed_at
        text details
    }

    execution_events {
        varchar(100) id PK
        varchar(100) execution_id
        bigint sequence_number
        varchar(100) pipeline_id
        varchar(100) stage_execution_id
        varchar(100) stage_name
        int stage_order
        varchar(50) event_type
        varchar(100) status_transition
        text message
        double duration_seconds
        int exit_code
        boolean is_authoritative
        timestamp timestamp
    }
```

---

## 10. JavaFX Implementation & UI Architecture

### 10.1 UI Component & Layout Hierarchy

DevOpsHub constructs its entire visual interface programmatically in Java using JavaFX 21 controls and scene graph containers. There are no FXML files.

```
MainApp (javafx.application.Application)
  └── Stage (primaryStage, min 1024x700, default 1280x800)
        └── Scene (stylesheet: /css/devopshub.css)
              └── MainLayoutView (extends BorderPane)
                    ├── Left: Sidebar (extends VBox)
                    │     ├── macOS Traffic Lights (HBox of styled Regions)
                    │     ├── Brand Box (CPU Icon, DEVOPSHUB title)
                    │     ├── Navigation Buttons (Home, Dashboard, Projects, Pipelines, Builder, Executions, Logs, Analytics, Settings)
                    │     └── Database Status Footer Card (MySQL/H2 indicator)
                    ├── Right: AlertsSidebarView (collapsible VBox, responsive auto-hide < 1100px)
                    └── Center: VBox
                          ├── TopHeader (HBox)
                          │     ├── Page Title & Subtitle Labels
                          │     ├── Global Search Field (TextField, connects to active view filters)
                          │     ├── Date Badge Label
                          │     ├── Live DB Latency Pill (Clickable ping trigger)
                          │     ├── Alerts Toggle Button
                          │     └── Header Action Buttons (Contextual "New Project", "New Pipeline", etc.)
                          └── ContentStack (StackPane)
                                └── ScrollPane (fitToWidth: true)
                                      └── Active View (HomeView, DashboardView, ProjectsView, etc.)
```

### 10.2 Major View Implementations

| View Name | Class File Path | UI Elements & Controls | Services & Engines Invoked | Threading & Refresh Behavior |
|---|---|---|---|---|
| **HomeView** | `src/main/java/com/devopshub/ui/view/HomeView.java` | Metric cards, quick actions grid, active projects table, recent executions table | `ProjectService`, `DashboardService` | Synchronous initial load with async action triggers; navigates to project or execution details. |
| **DashboardView** | `src/main/java/com/devopshub/ui/view/DashboardView.java` | `MetricCard` widgets, active running pipelines card, failure breakdown chart, recent activity feed | `DashboardService`, `ExecutionEngine` | Live updates via `ExecutionEngine` listener; displays real operational metrics. |
| **ProjectsView** | `src/main/java/com/devopshub/ui/view/ProjectsView.java` | Search bar, project type filter combo, `TableView<Project>`, New Project modal dialog | `ProjectService`, `ProjectImportService` | Filtering occurs on JavaFX Application Thread; archive extraction runs in modal with error toast. |
| **ProjectDetailView** | `src/main/java/com/devopshub/ui/view/ProjectDetailView.java` | Project header, metadata cards, pipelines table, execution history table, Delete confirmation | `ProjectService`, `PipelineService`, `ExecutionEngine` | Refreshes on view activation; Run button launches background execution. |
| **PipelinesView** | `src/main/java/com/devopshub/ui/view/PipelinesView.java` | Search bar, mode filter, `TableView<Pipeline>`, Run / Edit / Delete buttons | `PipelineService`, `ExecutionEngine` | Run button invokes `executionEngine.startExecution(...)` asynchronously; updates table status badge. |
| **PipelineDetailView** | `src/main/java/com/devopshub/ui/view/PipelineDetailView.java` | Stage sequence topology cards, historical execution runs table, Run Pipeline button | `PipelineService`, `ExecutionEngine` | Topological card layout; click triggers run and navigates to `ExecutionDetailView`. |
| **PipelineBuilderView** | `src/main/java/com/devopshub/ui/view/PipelineBuilderView.java` | Interactive stage list, reorder up/down buttons, command editors, timeout fields, DAG cycle validator | `PipelineService`, `ProjectService`, `PipelineValidator` | Interactive stage addition; validates DAG via DFS cycle check before saving. |
| **ExecutionHistoryView** | `src/main/java/com/devopshub/ui/view/ExecutionHistoryView.java` | Status filter combo, search bar, `TableView<Execution>`, pagination/limit controls | `DashboardService`, `ExecutionRepository` | Live filter updates; double-click opens `ExecutionDetailView`. |
| **ExecutionDetailView** | `src/main/java/com/devopshub/ui/view/ExecutionDetailView.java` | Interactive stage cards, live progress bar, event timeline, diagnosis panel, artifact card, log console | `ExecutionEngine`, `ExecutionRepository`, `LogRepository`, `ExecutionEventBus` | **Full Real-Time Reactive View:** Uses `Timeline` for live duration counter, subscribes to `ExecutionEventBus`, streams logs via `Platform.runLater()`, disposes cleanly on exit. |
| **LogViewerView** | `src/main/java/com/devopshub/ui/view/LogViewerView.java` | Execution selector combo, stage filter combo, log stream filter (STDOUT/STDERR), search input, monospaced `TextArea` | `ExecutionRepository`, `LogRepository` | Asynchronous log query; regex-based text search highlighting. |
| **AnalyticsView** | `src/main/java/com/devopshub/ui/view/AnalyticsView.java` | Reliability metrics, success rate gauge, stage breakdown cards, failure category distribution table | `DashboardService` | Computes statistical metrics across configurable time windows (All Time, 7 Days, 30 Days). |
| **SettingsView** | `src/main/java/com/devopshub/ui/view/SettingsView.java` | Database connectivity tester, latency ping button, backup trigger, workspace path editor, concurrency sliders | `AppConfig`, `DatabaseManager`, `ExecutionBackendFactory` | Probes MySQL ping latency and Docker CLI availability without blocking the UI thread. |
| **AlertsSidebarView** | `src/main/java/com/devopshub/ui/view/AlertsSidebarView.java` | Slide-out right drawer, failed execution alert cards, quick retry buttons, dismissal handlers | `DashboardService`, `ExecutionEngine` | Auto-collapses when window width drops below 1100px; toggled via header bell button. |

### 10.3 JavaFX Threading & UI Safety

- **Background Offloading:** All pipeline executions, child process spawning, Docker executions, Git clones, and network webhooks run exclusively on worker threads allocated from `ExecutionThreadPool` or dedicated daemon threads.
- **UI Thread Marshalling:** Any updates touching JavaFX scene graph nodes (`Label.setText`, `ProgressBar.setProgress`, `TextArea.appendText`) are marshalled onto the JavaFX Application Thread using `javafx.application.Platform.runLater(Runnable)`.
- **Memory & Leak Defense:** In `ExecutionDetailView`, `dispose()` unhooks event listeners from `ExecutionEngine`, detaches `LogCollector` subscribers, stops the `javafx.animation.Timeline` timer, and clears references to prevent controller memory leaks upon view switching.

---

## 11. Object-Oriented Programming (OOP) & Language Concepts

DevOpsHub rigorously adheres to modern object-oriented principles in Java:

### 11.1 Encapsulation

- All entity and service fields are `private` or `private final` with controlled getters, setters, and domain validation.
- `AppConfig.java:21–27`: Configuration map is completely private. Access is mediated through typed accessor methods (`getInt`, `getDbHost`, `getWorkspaceDir`).
- `BoundedLogBuffer.java:15–17`: The internal `ArrayDeque<ExecutionLog>` is private. Appending and reading are guarded by `synchronized` block monitors to maintain thread safety.

### 11.2 Abstraction

- `ExecutionBackend.java:33–56`: Defines the abstraction for command execution (`execute`, `cancel`, `isAvailable`, `getBackendType`), hiding whether the command runs as a host subprocess or inside a Docker container.
- `SecurityScanner.java:11–13`: Abstract contract (`scan(workspace, executionId, stageId)`) hiding specific static analysis rules behind a uniform interface.
- `DeploymentProvider.java:16–30`: Abstract contract hiding filesystem copy mechanisms vs remote SSH batch commands.

### 11.3 Inheritance

- **Framework Inheritance:** `MainApp extends javafx.application.Application`, `MainLayoutView extends javafx.scene.layout.BorderPane`, `ExecutionDetailView extends javafx.scene.layout.VBox`.
- **Domain Inheritance:** `DevOpsHubException` extends `RuntimeException`, with specialized child exceptions: `DatabaseException`, `ExecutionTimeoutException`, `PipelineValidationException`, `StageExecutionException`.

### 11.4 Polymorphism

- `ExecutionBackendFactory.java:43–72`: Resolves either `HostExecutionBackend`, `DockerExecutionBackend`, or `UnavailableExecutionBackend` at runtime based on `ExecutionPolicy`. Callers invoke `backend.execute(...)` without branching on backend types.
- `CompositeSecurityScanner.java:32–41`: Loops through a heterogeneous collection of `SecurityScanner` implementations (`SecretScanner`, `DependencyAuditScanner`) invoking `scanner.scan(...)` polymorphically.
- `FailureRuleEngine.java:55–60`: Iterates through a prioritized list of `FailureRule` implementations invoking `rule.matches(...)` and `rule.diagnose(...)` via dynamic dispatch.

### 11.5 Composition and Aggregation

- `ExecutionEngine.java:31–41`: Composed of `ExecutionRepository`, `LogRepository`, `DiagnosisRepository`, `FailureRuleEngine`, `ExecutionThreadPool`, `ProjectRepository`, `GitService`, `DeploymentService`, and `ExecutionEventBus`.
- `MainLayoutView.java:34–45`: Owns instances of `ProjectService`, `PipelineService`, `DashboardService`, and `ExecutionEngine`, injecting them into child views during view construction.

### 11.6 SOLID Principles Forensic Assessment

| Principle | Compliance Level | Concrete Code Evidence | Identified Violations or Structural Risks |
|---|---|---|---|
| **Single Responsibility (SRP)** | **High** | `PipelineValidator` only validates DAGs and detects cycles. `SecretMasker` only redacts credentials. `ProcessExecutor` only executes processes. `DatabaseManager` only manages connection pools and migrations. | In `ExecutionEngine.java`, while workflow execution is well-orchestrated, it handles artifact discovery, git initialization, and stage scheduling in a single ~900 line file. |
| **Open/Closed (OCP)** | **High** | Adding a new failure diagnosis rule requires implementing `FailureRule` and registering it in `FailureRuleEngine` without modifying existing rules. Adding a new security scanner requires implementing `SecurityScanner`. | Adding a new execution backend requires updating `BackendType` enum and `ExecutionBackendFactory` resolution logic. |
| **Liskov Substitution (LSP)** | **High** | All `FailureRule` implementations correctly fulfill `matches()` and `diagnose()`. `DockerExecutionBackend` and `HostExecutionBackend` conform to `ExecutionBackend` contracts without throwing unexpected checked exceptions. | None detected. |
| **Interface Segregation (ISP)** | **High** | Interfaces are small and focused: `ExecutionBackend` (4 methods), `SecurityScanner` (1 method), `DeploymentProvider` (4 methods), `FailureRule` (4 methods). | None detected. |
| **Dependency Inversion (DIP)** | **Moderate** | Services and engines depend on abstract interfaces (`ExecutionBackend`, `SecurityScanner`, `DeploymentProvider`). All repositories and services provide overloaded constructors accepting dependencies to allow mock injection during unit tests. | Default no-arg constructors instantiate concrete repository implementations (`new ProjectRepository()`), creating tight coupling when using default constructors instead of full DI. |

### 11.7 Additional Language Concepts

1. **Java Records:** Used for immutable rule definitions in `SecretScanner.java:34–40`:
   ```java
   private record Rule(String id, String title, Pattern pattern, SecuritySeverity severity, String remediation) {}
   ```
2. **Generics:** Pervasive across JavaFX UI collections (`TableView<Project>`, `ComboBox<String>`), repositories (`Optional<T>`), and event publishers (`Consumer<T>`).
3. **Enums with Behavior:** `ExecutionStatus`, `StageExecutionStatus`, `StageType`, `TriggerType`, `LogLevel`, `LogStream`, `SecuritySeverity`, `SecurityCategory`, `BackendType`. Many contain custom display names and string deserializers (`fromString`).
4. **Lambdas and Streams:** Used for collection transformations, log filtering, UI listener attachments, and parallel stream processing (e.g., `SecretScanner.java:78–94` using `Files.walk`).
5. **Concurrency Primitives:** `ConcurrentHashMap`, `CopyOnWriteArrayList`, `AtomicBoolean`, `AtomicInteger`, `AtomicLong`, `ThreadPoolExecutor`, `ScheduledExecutorService`, `Future<?>`.

---

## 12. Design Patterns & Architectural Patterns

| Pattern Name | Genuine Implementation? | Concrete Class References | Actual Architectural Role | Caveats / Disadvantages |
|---|---|---|---|---|
| **Singleton** | **Yes** | `AppConfig.java:19`, `DatabaseManager.java:24`, `ExecutionEngine.java:48`, `ExecutionEventBus.java:20`, `ExecutionThreadPool.java:15`, `WebhookServerService.java:34`, `PipelineSchedulerService.java:22` | Centralizes runtime state, thread pools, HTTP listener, and connection pools across desktop views. | Impedes parallel test execution if not resettable; `DatabaseManager` provides `resetInstanceForTesting()` to mitigate this. |
| **Repository** | **Yes** | `ProjectRepository`, `PipelineRepository`, `ExecutionRepository`, `LogRepository`, `DiagnosisRepository`, `DeploymentRepository`, `ScheduleRepository`, `SecurityFindingRepository`, `ExecutionEventRepository` | Mediates between domain objects and raw JDBC tables. Encapsulates all SQL statements and connection borrowing. | Repositories must be manually constructed or passed; no IoC container manages their lifecycle. |
| **Service Layer** | **Yes** | `ProjectService`, `PipelineService`, `DashboardService`, `ProjectImportService`, `DeploymentService` | Coordinates transactions, enforces business validation, and aggregates repository queries. | Some views bypass services and query repositories directly for read operations. |
| **Factory Method** | **Yes** | `ExecutionBackendFactory.java:43–72` | Resolves the appropriate `ExecutionBackend` based on `ExecutionTrustLevel` and container runtime availability. | Static resolution prevents dynamic plugin registration of external third-party backends. |
| **Strategy** | **Yes** | `ExecutionBackend` (`HostExecutionBackend`, `DockerExecutionBackend`), `DeploymentProvider` (`LocalStagingDeploymentProvider`, `SshRemoteDeploymentProvider`) | Encapsulates interchangeable algorithms for process isolation and artifact deployment. | Strategy selection is based on project metadata rather than user-specified execution flags. |
| **Observer / Pub-Sub** | **Yes** | `ExecutionEventBus.java:18–151`, `LogCollector.java:28–76` | Decouples asynchronous engine lifecycle events and log lines from JavaFX UI views via `Consumer<T>` subscriptions. | Requires explicit `dispose()` calls in JavaFX views to prevent memory leaks from retained lambdas. |
| **Composite** | **Yes** | `CompositeSecurityScanner.java:11–50` | Treats a collection of security scanners (`SecretScanner`, `DependencyAuditScanner`) as a single `SecurityScanner`. | Scanner failures must be aggregated carefully so one failed scan does not mask other findings. |
| **Chain / Priority Rules** | **Yes** | `FailureRuleEngine.java:24–60` | Evaluates execution logs and exit codes sequentially against 12 prioritized `FailureRule` implementations. | First matching rule terminates evaluation; cannot produce multi-cause compound diagnoses. |
| **Worker / Coordinator** | **Yes** | `ParallelStageCoordinator.java:15–72` | Implements Fork/Join execution for parallel stage groups using `ExecutorService` and `Future<StageExecution>`. | Requires careful cancellation handling to ensure worker threads are interrupted if one branch fails. |
| **Adapter** | **Yes** | `ProcessExecutor.java:17–60`, `GitService.java:450–532` | Adapts external operating system command-line utilities (Git, Docker, shell) into Java objects and return models. | Relies on OS-specific shell wrappers (`/bin/sh -c` on Unix, `cmd.exe /c` on Windows). |

---

## 13. Pipeline Execution Engine Deep Dive

### 13.1 Step-by-Step Execution Lifecycle

1. **Trigger & Identification:** Initiated via UI click (`TriggerType.MANUAL`), incoming HTTP webhook (`TriggerType.WEBHOOK`), or time-based scheduler (`TriggerType.SCHEDULED`). An execution ID is generated via `UUID.randomUUID()`, and an atomic execution number is assigned from `SELECT COALESCE(MAX(execution_number), 0) + 1 FROM executions WHERE pipeline_id = ?`.
2. **Initial Persistence:** The `Execution` record is saved immediately with status `RUNNING` and phase `Queued`. All child stages from `pipeline.getStages()` are persisted as `StageExecution` records with status `QUEUED`.
3. **Event Publishing:** An `EXECUTION_QUEUED` event is published onto `ExecutionEventBus`.
4. **Asynchronous Dispatch:** The execution is wrapped in a `Callable` and submitted to `ExecutionThreadPool.getPipelineExecutor()`, returning a `Future<?>` tracked in `activeExecutions` map for user cancellation.
5. **Trust Classification & Policy Evaluation:**
   - `ExecutionIsolationService.resolveTrustLevel(project, triggerType)`: Local filesystem repositories resolve to `TRUSTED_LOCAL`. Webhook triggers or remote repositories resolve to `UNTRUSTED_EXTERNAL` (unless `ALLOW_REMOTE_REPOS_AS_TRUSTED=true` is set).
   - If `UNTRUSTED_EXTERNAL` and Docker is not running: **Fail-Closed.** The execution is immediately set to `BLOCKED`, stages marked `SKIPPED`, a security rejection diagnosis (`RULE_SEC_ISOLATION_01`) generated, and the run terminated without executing any commands.
6. **Workspace Resolution:** Workspaces are resolved to `./workspaces/projects/<projectId>`. `GitService.validateWorkspaceConfinement()` asserts that the destination resides strictly inside the application workspace root, rejecting directory traversal attempts.
7. **Git Synchronization:**
   - If `.git` exists: Runs `git remote`, `git fetch --all --prune`, `git checkout <branch>`, `git pull`.
   - If directory is fresh: Runs `git clone --branch <branch> --single-branch <url> <dest>`.
   - Resolves commit hash via `git rev-parse HEAD` and persists to `executions.commit_hash`.
8. **DAG Stage Traversal & Scheduling:**
   - Evaluates prerequisites (`Stage.getDependsOnStageIds()`): If any prerequisite stage failed or was skipped, the current stage is marked `SKIPPED`.
   - Sequential stages: Dispatched directly via `StageRunner.call()`.
   - Parallel stage groups (`Stage.getParallelGroup()`): Collated and dispatched concurrently via `ParallelStageCoordinator.executeParallel(runners)` onto `threadPool.getStageExecutor()`.
9. **Process & Container Execution:**
   - `HostExecutionBackend`: Uses `ProcessBuilder` with environment sanitization (whitelisted variables, dummy home directory for untrusted code).
   - `DockerExecutionBackend`: Uses `docker run --rm --security-opt no-new-privileges --cap-drop ALL --tmpfs /tmp --memory 2048m --cpus 4.0 -v <workspace>:/workspace:rw node:20-alpine` (or `alpine:latest`).
   - Standard output and error streams are captured asynchronously via dedicated stream-reading threads and ingested by `LogCollector`.
10. **Specialized Stage Verification:**
    - `StageType.CHECKOUT`: Synchronizes Git ref.
    - `StageType.BUILD` / `PACKAGE`: If `package.json` contains Next.js, verifies that `.next` directory was created upon zero exit code. Fails stage if `.next` is absent.
    - `StageType.SECURITY`: Runs `CompositeSecurityScanner` (`SecretScanner` + `DependencyAuditScanner`). Persists findings to `security_findings` table. If any finding is `CRITICAL`, fails the stage with exit code 1.
    - `StageType.DEPLOY`: Locates build artifact and deploys via `DeploymentService` (`LocalStaging` or `SshRemote`).
11. **Failure Diagnosis:** If the run fails, `FailureRuleEngine.analyze(failedStage, logs)` is invoked. Evaluates 12 rules, constructs a `FailureDiagnosis` record, and persists it to `failure_diagnoses` table.
12. **Finalization:** Computes total elapsed duration, sets status (`SUCCESS`, `FAILED`, `CANCELLED`), updates database, flushes remaining log buffers, and emits `EXECUTION_COMPLETED` event.

### 13.2 Stage Implementation Truth Matrix

| Stage Type | Real Implementation Status | Actual Runtime Mechanism | Limitations / Verification Notes |
|---|---|---|---|
| **CHECKOUT** | **Real** | Invokes host `git` CLI (`clone`, `fetch`, `checkout`, `pull`). | Verified against `https://github.com/Kaviselvaram/lair.git`. |
| **BUILD** | **Real** | Host subprocess or Docker container running user-specified commands (`mvn compile`, `npm install`, `npm run build`). | Real process execution. Verified Next.js build detection. |
| **TEST** | **Real** | Subprocess running test runner (`mvn test`, `npm test`, `pytest`). | Captures stdout/stderr; detects assertion failures via rule engine. |
| **SECURITY** | **Real** | Invokes `CompositeSecurityScanner` on workspace files. | Real regex secret scanner and JSON/XML dependency auditor. |
| **PACKAGE** | **Real** | Subprocess running package command (`mvn package`, `npm run build`); discovers target artifact. | Inspects `.next`, `target/*.jar`, `dist/`. Calculates SHA-256. |
| **DEPLOY** | **Real** | Copies artifact to staging directory (`LocalStaging`) or executes `scp` (`SshRemote`). | Real file copy and SHA-256 verification. Not a mock. |
| **CUSTOM** | **Real** | Executes arbitrary shell command via configured backend. | Subject to trust level and security boundary policies. |

---

## 14. Git, Processes, and Filesystem Concepts

### 14.1 ProcessBuilder & Child Process Management

All subprocess execution in DevOpsHub is managed through `java.lang.ProcessBuilder`:

- **Operating System Branching:** `HostExecutionBackend.java:87–93` detects host OS via `System.getProperty("os.name")`:
  - Windows: `new ProcessBuilder("cmd.exe", "/c", command)`
  - macOS / Linux: `new ProcessBuilder("/bin/sh", "-c", command)`
- **Working Directory:** Set explicitly via `pb.directory(workingDir)`.
- **Environment Sanitization:** `pb.environment().clear()` is invoked, followed by injecting only whitelisted system environment variables (`PATH`, `JAVA_HOME`, `NODE_PATH`, `LANG`, etc.). All sensitive tokens (`AWS_*`, `TOKEN`, `PASSWORD`, `SECRET`, `KEY`) are stripped (`ExecutionPolicy.java:70–138`).
- **Stream Redirection:** `pb.redirectErrorStream(false)` is enforced to keep stdout and stderr separate, routing them into independent `LogStream.STDOUT` and `LogStream.STDERR` database log streams.
- **Asynchronous Output Pumping:** Two background worker threads read `process.getInputStream()` and `process.getErrorStream()` using `InputStreamReader(..., StandardCharsets.UTF_8)` to prevent process deadlocks when pipe buffers fill up.
- **Process Timeouts:** `process.waitFor(effectiveTimeout, TimeUnit.SECONDS)` guards execution. If timeout expires, an `ExecutionTimeoutException` is thrown, and `terminateProcessTree()` is invoked.
- **Process Tree Destruction:** `terminateProcessTree()` (`HostExecutionBackend.java:177–195`) calls `process.descendants().forEach(ProcessHandle::destroyForcibly)` before calling `process.destroyForcibly()`. This guarantees that orphaned child processes (e.g., node servers or daemonized scripts) are terminated when a stage times out or is cancelled.

### 14.2 Git CLI Integration & Workspace Safety

- **CLI-Only Git:** The application does not use JGit; it uses the native `git` executable installed on the host system (`GitService.java:34–58`).
- **Workspace Confinement (ARCH-02):** `GitService.validateWorkspaceConfinement()` validates that the clone destination resides strictly within `workspaces/`. It explicitly prevents cloning into the DevOpsHub root directory or filesystem root (`/`).
- **Parent Repository Traversal Defense:** `GitService.resolveAuthorizedRepositoryRoot()` prevents Git CLI commands from discovering or modifying DevOpsHub's own enclosing `.git` repository by setting `GIT_CEILING_DIRECTORIES` to the workspace root.
- **Non-Interactive Execution:** Disables terminal login prompts by setting `GIT_TERMINAL_PROMPT=0` and `GIT_ASKPASS=echo`, and immediately closes child process standard input (`process.getOutputStream().close()`).
- **Symlink-Safe Cleanup (SEC-03):** When a failed clone requires workspace cleanup, `GitService.safeCleanupDirectory()` walks the directory tree using `Files.walkFileTree()` and deletes symlinks directly without following them into external host directories.

---

## 15. Webhooks, Networking, and Scheduling

### 15.1 Webhook HTTP Server & Cryptography

```
                    Incoming HTTP POST Request
                                │
                                ▼
               WebhookServerService (Port 8089/8080)
                                │
          ┌─────────────────────┴─────────────────────┐
          ▼                                           ▼
1. Body Size Check (>1MB?)                2. Method Check (POST?)
   ├── Yes -> 413 Payload Too Large          └── No -> 405 Method Not Allowed
   └── No -> Proceed
          │
          ▼
3. Timestamp Check (X-Hub-Timestamp)
   ├── |now - ts| > 300,000ms (5 min) -> 401 Unauthorized (Expired)
   └── Valid -> Proceed
          │
          ▼
4. Signature Check (X-Hub-Signature-256)
   ├── Calculate HMAC-SHA256(body, WEBHOOK_SECRET_KEY)
   ├── MessageDigest.isEqual(expected, actual) [Constant-Time]
   ├── If invalid & rotation key configured: Check WEBHOOK_ROTATION_SECRET_KEY
   └── Both invalid -> 401 Unauthorized
          │
          ▼
5. Replay Deduplication (X-GitHub-Delivery or Sig Hash)
   ├── Key in processedDeliveryCache? -> 409 Conflict (Replay)
   └── Fresh -> Add to cache
          │
          ▼
6. Trigger Pipeline Execution
   └── ExecutionEngine.startExecution(pipeline, TriggerType.WEBHOOK) -> 200 OK
```

- **HTTP Server:** Uses JDK standard `com.sun.net.httpserver.HttpServer` bound to `http://localhost:8089/api/webhook/trigger` (or configured `WEBHOOK_PORT`). Handled on a bounded pool of 4 executor threads.
- **HMAC Verification:** `calculateHmacSha256(byte[] data, String secret)` uses Java Cryptography Architecture `javax.crypto.Mac` with algorithm `HmacSHA256`.
- **Constant-Time Comparison:** Evaluated using `java.security.MessageDigest.isEqual()` to prevent timing attacks.
- **Fail-Closed Stance:** If `WEBHOOK_SECRET_KEY` is not configured in `.env` or system environment, the webhook handler rejects all incoming requests with HTTP `503 Service Unavailable`.

### 15.2 Automated Pipeline Scheduler

- **Scheduling Mechanism:** `com.devopshub.scheduler.PipelineSchedulerService` uses `java.util.concurrent.ScheduledExecutorService` (`Executors.newScheduledThreadPool(2)`).
- **Durable Persistence:** Scheduled jobs are persisted to MySQL/H2 table `pipeline_schedules` via `ScheduleRepository`. Schedules survive application restarts and are reloaded during `MainApp` startup (`loadPersistedSchedules()`).
- **Fixed-Rate Scheduling:** Jobs are scheduled via `scheduler.scheduleAtFixedRate()`. Upon firing, the scheduler invokes `executionEngine.startExecution(pipeline, TriggerType.SCHEDULED)`, updates `last_run_at`, increments `execution_count`, calculates `next_run_at`, and persists the updated schedule state to the database.

---

## 16. Security, Isolation, and Reliability Concepts

### 16.1 Security Controls Matrix

| Control Category | Security Threat Addressed | Actual Implementation & Enforcement Point | Status |
|---|---|---|---|
| **Execution Sandboxing** | Arbitrary host desktop compromise from malicious untrusted repos | `DockerExecutionBackend.java:151–176`: Drops capabilities (`--cap-drop ALL`), enables `no-new-privileges`, mounts only `/workspace`, enforces memory and CPU limits. | **Fully Implemented** |
| **Fail-Closed Isolation** | Accidental host execution of external untrusted code when Docker is absent | `ExecutionPolicy.java:46–64`, `ExecutionEngine.java:252–297`: If trust level is `UNTRUSTED_EXTERNAL` and Docker is unavailable, blocks run immediately with HTTP 403 / status `BLOCKED`. | **Fully Enforced** |
| **Secret Masking** | Accidental leakage of passwords, tokens, private keys into logs and UI | `SecretMasker.java:11–57`: Redacts private keys (`[REDACTED PRIVATE KEY]`), URL basic auth credentials (`https://user:***@host`), and token assignments (`password=***`). | **Fully Enforced** |
| **Static Secret Scanning** | Committing private keys, AWS tokens, GitHub PATs into project repository | `SecretScanner.java:42–67`: Static regex rules detect AWS keys, unencrypted private keys, GitHub PATs, Slack webhooks, and hardcoded credentials. | **Fully Implemented** |
| **Dependency Auditing** | Supply-chain attacks via unpinned versions or plaintext HTTP repositories | `DependencyAuditScanner.java:45–140`: Scans `package.json` and `pom.xml` for unpinned wildcard versions (`*`, `latest`) and unencrypted `http://` repository URLs. | **Fully Implemented** |
| **Webhook HMAC & Replay** | Forged webhook triggers, man-in-the-middle tampering, replay attacks | `WebhookServerService.java:138–157, 219–307`: Constant-time HMAC-SHA256 signature verification, 5-minute timestamp replay window, delivery ID deduplication cache. | **Fully Enforced** |
| **Path Traversal Defense** | Escaping build workspace to overwrite host application files (Zip-Slip) | `GitService.validateWorkspaceConfinement()`, `ProjectImportService.extractZipSafely()`: Resolves canonical paths and asserts they start with the authorized workspace root. | **Fully Enforced** |
| **Resource Quotas** | Fork-bombs, memory exhaustion, hung build processes | `HostExecutionBackend.java:128–135`: 300s default timeout with process-tree SIGKILL. `DockerExecutionBackend.java:158–170`: `--memory 2048m --cpus 4.0 --pids-limit 2048`. | **Fully Enforced** |

---

## 17. Testing, Build, and Quality Practices

### 17.1 Test Suite Inventory & Results

The test suite consists of **32 test classes** in `src/test/java`. A complete run via `mvn test` executed on 2026-10-26 confirmed:
- **Tests run:** 135
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Total Execution Time:** 57.28 seconds

| Test Suite Class | Tests Run | What It Actually Verifies | What It Does NOT Verify |
|---|---|---|---|
| `PipelineExecutionRepairTest` | 10 | Real end-to-end execution of `https://github.com/Kaviselvaram/lair.git`, stage status progression, Next.js build verification, commit resolution, log persistence. | Does not verify production remote deployment to an external cloud server. |
| `DatabaseIntegrationTest` | 8 | Project and pipeline CRUD, foreign key cascade deletion, stage dependency retrieval against embedded database. | Does not verify multi-node database replication. |
| `IndependentPersistenceAuditTest` | 6 | Full persistence verification of project metadata, pipeline topological DAGs, execution history, and diagnosis across simulated restarts. | Does not verify network partition failover. |
| `DatabaseManagerPersistenceAuditTest` | 5 | Persistence durability across `DatabaseManager.resetInstanceForTesting()` under disk-backed mode vs ephemeral in-memory mode. | Does not test live MySQL container restart. |
| `SecurityIsolationAuditTest` | 8 | Fail-closed behavior for untrusted repositories, Docker availability check, environment sanitization, working directory confinement. | Does not execute actual malicious kernel escape exploits. |
| `GitServiceTest` | 6 | Real `git clone`, branch checkout, commit hash resolution, clone failure handling, credential masking in Git output. | Does not test SSH agent forwarding. |
| `GitWorkspaceSecurityAuditTest` | 6 | Symlink-safe directory cleanup, parent repository traversal defense (`GIT_CEILING_DIRECTORIES`), workspace boundary confinement. | Does not test Windows junction points. |
| `WebhookHmacVerificationTest` | 8 | Cryptographic HMAC-SHA256 calculation, constant-time validation, timestamp window expiry, delivery ID deduplication. | Does not test reverse proxy header spoofing. |
| `WebhookServerServiceTest` | 6 | Live embedded HTTP server POST requests, signature authorization, pipeline trigger integration, payload size limits. | Does not test distributed rate limiting. |
| `SecurityScannerTest` | 7 | Detection of AWS keys, private keys, GitHub PATs, unpinned npm dependencies, and insecure HTTP URLs. | Does not perform full CVE database lookup. |
| `DeploymentServiceTest` | 5 | Local staging deployment, file copy verification, SHA-256 integrity calculation, automated rollback to `.rollback-backup`. | Does not test remote SSH deployment against a live remote host. |
| `PipelineSchedulerServiceTest` | 4 | Fixed-rate scheduling, database schedule persistence, automated pipeline execution trigger, unscheduling. | Does not test year-long leap-second scheduling. |
| `FailureRuleEngineTest` | 14 | Matching and diagnosis generation for all 12 heuristic failure rules and truthful unclassified fallback. | Does not test machine-learning failure prediction. |
| `ProjectImportServiceTest` | 6 | Build system detection (Maven, Gradle, npm, Next.js, Python), Zip-Slip path traversal defense during archive extraction. | Does not test corrupted ZIP headers. |
| `SecretMaskerTest` | 6 | Regex masking of private keys, basic auth URLs, and token key-value pairs in console logs. | Does not mask unstructured natural-language passwords. |

---

## 18. Configuration & Dependency Map

| Configuration Key | Default Value | Source Precedence | Actual Usage in Code | Sensitive? |
|---|---|---|---|---|
| `DB_HOST` | `localhost` | Defaults -> `.env` -> System Env | `AppConfig.java:34, 105`: MySQL host connection endpoint | No |
| `DB_PORT` | `3306` | Defaults -> `.env` -> System Env | `AppConfig.java:35, 106`: MySQL TCP port | No |
| `DB_NAME` | `devopshub` | Defaults -> `.env` -> System Env | `AppConfig.java:36, 107`: Primary database / schema name | No |
| `DB_USER` | `root` | Defaults -> `.env` -> System Env | `AppConfig.java:37, 108`: Database authentication username | Redacted in reports |
| `DB_PASSWORD` | `root` | Defaults -> `.env` -> System Env | `AppConfig.java:38, 109`: Database authentication password | **YES (Redacted)** |
| `DB_POOL_SIZE` | `10` | Defaults -> `.env` -> System Env | `AppConfig.java:39, 110`: Maximum HikariCP pool connections | No |
| `DATABASE_STORAGE_PATH` | `./data/devopshub` | Defaults -> `.env` -> System Env | `DatabaseManager.java:68`: Resolved path for fallback H2 database file | No |
| `H2_IN_MEMORY_ONLY` | `false` | Defaults -> `.env` -> System Env | `DatabaseManager.java:61`: If true, disables disk persistence for tests | No |
| `WEBHOOK_PORT` | `8089` | Defaults -> `.env` -> System Env | `WebhookServerService.java:55`: HTTP port for webhook listener | No |
| `WEBHOOK_SECRET_KEY` | *(unconfigured)* | Defaults -> `.env` -> System Env | `WebhookServerService.java:56`: Primary secret key for HMAC-SHA256 | **YES (Redacted)** |
| `WEBHOOK_ROTATION_SECRET_KEY` | *(unconfigured)* | Defaults -> `.env` -> System Env | `WebhookServerService.java:58`: Secondary secret key during rotation | **YES (Redacted)** |
| `EXECUTION_MAX_CONCURRENT_PIPELINES` | `4` | Defaults -> `.env` -> System Env | `ExecutionThreadPool.java:22`: Core/max threads in pipeline executor | No |
| `EXECUTION_DEFAULT_TIMEOUT_SECONDS` | `300` | Defaults -> `.env` -> System Env | `AppConfig.java:44, 122`: Default stage execution timeout | No |
| `EXECUTION_WORKSPACE_DIR` | `./workspaces` | Defaults -> `.env` -> System Env | `GitService.java:50`, `ExecutionEngine.java:90`: Parent build workspace folder | No |
| `ALLOW_HOST_EXECUTION_FOR_TRUSTED` | `true` | Defaults -> `.env` -> System Env | `ExecutionPolicy.java:25`: Permits trusted local repos to run on host | No |
| `ALLOW_REMOTE_REPOS_AS_TRUSTED` | `false` | Defaults -> `.env` -> System Env | `ExecutionIsolationService.java:56`: Security toggle (must default to false) | No |
| `EXECUTION_CONTAINER_ENABLED` | `true` | Defaults -> `.env` -> System Env | `ExecutionBackendFactory.java:32`: Enables Docker isolation backend | No |
| `EXECUTION_CONTAINER_IMAGE` | *(auto-detected)* | Defaults -> `.env` -> System Env | `DockerExecutionBackend.java:296`: Overrides container image (`node:20-alpine`) | No |
| `GIT_EXECUTABLE` | `git` | Defaults -> `.env` -> System Env | `GitService.java:39`: Path to host Git executable | No |
| `GIT_TIMEOUT_SECONDS` | `300` | Defaults -> `.env` -> System Env | `GitService.java:41`: Timeout for Git clone/fetch commands | No |

---

## 19. Complete Concept-to-Code Matrix

| Concept / Technology | Implemented? | Exact File Path and Line Range | Actual Architectural Role | Concrete Code Example / Usage | Current Limitations |
|---|---|---|---|---|---|
| **JavaFX Application** | **Yes** | `src/main/java/com/devopshub/app/MainApp.java:15–58` | Application entry point and window lifecycle. | `public class MainApp extends Application { public void start(Stage primaryStage) { ... } }` | Desktop only; requires graphical display server. |
| **JavaFX Programmatic UI** | **Yes** | `src/main/java/com/devopshub/ui/view/MainLayoutView.java:32–200` | Constructs layout containers, sidebar, and view switching. | `BorderPane`, `StackPane`, `VBox`, `HBox`, `ScrollPane`, `Button`, `Label` | FXML is declared in `pom.xml` but entirely unused. |
| **JavaFX Thread Safety** | **Yes** | `src/main/java/com/devopshub/ui/view/ExecutionDetailView.java:922–951` | Marshals async events and logs to the UI thread. | `Platform.runLater(() -> terminalArea.appendText(log.getMessage() + "\n"))` | None. Prevents `IllegalStateException: Not on FX application thread`. |
| **Raw JDBC** | **Yes** | `src/main/java/com/devopshub/repository/ProjectRepository.java:26–43` | Direct relational query execution without ORM. | `try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) { ... }` | Hand-written SQL requires manual schema synchronization. |
| **Connection Pooling** | **Yes** | `src/main/java/com/devopshub/config/DatabaseManager.java:31–84` | Manages reusable database connections. | `HikariConfig hikariConfig = new HikariConfig(); ds = new HikariDataSource(hikariConfig);` | Max pool size fixed to 10 for MySQL, 5 for H2. |
| **Prepared Statements** | **Yes** | `src/main/java/com/devopshub/repository/ExecutionRepository.java:174–196` | Parameterized SQL execution preventing SQL injection. | `ps.setString(1, execution.getId()); ps.setInt(4, execution.getExecutionNumber());` | None. Used universally across all repositories. |
| **JDBC Transactions** | **Yes** | `src/main/java/com/devopshub/repository/PipelineRepository.java:110–140` | Atomic multi-table updates. | `conn.setAutoCommit(false); try { ... conn.commit(); } catch(SQLException e) { conn.rollback(); }` | Used selectively for pipeline + stages and log batches. |
| **Batch JDBC Execution** | **Yes** | `src/main/java/com/devopshub/repository/LogRepository.java:50–72` | High-throughput log line persistence. | `for (ExecutionLog log : logs) { ps.addBatch(); } ps.executeBatch(); conn.commit();` | Batch size capped to threshold of 15 lines or immediate flush. |
| **Schema Migrations** | **Yes** | `src/main/java/com/devopshub/config/DatabaseManager.java:172–209` | Startup SQL migration executor with version tracking. | `if (!isMigrationApplied("V1")) { executeSqlScript("/db/migrations/V1__initial_schema.sql"); recordMigration("V1"); }` | Custom script splitter (`split(";")`); does not support complex stored procedures. |
| **Process Execution** | **Yes** | `src/main/java/com/devopshub/execution/backend/HostExecutionBackend.java:86–170` | Spawns child processes on host OS with timeout. | `ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command); Process p = pb.start();` | Process tree killing uses Java 9+ `ProcessHandle.descendants()`. |
| **Container Sandboxing** | **Yes** | `src/main/java/com/devopshub/execution/backend/DockerExecutionBackend.java:139–200` | Executes untrusted code inside isolated Docker containers. | `docker run --rm --security-opt no-new-privileges --cap-drop ALL --memory 2048m -v <workspace>:/workspace:rw node:20-alpine` | Requires local Docker daemon to be running. |
| **Git CLI Integration** | **Yes** | `src/main/java/com/devopshub/git/GitService.java:198–290` | Clones and updates Git repositories. | `executeGit(parentDir, "clone", "--branch", branch, "--single-branch", url, dest)` | Requires `git` binary installed on host PATH. |
| **Topological DAG Validation** | **Yes** | `src/main/java/com/devopshub/pipeline/PipelineValidator.java:58–105` | Detects cycles and invalid dependencies in stages. | DFS cycle detection algorithm using `visited` and `recStack` sets. | Validates stage ordering within a single pipeline. |
| **Rule-Based Diagnosis** | **Yes** | `src/main/java/com/devopshub/analysis/FailureRuleEngine.java:48–64` | Evaluates failure causes from logs and exit codes. | Loops 12 rules (`CompilationErrorRule`, `GitCheckoutFailureRule`, etc.) returning `FailureDiagnosis`. | Single-diagnosis return (stops on first match). |
| **Static Secret Scanning** | **Yes** | `src/main/java/com/devopshub/security/SecretScanner.java:42–100` | Static analysis scanning files for leaked credentials. | Regex pattern matching for AWS keys, private keys, PATs, webhooks. | Scans up to 1000 files; skips files >2MB and binaries. |
| **HTTP Webhook Server** | **Yes** | `src/main/java/com/devopshub/webhook/WebhookServerService.java:79–94` | Embedded HTTP listener for incoming triggers. | `HttpServer.create(new InetSocketAddress(port), 0); server.createContext("/api/webhook/trigger", ...)` | Bounded to 4 threads; standard HTTP (HTTPS requires upstream reverse proxy). |
| **HMAC-SHA256 Security** | **Yes** | `src/main/java/com/devopshub/webhook/WebhookServerService.java:114–157` | Cryptographic signature verification and replay defense. | `Mac.getInstance("HmacSHA256")` and `MessageDigest.isEqual(...)`. | Replay window set to 300,000ms (5 minutes). |
| **Pipeline Scheduling** | **Yes** | `src/main/java/com/devopshub/scheduler/PipelineSchedulerService.java:24–116` | Automated recurring execution scheduler. | `scheduler.scheduleAtFixedRate(...)` backed by `pipeline_schedules` DB table. | Simple interval-based scheduling (seconds/minutes); cron expressions not yet implemented. |
| **Artifact Verification** | **Yes** | `src/main/java/com/devopshub/execution/ExecutionEngine.java:822–863` | Discovers built deliverables and computes checksums. | Discovers `.next`, `target/*.jar`, `dist/`; computes SHA-256 via `DeploymentService.computeSha256()`. | Directory bundles calculate size recursively; SHA-256 computed on `BUILD_ID` if present. |
| **Artifact Deployment** | **Yes** | `src/main/java/com/devopshub/deployment/LocalStagingDeploymentProvider.java:30–87` | Deploys deliverables with backup and rollback support. | `Files.copy(source, dest, REPLACE_EXISTING); DeploymentRepository.save(record);` | Remote SSH provider implemented via CLI `scp -B`; requires configured SSH key or agent. |

---

## 20. Incomplete, Disconnected, or Absent Implementations

To maintain absolute technical truth, the following features in the repository are identified as partial, simulated, or absent:

1. **FXML Markup Support (Absent):** While `javafx-fxml` is included in `pom.xml`, there are **zero `.fxml` files** in `src/main/resources`. The entire UI is built imperatively in Java.
2. **Cron Expression Parser in Scheduler (Partial):** `PipelineSchedulerService` uses fixed interval scheduling in seconds (`intervalSeconds`). Advanced 5-field cron syntax (e.g. `0 0 * * *`) is not yet parsed; intervals are computed as fixed second offsets.
3. **Database Dual-Write / Fallback Synchronization (Absent):** If the application switches from MySQL to H2 fallback, data written to H2 is not automatically synced back to MySQL when MySQL returns. Datasets remain separate.
4. **Remote SSH Deployment Testing (Partial):** `SshRemoteDeploymentProvider` contains real `scp -B` execution code, but automated unit tests mock or skip remote execution due to lack of a live SSH target in CI.
5. **Static Analysis of Remote Repositories without Clone (Absent):** The `SecurityScanner` requires a local directory. It cannot inspect remote GitHub repositories without first cloning them to the workspace.

---

## 21. Known Defects, Operational Risks, and Architectural Concerns

1. **MySQL Docker Host Storage Visibility:** Because the MySQL container was created without a host directory bind mount (`-v ./data/mysql:/var/lib/mysql`), its files reside in Docker Desktop's hidden virtual disk. If the user deletes or resets Docker Desktop, the database is wiped unless a backup is taken.
2. **ExecutionEngine Complexity:** `ExecutionEngine.java` is 948 lines long and coordinates workspace initialization, git operations, isolation checks, stage scheduling, parallel joins, artifact inspection, and failure analysis. Refactoring into dedicated lifecycle coordinators (`WorkspacePreparer`, `StageDispatcher`, `ArtifactVerifier`) would improve maintainability.
3. **Log Table Growth:** `execution_logs` stores individual lines as separate rows. For high-volume builds generating tens of thousands of lines, inserting 1 row per line (even in batches of 15) increases database I/O. Future iterations should compress or chunk logs into block segments.
4. **Desktop UI Direct Repository Access:** Views such as `ExecutionHistoryView` and `LogViewerView` query repositories directly rather than going through dedicated service façades.

---

## 22. Technical Terms Glossary

- **DAG (Directed Acyclic Graph):** A mathematical graph of pipeline stages connected by directional dependencies without closed loops, ensuring deterministic execution order.
- **HikariCP:** A high-performance JDBC connection pool that manages physical database socket connections, minimizing latency and connection creation overhead.
- **HMAC-SHA256 (Hash-based Message Authentication Code):** A cryptographic message authentication mechanism combining SHA-256 with a shared secret key to verify data integrity and authenticity.
- **Fail-Closed:** A security design principle where a system defaults to rejecting access or aborting execution when security conditions (e.g., container sandbox availability) cannot be verified.
- **Constant-Time Comparison:** A string/byte comparison method (`MessageDigest.isEqual`) that always takes the same execution time regardless of where differences occur, eliminating timing side-channel attacks.
- **ProcessHandle:** A Java 9+ standard API representing a native operating system process, allowing traversal and forcible destruction of entire child process trees (`descendants()`).
- **Zip-Slip:** A critical path traversal vulnerability where malicious ZIP archives contain relative directory entries (`../../etc/passwd`) designed to write outside target extraction folders.
- **JavaFX Application Thread:** The single dedicated UI thread responsible for rendering JavaFX nodes and processing window events. All background updates must be scheduled onto it via `Platform.runLater()`.
