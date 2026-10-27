# DevOpsHub — Individual Pipeline Stage Audit

**Audit Date:** 2026-10-23  
**Auditor:** Principal Software Architect, Senior Java Engineer, CI/CD Platform Engineer  

---

## 1. Supported Stage Types Overview

DevOpsHub implements 6 core stage types declared in `com.devopshub.model.StageType`:
1. `CHECKOUT`
2. `BUILD`
3. `TEST`
4. `SECURITY`
5. `PACKAGE`
6. `DEPLOY`

---

## 2. Stage-by-Stage Detailed Audit

### Stage 1: CHECKOUT

1. **Stage Name:** `CHECKOUT` (e.g. "Checkout Source", "Git Clone")
2. **Purpose:** Clones or updates the target Git repository or workspace directory to the configured branch/revision.
3. **Entry Conditions:** Project must have a valid `repository_url`.
4. **Trigger:** Pipeline execution start in `ExecutionEngine.executePipelineAsync()`.
5. **Inputs:** Repository URL, branch name, target workspace path.
6. **Implementation:** `GitService.cloneOrFetch()` in `GitService.java:72-129`.
7. **External Commands:** `git clone --branch <branch> --single-branch <url>` or `git fetch --all --prune` and `git checkout <branch>`.
8. **Working Directory:** `workspaces/projects/<projectId>/`.
9. **Expected Outputs:** Checked-out source repository containing project source files.
10. **Exit Criteria:** `git clone` or `git checkout` exits with code `0`.
11. **Exit-Code Handling:** Checked directly via `GitExecutionResult.getExitCode()`.
12. **Failure Handling:** Returns non-zero exit code, logs Git stderr (e.g. repository not found, authentication failure), stops pipeline immediately with status `FAILED`.
13. **Retry Behavior:** Safe to re-run; existing checkout triggers `fetch` and `pull` rather than duplicate cloning.
14. **Timeout Behavior:** 120-second timeout on Git operations.
15. **Cancellation:** Process destroyed via `Process.destroyForcibly()`.
16. **Downstream Dependencies:** All subsequent build/test stages depend on CHECKOUT.
17. **Persistence:** Execution record updated in MySQL `executions`.
18. **UI Representation:** Displayed as first stage in `ExecutionDetailView` stages list and visual flow map.
19. **Security:** Git credentials masked via `SecretMasker.mask(url)`.
20. **Verification:** Verified in `RealLairIntegrationTest.java` (cloned L/Air repo) and `GitServiceTest.java`.
21. **Verdict:** **REAL AND VERIFIED**
22. **Defect Severity:** None.
23. **Required Correction:** None.

---

### Stage 2: BUILD

1. **Stage Name:** `BUILD` (e.g. "Compile Application", "npm install", "Next.js Build")
2. **Purpose:** Resolves dependencies and compiles source code into executable binaries or bundles.
3. **Entry Conditions:** `CHECKOUT` stage succeeded and source files exist.
4. **Trigger:** `ExecutionEngine` invokes `StageRunner.executeStage()`.
5. **Inputs:** Checked-out source code, build tool on PATH (`mvn`, `npm`, `javac`, `gradle`).
6. **Implementation:** `StageRunner.java:35-93`, `ProcessExecutor.java:30-144`.
7. **External Commands:** `mvn clean compile -B`, `npm ci`, `npm run build`, `gradle compileJava`.
8. **Working Directory:** `workspaces/projects/<projectId>/`.
9. **Expected Outputs:** Compiled class files (`target/classes`), bundled assets (`.next/BUILD_ID`), or compiled binaries.
10. **Exit Criteria:** Process exit code `0` AND output verification (e.g. `.next` directory exists for Next.js).
11. **Exit-Code Handling:** Inspected via `process.exitValue()`.
12. **Failure Handling:** Non-zero exit code marks stage `FAILED`, triggers `FailureRuleEngine`, records diagnosis in MySQL.
13. **Retry Behavior:** Idempotent; executing build again cleans/rebuilds artifacts.
14. **Timeout Behavior:** Configurable timeout (default 300s). Exceeding timeout returns exit code `124`.
15. **Cancellation:** `terminateProcessTree()` terminates compiler and child processes.
16. **Downstream Dependencies:** Dependent TEST and PACKAGE stages.
17. **Persistence:** `stage_executions` and `execution_logs` saved in MySQL.
18. **UI Representation:** Stage pill in topology map and console log panel in `ExecutionDetailView`.
19. **Security:** Secret masking applied to compile flags and environment variables.
20. **Verification:** Verified in `RealLairIntegrationTest.testNextJsBuildProducesRealBuildArtifacts()` (`.next/BUILD_ID` verified).
21. **Verdict:** **REAL AND VERIFIED**
22. **Defect Severity:** None.
23. **Required Correction:** None.

---

### Stage 3: TEST

1. **Stage Name:** `TEST` (e.g. "Unit Tests", "npm run lint", "Integration Tests")
2. **Purpose:** Executes automated test suites or linters and collects test results.
3. **Entry Conditions:** `BUILD` stage succeeded.
4. **Trigger:** `StageRunner.executeStage()`.
5. **Inputs:** Compiled application and test source code.
6. **Implementation:** `StageRunner.java:35-93`, `ProcessExecutor.java:30-144`.
7. **External Commands:** `mvn test -B`, `npm run lint`, `pytest`, `go test ./...`.
8. **Working Directory:** `workspaces/projects/<projectId>/`.
9. **Expected Outputs:** Test execution reports, zero assertion errors.
10. **Exit Criteria:** Exit code `0` (zero test failures).
11. **Exit-Code Handling:** Non-zero exit code (e.g. 1 or custom exit code like 23) captured directly.
12. **Failure Handling:** Marks stage `FAILED`, stops downstream stages, captures failed test names in failure diagnosis.
13. **Retry Behavior:** Safe; re-executes tests without modifying persistent application state.
14. **Timeout Behavior:** Default 180s. Timed-out tests killed with exit code `124`.
15. **Cancellation:** Clean process tree destruction via Java 9+ `ProcessHandle.descendants()`.
16. **Downstream Dependencies:** PACKAGE and DEPLOY stages.
17. **Persistence:** `stage_executions` status and exit code stored in MySQL.
18. **UI Representation:** Red failure badge with exit code and execution time.
19. **Security:** Test logs sanitized for secrets.
20. **Verification:** Verified in `RealLairIntegrationTest.testControlledFailureProducesRealExitCode23()`.
21. **Verdict:** **REAL AND VERIFIED**
22. **Defect Severity:** None.
23. **Required Correction:** None.

---

### Stage 4: SECURITY

1. **Stage Name:** `SECURITY` (e.g. "Security Scan", "Dependency Audit")
2. **Purpose:** Scans dependencies and source code for known CVEs or code quality vulnerabilities.
3. **Entry Conditions:** Source and dependency manifest exist.
4. **Trigger:** `StageRunner.executeStage()`.
5. **Inputs:** `pom.xml`, `package-lock.json`, or source files.
6. **Implementation:** `StageRunner.java`, `ProcessExecutor.java`.
7. **External Commands:** `npm audit --audit-level=high`, `mvn dependency-check:check`, `trivy fs .`.
8. **Working Directory:** `workspaces/projects/<projectId>/`.
9. **Expected Outputs:** Security scan log and vulnerability assessment.
10. **Exit Criteria:** Exit code `0` (no critical vulnerabilities found).
11. **Exit-Code Handling:** Evaluates CLI exit code.
12. **Failure Handling:** Fails stage if high/critical vulnerabilities trigger non-zero exit code.
13. **Retry Behavior:** Idempotent read-only scan.
14. **Timeout Behavior:** Default 180s.
15. **Cancellation:** Supported via process tree termination.
16. **Downstream Dependencies:** PACKAGE or DEPLOY.
17. **Persistence:** Saved in `stage_executions`.
18. **UI Representation:** Security stage pill with status indicator in DAG map.
19. **Security:** Does not transmit codebase outside local host.
20. **Verification:** Verified in `LifecycleEndToEndAuditTest.java`.
21. **Verdict:** **REAL AND VERIFIED**
22. **Defect Severity:** None.
23. **Required Correction:** None.

---

### Stage 5: PACKAGE

1. **Stage Name:** `PACKAGE` (e.g. "Package Artifact", "Jar Assembly")
2. **Purpose:** Packages compiled binaries into distribution archives (e.g. `.jar`, `.zip`, `.tar.gz`).
3. **Entry Conditions:** BUILD and TEST stages succeeded.
4. **Trigger:** `StageRunner.executeStage()`.
5. **Inputs:** Compiled classes, resources, dependencies.
6. **Implementation:** `StageRunner.java`, `ProcessExecutor.java`.
7. **External Commands:** `mvn package -DskipTests=true -B`, `npm pack`, `tar -czf app.tar.gz dist/`.
8. **Working Directory:** `workspaces/projects/<projectId>/`.
9. **Expected Outputs:** Packaged artifact file in project directory (e.g. `target/app.jar`).
10. **Exit Criteria:** Packaging tool returns exit code `0` and artifact exists on disk.
11. **Exit-Code Handling:** Inspected via `process.exitValue()`.
12. **Failure Handling:** Non-zero exit code fails stage.
13. **Retry Behavior:** Safe; overwrites destination artifact.
14. **Timeout Behavior:** Default 300s.
15. **Cancellation:** Supported.
16. **Downstream Dependencies:** DEPLOY stage.
17. **Persistence:** Saved in `stage_executions`.
18. **UI Representation:** Displays artifact name and completion status in `ExecutionDetailView`.
19. **Security:** Safe process execution.
20. **Verification:** Verified in `LifecycleEndToEndAuditTest.java`.
21. **Verdict:** **REAL AND VERIFIED**
22. **Defect Severity:** None.
23. **Required Correction:** None.

---

### Stage 6: DEPLOY

1. **Stage Name:** `DEPLOY` (e.g. "Deploy Staging", "Local File Deployment")
2. **Purpose:** Deploys verified build artifacts to a designated deployment directory with SHA-256 integrity verification.
3. **Entry Conditions:** PACKAGE stage produced a verified artifact file.
4. **Trigger:** `StageRunner.executeStage()` or `DeploymentService.deployArtifact()`.
5. **Inputs:** Source artifact file, destination target directory.
6. **Implementation:** `DeploymentService.java:25-70`.
7. **External Commands:** File copy / atomic rename via Java `Files.copy()`.
8. **Working Directory:** `workspaces/projects/<projectId>/`.
9. **Expected Outputs:** Copied artifact file in target deployment folder, logged SHA-256 checksum and byte count.
10. **Exit Criteria:** Destination file exists, byte length matches source, SHA-256 hash matches.
11. **Exit-Code Handling:** Success or failure reflected via `DeploymentResult.isSuccess()`.
12. **Failure Handling:** Fails stage if source file missing, directory inaccessible, or hash mismatch.
13. **Retry Behavior:** Safe; overwrites existing target file cleanly.
14. **Timeout Behavior:** Fast local I/O, bounded by stage timeout.
15. **Cancellation:** Supported.
16. **Downstream Dependencies:** None (terminal pipeline stage).
17. **Persistence:** Saved in `stage_executions`.
18. **UI Representation:** Deployment status and SHA-256 checksum displayed in logs.
19. **Security:** Validates path boundaries; rejects traversal out of deployment target directory.
20. **Verification:** Verified in `DeploymentServiceTest.testSuccessfulDeploymentCopiesFileAndComputesSha256()`.
21. **Verdict:** **REAL AND VERIFIED**
22. **Defect Severity:** None.
23. **Required Correction:** None (Local file deployment verified; remote cloud deployment is intentionally out of scope for Desktop MVP).
