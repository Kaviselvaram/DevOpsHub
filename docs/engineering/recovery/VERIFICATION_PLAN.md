# DEVOPSHUB — AUTHORIZED RUNTIME VERIFICATION PLAN

**Document Version:** 1.0.0  
**Phase:** Authorized Verification Plan (Static Blueprint — DO NOT EXECUTE DURING RECOVERY PHASE)  
**Target Environment:** Developer Workstation / CI Runner with Java 21 LTS  

---

## 1. Verification Protocol & Execution Rules

> [!WARNING]
> **CRITICAL EXECUTION RESTRICTION IN CURRENT PHASE:**  
> The commands and procedures outlined in this document are **strictly specifications for a future, explicitly authorized verification phase**. None of these commands have been executed during the engineering recovery phase.

### Execution Requirements for Future Phase:
1. All commands must be executed in a dedicated, isolated test environment.
2. The external target repository `https://github.com/Kaviselvaram/lair.git` must NEVER be modified, pushed to, or used destructively.
3. Use temporary directories or disposable databases where specified.

---

## 2. Test Plan Matrix

| Plan ID | Category | Component | Purpose | Preconditions | Modifies Data | Safe on Dev Machine |
|:---|:---:|:---:|:---|:---:|:---:|:---:|
| **TP-01** | Security | `SecurityIsolationAuditTest` | Verify env sanitization & fail-closed untrusted policy | Java 21, Maven 3.6+ | No | Yes |
| **TP-02** | Security | `WebhookHmacVerificationTest` | Verify HMAC-SHA256 & replay defense | Java 21, Maven 3.6+ | No | Yes |
| **TP-03** | Security | `GitWorkspaceSecurityAuditTest` | Verify workspace confinement & safe cleanup | Java 21, Git CLI installed | No | Yes |
| **TP-04** | Data | `DatabaseManagerPersistenceAuditTest`| Verify H2 disk persistence & backup | Java 21, Maven 3.6+ | Yes (temp files) | Yes |
| **TP-05** | Pipeline | `ExecutionEngineCloneFailureTest` | Verify clone failure aborts pipeline | Java 21, Git CLI installed | No | Yes |
| **TP-06** | Engine | `ExecutionEngineTest` | Verify parallel fork execution & cancellation | Java 21, Maven 3.6+ | Yes (H2/MySQL) | Yes |
| **TP-07** | Packaging| Standalone Executable JAR Launch | Verify thin JAR classpath resolution (`libs/`)| Java 21, Maven package build | No | Yes |
| **TP-08** | E2E | Webhook HTTP Endpoint (`curl`) | Verify live signature checking & replay reject | Webhook server running (:8080) | No | Yes |
| **TP-09** | Durability| Genuine JVM Process Restart | Verify data persists across true process kill | MySQL stopped, H2 disk file | Yes (data dir) | Yes |
| **TP-10** | UI / E2E | Full UI Manual Walkthrough | Verify search, scheduling, monitoring, alerts | JavaFX display environment | Yes (local DB) | Yes |

---

## 3. Detailed Verification Procedures

### TP-01: Execution Isolation & Environment Sanitization
- **Purpose:** Prove that `ExecutionPolicy` strips host credentials (`AWS_*`, `GITHUB_*`, `SSH_*`, `DATABASE_*`, passwords, tokens) and rejects untrusted external jobs with exit code 126 when no container runner is present.
- **Preconditions:** Java 21 and Maven installed.
- **Modifies Data:** No.
- **Developer Machine Safety:** Safe.
- **Exact Command:**
  ```bash
  mvn test -Dtest=SecurityIsolationAuditTest
  ```
- **Expected Result:** All 4 tests pass (`testEnvironmentSanitizationStripsSensitiveVariables`, `testUntrustedExecutionFailsClosedWithoutContainer`, `testWorkingDirectoryValidationBlocksForbiddenRoots`, `testProcessExecutorRejectionReturnsCode126`).
- **Failure Interpretation:** If tests fail, environment variables or untrusted policy checks in `ProcessExecutor` or `ExecutionPolicy` were modified or weakened.
- **Cleanup:** None.

---

### TP-02: Webhook HMAC-SHA256 Cryptographic Verification
- **Purpose:** Prove HMAC-SHA256 signature computation, constant-time verification, rejection of altered payloads, rejection of invalid keys, and secret rotation support.
- **Preconditions:** Java 21 and Maven installed.
- **Modifies Data:** No.
- **Developer Machine Safety:** Safe.
- **Exact Command:**
  ```bash
  mvn test -Dtest=WebhookHmacVerificationTest
  ```
- **Expected Result:** All 5 tests pass (`testValidHmacSignatureVerification`, `testAlteredPayloadFailsVerification`, `testInvalidSignatureRejection`, `testMissingSecretFailsClosed`, `testSecretRotationSecondaryKeyVerification`).
- **Failure Interpretation:** If verification fails, `Mac.getInstance("HmacSHA256")` or `MessageDigest.isEqual` implementation is broken.
- **Cleanup:** None.

---

### TP-03: Git Workspace Confinement & Non-Destructive Cleanup
- **Purpose:** Prove that failed Git clones do NOT delete pre-existing user folders/files, paths outside `./workspaces` are rejected, directories without immediate `.git` return `"UNKNOWN"`, and recursive cleanup does not follow symlinks.
- **Preconditions:** Git CLI installed on host.
- **Modifies Data:** Creates and cleans up temporary folders in system temp.
- **Developer Machine Safety:** Safe.
- **Exact Command:**
  ```bash
  mvn test -Dtest=GitWorkspaceSecurityAuditTest
  ```
- **Expected Result:** All 4 tests pass (`testPreExistingDirectoryPreservedOnCloneFailure`, `testDestinationOutsideWorkspaceRootRejected`, `testDirectoryWithoutImmediateGitReturnsUnknown`, `testSafeCleanupDoesNotFollowSymlinks`).
- **Failure Interpretation:** If sentinel files are deleted, ownership tracking in `GitService.cloneOrFetch` has regressed.
- **Cleanup:** Temporary directories managed automatically by JUnit `@TempDir`.

---

### TP-04: Database Durability & Backup Verification
- **Purpose:** Prove that `DatabaseManager` distinguishes persistent storage from ephemeral storage and creates valid `.zip` database backups.
- **Preconditions:** Java 21 and Maven.
- **Modifies Data:** Generates temp backup zip archive.
- **Developer Machine Safety:** Safe.
- **Exact Command:**
  ```bash
  mvn test -Dtest=DatabaseManagerPersistenceAuditTest
  ```
- **Expected Result:** All 2 tests pass.
- **Failure Interpretation:** If backup fails, H2 `BACKUP TO` SQL command syntax or file permissions are invalid.
- **Cleanup:** Managed by `@TempDir`.

---

### TP-05: Pipeline Clone Failure Abort & Stage Skipping
- **Purpose:** Prove that when Git cloning fails during workspace preparation, `ExecutionEngine` immediately marks the execution as `FAILED`, marks all downstream stages as `SKIPPED`, and aborts before executing any stage commands.
- **Preconditions:** Git CLI available.
- **Modifies Data:** Inserts test execution records in local database.
- **Developer Machine Safety:** Safe.
- **Exact Command:**
  ```bash
  mvn test -Dtest=ExecutionEngineCloneFailureTest
  ```
- **Expected Result:** Test passes in < 3 seconds. Execution status is `FAILED`; stage statuses are `SKIPPED`.
- **Failure Interpretation:** If stage commands execute or status is not FAILED, the clone error guard in `ExecutionEngine.java` was bypassed.
- **Cleanup:** Test execution records remain in local test database or cleaned up by fixture.

---

### TP-06: Full Execution Engine & Parallel Fork Suite
- **Purpose:** Verify sequential execution, parallel fork/join execution, failure diagnosis rule matching, and cancellation propagation.
- **Preconditions:** Java 21, Maven.
- **Modifies Data:** Yes.
- **Developer Machine Safety:** Safe.
- **Exact Command:**
  ```bash
  mvn test -Dtest=ExecutionEngineTest
  ```
- **Expected Result:** All 4 tests in `ExecutionEngineTest` pass.
- **Failure Interpretation:** If parallel fork hangs or cancellation fails, thread pool coordinator or process destroyer has regressed.
- **Cleanup:** None.

---

### TP-07: Standalone Executable JAR Launch & Classpath Verification
- **Purpose:** Prove that the Maven build produces an executable JAR that launches without `ClassNotFoundException: javafx.application.Application` by loading bundled dependencies from `target/libs`.
- **Preconditions:** Maven 3.6+, Java 21.
- **Modifies Data:** Creates `target/` artifacts.
- **Developer Machine Safety:** Safe.
- **Exact Commands:**
  ```bash
  # Step 1: Package application and copy dependencies to target/libs
  mvn clean package -DskipTests

  # Step 2: Verify target/libs directory contains required JavaFX and database JARs
  ls -la target/libs/javafx-controls-*.jar target/libs/HikariCP-*.jar target/libs/h2-*.jar

  # Step 3: Launch packaged JAR with help/version check (or headful desktop launch)
  java -jar target/devopshub-desktop-1.0.0.jar
  ```
- **Expected Result:** `target/libs` contains all runtime dependencies; `java -jar` successfully initializes the JVM, resolves JavaFX classes from the manifest classpath, and opens the main desktop window.
- **Failure Interpretation:** If `ClassNotFoundException` occurs, `maven-jar-plugin` manifest classpath or `maven-dependency-plugin` configuration failed.
- **Cleanup:** Run `mvn clean`.

---

### TP-08: Live Webhook HMAC-SHA256 & Replay Window Verification
- **Purpose:** Verify the live HTTP webhook receiver rejects invalid signatures, accepts valid signatures, and rejects replayed requests.
- **Preconditions:** DevOpsHub application running with `WEBHOOK_PORT=8080` and `WEBHOOK_SECRET_KEY=test-secret-12345`.
- **Modifies Data:** Submits execution if payload valid.
- **Developer Machine Safety:** Safe.
- **Procedure:**
  ```bash
  # Step 1: Test request with missing secret/signature (Expected: HTTP 401 Unauthorized)
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/webhook \
       -H "Content-Type: application/json" \
       -d '{"pipelineId": "pipe-1"}'
  # Verify output: 401

  # Step 2: Test request with invalid signature (Expected: HTTP 401 Unauthorized)
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/webhook \
       -H "Content-Type: application/json" \
       -H "X-Hub-Signature-256: sha256=0000000000000000000000000000000000000000000000000000000000000000" \
       -d '{"pipelineId": "pipe-1"}'
  # Verify output: 401

  # Step 3: Compute valid HMAC-SHA256 for payload
  PAYLOAD='{"pipelineId":"pipe-1"}'
  SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "test-secret-12345" | sed 's/^.* //')
  NOW=$(date +%s%3N)
  NONCE=$(uuidgen)

  # Step 4: Send valid request with timestamp and nonce
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/webhook \
       -H "Content-Type: application/json" \
       -H "X-Hub-Signature-256: sha256=$SIG" \
       -H "X-Hub-Timestamp: $NOW" \
       -H "X-GitHub-Delivery: $NONCE" \
       -d "$PAYLOAD"
  # Verify output: 200

  # Step 5: Immediately replay the EXACT same delivery nonce (Expected: HTTP 403 Forbidden - Replay Detected)
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/webhook \
       -H "Content-Type: application/json" \
       -H "X-Hub-Signature-256: sha256=$SIG" \
       -H "X-Hub-Timestamp: $NOW" \
       -H "X-GitHub-Delivery: $NONCE" \
       -d "$PAYLOAD"
  # Verify output: 403
  ```
- **Expected Result:** Missing/invalid signatures receive 401; valid signatures receive 200; duplicate nonce receives 403.
- **Failure Interpretation:** If step 5 returns 200, replay nonce deduplication in `WebhookServerService` is inactive.
- **Cleanup:** None.

---

### TP-09: Persistence Across Genuine JVM Process Restart
- **Purpose:** Prove data durability in embedded H2 fallback mode across an actual OS process kill and restart.
- **Preconditions:** Ensure MySQL is stopped (`docker stop devopshub-mysql` or stop local MySQL daemon).
- **Modifies Data:** Creates `./data/devopshub.mv.db`.
- **Developer Machine Safety:** Safe.
- **Procedure:**
  1. Launch DevOpsHub in fallback mode:
     ```bash
     mvn javafx:run
     ```
  2. In the UI, navigate to **Projects** -> Click **+ New Project**.
  3. Enter Name: `"Durable Test Project"`, Repository: `"https://github.com/example/test.git"`, Branch: `"main"`. Save.
  4. Create a Pipeline named `"Durable Test Pipeline"` with 1 Stage: `"Echo Stage"` (`echo 'Durable execution'`). Save.
  5. Close the application window or terminate process with `Ctrl+C`.
  6. Verify the physical file exists on disk:
     ```bash
     ls -la data/devopshub.mv.db
     ```
  7. Relaunch the application:
     ```bash
     mvn javafx:run
     ```
  8. Navigate to **Projects** and **Pipelines**.
- **Expected Result:** `"Durable Test Project"` and `"Durable Test Pipeline"` are displayed immediately upon application reload.
- **Failure Interpretation:** If projects/pipelines are missing, `DatabaseManager` fell back to in-memory URL (`jdbc:h2:mem:devopshub`) rather than disk-backed file path.
- **Cleanup:** Delete `data/devopshub.mv.db` if test data is no longer needed.

---

### TP-10: End-to-End User Journey & UI Manual Walkthrough
- **Purpose:** Verify all interactive controls, global search filtering, scheduling dialogs, alerts navigation, and execution monitoring.
- **Preconditions:** Display environment available.
- **Modifies Data:** Yes (local database).
- **Developer Machine Safety:** Safe.
- **Walkthrough Steps:**
  1. **Global Search Verification (UI-02):**
     - Type query `"Durable"` in header search bar.
     - Verify active views filter in real-time.
     - Clear search input; verify full list is restored.
  2. **Pipeline Scheduling Verification (UI-01):**
     - Open a Pipeline detail view.
     - Click the **Schedule** button.
     - Select interval `"Every 15 minutes"`. Click **Save Schedule**.
     - Navigate to **Settings** view -> verify schedule appears in **Active Pipeline Schedules** table with next run time.
     - Click **Trigger Now**; verify execution begins.
     - Click **Unschedule**; verify job is removed from table.
  3. **Alerts Settings Navigation (UI-03):**
     - Click the gear icon in the top right of the **Alerts Sidebar**.
     - Verify view navigates directly to **SettingsView**.
  4. **Execution Monitoring & Bounded Logs (ARCH-04):**
     - Trigger a pipeline execution.
     - Verify the terminal log viewer updates in real time without UI freezes.
     - Check stage status indicators update as each stage succeeds or fails.
- **Expected Result:** All controls behave as specified without uncaught exceptions or UI thread deadlocks.
