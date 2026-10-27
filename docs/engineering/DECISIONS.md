# DevOpsHub — Architectural & Engineering Decisions (ADR)

---

## ADR-001: Separation of Bootstrap Launcher from JavaFX Application Subclass
- **Context:** JavaFX 11+ runtime enforces modular encapsulation. If a class extending `javafx.application.Application` is invoked as the `Main-Class` from a non-modular JAR on the standard classpath, the JVM terminates with `RuntimeError: JavaFX runtime components are missing`.
- **Decision:** Introduce `com.devopshub.app.Launcher` as a pure standard Java class without extending `Application`. Its `main` method delegates to `MainApp.main(args)`.
- **Consequences:** The application can be packaged into a standalone fat JAR (`maven-assembly-plugin`) and executed directly with `java -jar` without requiring `--module-path` JVM arguments.

---

## ADR-002: Dual Persistence Model with Explicit UI Transparency
- **Context:** Desktop developers frequently run offline or without a local MySQL service. However, silent fallback to in-memory H2 destroys pipeline history upon application exit without warning.
- **Decision:** Retain H2 fallback capability in `DatabaseManager` for zero-setup development, but mandate explicit disclosure:
  1. Primary target is always MySQL 8.0 via HikariCP.
  2. If MySQL fails, `DatabaseManager.isH2Fallback()` is set to `true`.
  3. The desktop header displays an amber status pill ("H2 Database (In-Memory Fallback)") rather than a green "MySQL Connected" pill.
  4. Toast notification warns the user that in-memory data is volatile.
- **Consequences:** Developers are never misled into assuming uncommitted in-memory executions are durable.

---

## ADR-003: Subprocess Process Tree Termination via `ProcessHandle`
- **Context:** Desktop CI/CD applications spawn external shell commands (e.g. `mvn`, `npm`, `bash`). Standard `Process.destroy()` kills only the immediate process shell, leaving compiler threads or test runners orphaned in the background.
- **Decision:** Utilize Java 9+ `ProcessHandle.descendants()` to traverse and issue `destroyForcibly()` across all child and descendant process PIDs before closing stdout/stderr pipes.
- **Consequences:** Guarantees zero orphaned processes when a user clicks "Cancel Execution" in the UI.

---

## ADR-004: Bit-for-Bit SHA-256 Deployment Verification
- **Context:** Earlier prototypes logged a simulated deployment without verifying that the pipeline actually produced an artifact.
- **Decision:** The deploy stage inspects `target/`, `build/libs/`, or `dist/` for `.jar`, `.war`, `.zip`, `.tar.gz`. If none exists, the deploy stage immediately fails with code 1. If present, it copies the artifact to the designated deployment directory and computes a SHA-256 checksum stored in the database.
- **Consequences:** Deployments are authenticated and verifiable.

---

## ADR-005: Security Threat Model & Credential Scrubbing
- **Context:** CI/CD runners handle sensitive tokens and URLs.
- **Decision:** Desktop runs within the user's host OS account permissions. Arbitrary shell commands are executed via `ProcessBuilder`. All command arguments and stdout/stderr streams pass through regex credential scrubbing to mask URLs matching `https://.*@` and common token formats.
- **Consequences:** Sensitive Git tokens are prevented from being stored in `execution_logs` database tables.
