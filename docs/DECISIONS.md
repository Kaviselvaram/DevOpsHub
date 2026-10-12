# DevOpsHub — Architectural & Engineering Decisions Log (ADR)

**Project:** DevOpsHub — Intelligent CI/CD Management Platform  
**Target Platform:** JavaFX Desktop Application  
**Database:** MySQL via JDBC  

---

## ADR-001: JavaFX Native Desktop UI vs. Web Frontend

### Context
Slide 9, 10, and 12 of the early concept presentation mentioned "React + TypeScript" as an edge client communicating with a Spring Boot backend. However, the authoritative project specification, academic mandate (CSE1007 — Java Programming), and prompt explicitly require:
> "The application must use JavaFX for its entire frontend. Do not use React or any web frontend framework... Do not introduce Spring Boot, a REST server, or a microservice architecture merely because the previous design used them."

### Decision
We deliver a **100% native JavaFX desktop application**. No web frontend or Spring Boot microservice is used. All presentation logic, controls, layouts, and styles are native JavaFX (`BorderPane`, `TableView`, `ScrollPane`, JavaFX CSS). All business logic runs directly in the Java application layer, connecting to MySQL via direct JDBC with `HikariCP` connection pooling.

### Consequences
- **Advantages:** Zero node/npm runtime dependencies, zero cross-origin (CORS) overhead, direct access to the JVM process execution environment (`ProcessBuilder`), faster native rendering, strict adherence to CSE1007 syllabus requirements.
- **Trade-offs:** Custom JavaFX responsive layout bindings and CSS styling required to reproduce modern web aesthetic tokens.

---

## ADR-002: Strict SVG-Only Icon System via JavaFX Vector Path Geometry

### Context
Standard JavaFX does not provide out-of-the-box support for all arbitrary SVG documents via `javafx.scene.image.Image`. The project specification enforces a strict prohibition against emojis, icon fonts, Unicode symbols, and raster images (PNG/JPG).

### Decision
We implement a lightweight, native, high-performance `SvgIcon` component using JavaFX `SVGPath` with standardized vector paths (viewBox 0 0 24 24).
- Each SVG icon is represented by precise vector path geometry strings.
- Icons support dynamic sizing, fill, stroke colors, and CSS style classes (`.svg-icon`, `.svg-icon-primary`, `.svg-icon-danger`).
- SVG source files are also preserved in `src/main/resources/icons/svg/` for asset integrity.

### Consequences
- Crisp rendering at any display DPI or scaling factor.
- Zero external font file dependencies or font-loading race conditions.
- Strict compliance with the SVG-only policy.

---

## ADR-003: Explainable Rule-Based Failure Intelligence vs. Complex Black-Box ML

### Context
CI/CD users need immediate, deterministic explanations of why a build or test failed, not probabilistic black-box hallucinations. Furthermore, external AI API services introduce latency, cost, and availability concerns.

### Decision
We implement a transparent, modular rule engine (`com.devopshub.analysis.FailureRuleEngine`).
- Evaluates normalized stdout/stderr streams, exit codes, and stage metadata.
- Pre-packaged with standard rules:
  1. `AssertionFailureRule` (matches test mismatches such as `expected HTTP 401 received HTTP 500` at service classes)
  2. `CompilationErrorRule` (matches `javac` syntax and unresolved symbol errors)
  3. `MissingDependencyRule` (matches missing Maven/library artifacts, `ClassNotFoundException`)
  4. `DatabaseConnectionRule` (matches connection refused, invalid credentials)
  5. `TimeoutRule` (matches exceeded execution time limits)
  6. `PermissionDeniedRule` (matches filesystem permission errors)
- Emits structured `FailureDiagnosis` containing Category, Likely Cause, Evidence Excerpts, Rule ID, and Suggested Action.

### Consequences
- Deterministic, verifiable, explainable, and zero operational cost.
- Extensible: new failure patterns can be registered in seconds via the `FailureRule` interface.

---

## ADR-004: Concurrency Model & UI Thread Safety

### Context
Pipeline stages execute long-running shell processes, file I/O, and database queries. If executed on the JavaFX Application Thread, the UI would freeze and degrade user experience.

### Decision
- UI events trigger asynchronous tasks managed by bounded `ExecutorService` pools (`ExecutionThreadPool`).
- Stage execution orchestrates tasks via `CompletableFuture` and worker threads.
- All UI state modifications (progress bar updates, status badges, log appends) are safely marshaled to the JavaFX Application Thread via `Platform.runLater()`.
- Thread pools are gracefully terminated during application shutdown hooks.

### Consequences
- Silky smooth 60fps UI responsiveness during intense parallel pipeline executions.
- Zero race conditions in shared execution models using thread-safe structures (`ConcurrentHashMap`, atomic primitives).

---

## ADR-005: Dual-Mode Persistence (MySQL Primary with Isolated Test/Fallback Support)

### Context
MySQL is mandated as the primary persistent database. However, unit tests and automated CI environments must be capable of running deterministically even when external database daemons are transiently unavailable.

### Decision
- `DatabaseManager` defaults to MySQL 8.0 on `localhost:3306` with credentials from `.env`.
- Database access uses parameterized `PreparedStatement` across all repositories (`ProjectRepository`, `PipelineRepository`, `ExecutionRepository`, `LogRepository`, `DiagnosisRepository`).
- An embedded H2 database mode is available as a fallback and for zero-dependency unit tests, ensuring tests never mutate developer databases.

### Consequences
- Robust, reliable, production-ready MySQL persistence.
- Zero barrier to entry for testing and CI pipelines.
