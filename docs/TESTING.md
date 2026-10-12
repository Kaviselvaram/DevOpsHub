# DevOpsHub Testing Strategy & Quality Verification

## 1. Testing Philosophy
DevOpsHub maintains a comprehensive test suite across three main levels:
1. **Unit Testing:** Validates business rules, topological sorting, pipeline validation, and isolated failure analysis rules using deterministic mock fixtures.
2. **Integration Testing:** Tests real JDBC persistence, database constraints, cascading deletes, transactions, and migration scripts.
3. **Execution Engine Testing:** Tests actual subprocess execution, exit code evaluation, thread pooling, and log streaming.

---

## 2. Test Suite Architecture

```
src/test/java/com/devopshub/
├── service/
│   ├── ProjectServiceTest.java       # Validates project CRUD & repo URL validation
│   └── PipelineValidatorTest.java    # Validates DAG cycle detection & empty stage rules
│
├── analysis/
│   └── FailureRuleEngineTest.java    # Verifies all 6 rules against realistic logs
│
├── execution/
│   └── ExecutionEngineTest.java      # Runs real echo/exit subprocesses & verifies DAG
│
└── repository/
    └── ProjectRepositoryTest.java    # Verifies JDBC persistence & relational queries
```

---

## 3. Running Tests
Execute the entire test suite:
```bash
mvn test
```

### Verified Test Results Summary:
```text
[INFO] Running com.devopshub.service.ProjectServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.devopshub.pipeline.PipelineValidatorTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.devopshub.analysis.FailureRuleEngineTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.devopshub.execution.ExecutionEngineTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.devopshub.repository.ProjectRepositoryTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

---

## 4. Key Test Scenarios Covered
1. **Cycle Detection in Pipelines:** Rejects stage loops (e.g. A -> B -> C -> A) with `ValidationException`.
2. **Deterministic Failure Intelligence:** Specifically asserts that logs containing `expected HTTP 401 but received HTTP 500 at LoginService` trigger `AssertionFailureRule` (`RULE-ASSERT-001`) with recommended action.
3. **Subprocess Non-zero Exits:** Confirms that a failing process (`exit 1`) stops execution, skips downstream stages, and generates a persistent failure diagnosis.
4. **Relational Database Lifecycle:** Tests insertion, retrieval, update, and deletion of projects, pipelines, stages, executions, logs, and diagnoses.
