# DevOpsHub Rule Engine Specification

## 1. Engine Structure & Extensibility
The Failure Rule Engine uses a pluggable Strategy pattern centered around the `com.devopshub.analysis.FailureRule` interface:

```java
public interface FailureRule {
    String getRuleId();
    String getRuleName();
    FailureCategory getCategory();
    boolean matches(StageExecution stageExecution, List<ExecutionLog> logs);
    FailureDiagnosis diagnose(StageExecution stageExecution, List<ExecutionLog> logs);
}
```

---

## 2. Implemented Failure Rules

| Rule ID | Rule Class | Category | Trigger Pattern | Action Recommended |
|---|---|---|---|---|
| `RULE-ASSERT-001` | `AssertionFailureRule` | `TEST_FAILURE` | `AssertionError`, `Expected.*received`, `HTTP 401.*HTTP 500` | Review test expectations and unhandled exceptions in endpoint logic. |
| `RULE-COMP-001` | `CompilationErrorRule` | `COMPILATION_ERROR` | `cannot find symbol`, `javac.*error`, `syntax error` | Fix syntax error or missing symbol import reported by compiler. |
| `RULE-DEP-001` | `MissingDependencyRule` | `DEPENDENCY_RESOLUTION` | `Could not resolve dependencies`, `ClassNotFoundException`, `NoClassDefFoundError` | Verify `pom.xml` / `build.gradle` dependency coordinates and repositories. |
| `RULE-DB-001` | `DatabaseConnectionRule` | `DATABASE_ERROR` | `Communications link failure`, `Access denied for user`, `Connection refused.*3306` | Check database host availability, port bindings, and credentials. |
| `RULE-TIMEOUT-001` | `TimeoutRule` | `TIMEOUT` | `timed out after`, process exit code -1 / timeout flag | Increase stage timeout threshold or optimize slow I/O / test suite. |
| `RULE-PERM-001` | `PermissionDeniedRule` | `PERMISSION_DENIED` | `Permission denied`, `access denied`, `EACCES` | Ensure execute bit (`chmod +x`) is set and file system permissions are correct. |

---

## 3. Fallback Mechanism
If a stage fails with a non-zero exit code but does not match any of the registered rules, the engine falls back to a default `UNKNOWN` diagnostic rule:
- **Title:** Unclassified Pipeline Failure (Exit code `X`)
- **Likely Cause:** The stage exited with non-zero status without matching known error signatures.
- **Action:** Inspect the raw execution logs in the Log Viewer tab for custom application errors.
