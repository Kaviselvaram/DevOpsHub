# DevOpsHub — Intelligent CI/CD Management Platform

[![Java 21+](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21.0.6-blue.svg)](https://openjfx.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/Academic-CSE1007-green.svg)](docs/README.md)

> **Execute • Understand • Optimize • Recover**  
> *DevOpsHub transforms raw CI/CD logs and exit codes into transparent, explainable failure diagnoses and actionable recovery recommendations.*

---

## 1. Overview & Vision

Modern software engineering teams spend countless hours investigating failing builds, digging through thousands of lines of terminal logs to find root causes. Current CI/CD tools merely report that a stage broke; **DevOpsHub understands behavior and recommends the exact next action**.

DevOpsHub is a native **JavaFX desktop platform** engineered for the **CSE1007 — Java Programming** curriculum. Built purely in Java, JDBC, and JavaFX, it orchestrates sequential and parallel pipeline executions, collects bounded stdout/stderr streams, and feeds structured evidence into an explainable Java rule engine.

```
Create Project ➔ Configure Pipeline ➔ Validate Pipeline ➔ Execute Pipeline ➔ Monitor Stages ➔ Collect Logs ➔ Analyze Failure ➔ Recommend Action ➔ Review History ➔ Recover
```

---

## 2. Key Capabilities & Differentiators

| Capability | DevOpsHub Implementation |
|---|---|
| **Native Desktop UI** | 100% JavaFX desktop frontend styled with a modern Slate & Sky design system. Zero web/React dependencies. |
| **Strict SVG-Only Icons** | Every icon is rendered natively using scalable vector path geometry (`SVGPath`). Zero raster images or emojis. |
| **Execution Engine** | High-performance Java multithreading with `ExecutorService`, `ProcessBuilder`, and Fork/Join parallel stage execution. |
| **Rule-Based Intelligence** | Lightweight, deterministic Java rule engine that matches error patterns and pinpoints causes (e.g. HTTP 401 vs 500 in `LoginService`). |
| **Actionable Recommendations** | Context-aware recovery advice linked directly to matched rule diagnoses. |
| **Durable Persistence** | Production-ready MySQL database connectivity using `HikariCP` connection pooling and parameterized `PreparedStatement`. |
| **Bounded Log Streaming** | Memory-bounded circular buffering preventing JVM OutOfMemory errors while streaming live output to a dark terminal console. |

---

## 3. Technology Stack

- **Frontend Desktop UI:** JavaFX 21 (`javafx-controls`, `javafx-graphics`, `javafx-fxml`)
- **Core Language:** Java (JDK 21 / 25 LTS)
- **Styling:** Native JavaFX CSS (`src/main/resources/css/devopshub.css`)
- **Build System:** Apache Maven 3.6+
- **Database:** MySQL 8.0 (with durable, disk-backed embedded H2 fallback in `./data/devopshub`)
- **Database Connectivity:** JDBC with `com.mysql:mysql-connector-j`, embedded H2, and `HikariCP` connection pooling
- **Security & Isolation:** Environment variable sanitization, strict workspace confinement, Git ceiling controls, and fail-closed policies for untrusted workloads
- **Webhook Security:** Cryptographic HMAC-SHA256 signature verification (`X-Hub-Signature-256`), replay window defense, and nonce deduplication
- **Testing:** JUnit 5 (`junit-jupiter:5.10.2`)
- **Logging:** SLF4J 2.0 with Logback Classic and memory-bounded circular buffers
- **Icons:** 100% Scalable Vector Graphics (`SVGPath`)

---

## 4. Quick Start & Launch

### Prerequisites
1. **JDK 21 or later** (`java -version` and `javac -version`)
2. **Apache Maven 3.6+** (`mvn -version`)
3. **Docker or MySQL 8.0** running on `localhost:3306` (Optional: DevOpsHub automatically falls back to persistent disk-backed H2 if MySQL is unavailable)

### 1. Database Setup (Optional)
```bash
./scripts/start_mysql.sh
```
*(If MySQL is not started, DevOpsHub automatically initializes a durable embedded H2 database at `./data/devopshub`.)*

### 2. Run the JavaFX Application
**Option A: Direct Development Mode (Maven JavaFX Plugin)**
```bash
./scripts/run_app.sh
# Or directly:
mvn javafx:run
```

**Option B: Packaged Executable JAR (With Bundled Dependencies)**
```bash
mvn clean package -DskipTests
java -jar target/devopshub-desktop-1.0.0.jar
```

### 3. Run Automated Tests (Authorized Verification Phase)
```bash
./scripts/run_tests.sh
# Or directly:
mvn test
```

---

## 5. Repository Structure

```
javacicd/
├── pom.xml                        # Maven dependencies & build configuration
├── .env.example                   # Database and environment template
├── .gitignore                     # Git exclusions
├── docs/                          # Comprehensive technical documentation suite
│   ├── REQUIREMENTS.md            # Traceability matrix with unique requirement IDs
│   ├── FEATURE_MATRIX.md          # Feature matrix and implementation status
│   ├── ARCHITECTURE.md            # System architecture & Mermaid diagrams
│   ├── WORKFLOWS.md               # Step-by-step user workflows
│   ├── DATABASE_DESIGN.md         # Schema ER diagrams and tables
│   ├── CONCURRENCY_MODEL.md       # Threading, Fork/Join, synchronization
│   ├── FAILURE_ANALYSIS.md        # Rule-based diagnostic engine
│   └── ...
├── database/
│   └── migrations/
│       ├── V1__initial_schema.sql # DDL table definitions
│       └── V2__sample_seed_data.sql # Seed records matching presentation
├── scripts/
│   ├── start_mysql.sh             # Launch local Docker MySQL 8
│   ├── run_app.sh                 # Start JavaFX desktop application
│   └── run_tests.sh               # Execute automated test suite
└── src/
    ├── main/
    │   ├── java/com/devopshub/
    │   │   ├── app/               # JavaFX Application & Launcher entry points
    │   │   ├── config/            # AppConfig & DatabaseManager (HikariCP)
    │   │   ├── model/             # Domain entities (Project, Pipeline, Execution...)
    │   │   ├── repository/        # JDBC Repositories (PreparedStatement)
    │   │   ├── service/           # Business services (Project, Pipeline, Dashboard)
    │   │   ├── pipeline/          # DAG Validation & stage composition
    │   │   ├── execution/         # Concurrency engine, ProcessExecutor, LogCollector
    │   │   ├── analysis/          # Rule-based failure engine & recommendations
    │   │   └── ui/                # JavaFX views, layout shell, SvgIcon component
    │   └── resources/
    │       ├── css/               # Modern desktop styling tokens
    │       ├── icons/svg/         # High-DPI scalable SVG assets
    │       └── db/migrations/     # Classpath SQL migration scripts
    └── test/java/com/devopshub/   # JUnit 5 unit & integration tests
```

---

## 6. Academic Syllabus Mapping (CSE1007)

DevOpsHub integrates the core Java syllabus concepts into production functionality:

- **OOP & Encapsulation:** Domain models (`Project`, `Pipeline`, `Stage`, `Execution`) encapsulate data and state invariants.
- **Inheritance & Polymorphism:** Execution strategies and failure rule hierarchies implement polymorphic contracts (`FailureRule`).
- **Interfaces:** `FailureRule`, `Callable<StageExecution>`, and `Consumer<ExecutionLog>` establish clear architectural boundaries.
- **Collections & Generics:** Generics-safe DAG representations (`Map<String, List<String>>`), topological sorting, and `BoundedLogBuffer`.
- **Custom Exceptions:** `PipelineValidationException`, `StageExecutionException`, `ExecutionTimeoutException`, `DatabaseException`.
- **Multithreading & Concurrency:** Bounded `ThreadPoolExecutor` instances orchestrate concurrent parallel stage forks (`Fork/Join`).
- **Synchronization & Thread Safety:** Concurrent maps, thread-safe buffers, and UI marshaling via `Platform.runLater()`.
- **File I/O & Process Management:** `ProcessBuilder` executes OS processes safely, capturing stdout/stderr streams.
- **JDBC & Transactions:** Parameterized SQL with `PreparedStatement`, HikariCP connection pooling, and automated schema migrations.

---

## 7. Documentation Index

Detailed architectural and operational documentation is available in the [`docs/`](docs/) directory:
- [Requirements Traceability Matrix](docs/REQUIREMENTS.md)
- [Feature Matrix & Status](docs/FEATURE_MATRIX.md)
- [Architecture & Design Guide](docs/ARCHITECTURE.md)
- [Concurrency & Execution Model](docs/CONCURRENCY_MODEL.md)
- [Rule-Based Failure Analysis](docs/FAILURE_ANALYSIS.md)
- [Database Schema & ER Diagram](docs/DATABASE_DESIGN.md)
- [Local Development & Setup](docs/LOCAL_DEVELOPMENT.md)
