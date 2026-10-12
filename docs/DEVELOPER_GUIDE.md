# DevOpsHub Developer Guide & Extension Manual

## 1. Developer Architecture
DevOpsHub is built on pure Java 21, JavaFX, Maven, and MySQL with HikariCP. It follows strict separation of concerns:
- **Presentation:** `com.devopshub.view.*`
- **Application Services:** `com.devopshub.service.*`
- **Execution Engine:** `com.devopshub.execution.*`
- **Intelligence Analysis:** `com.devopshub.analysis.*`
- **Persistence (JDBC):** `com.devopshub.repository.*`

---

## 2. Adding a New Failure Diagnosis Rule
To add a new custom diagnostic rule to the intelligence engine:

1. Create a new class in `com.devopshub.analysis.rules`:
   ```java
   package com.devopshub.analysis.rules;

   import com.devopshub.analysis.FailureRule;
   import com.devopshub.model.ExecutionLog;
   import com.devopshub.model.FailureDiagnosis;
   import com.devopshub.model.StageExecution;
   import com.devopshub.model.enums.FailureCategory;
   import java.util.List;
   import java.util.UUID;

   public class OutOfMemoryRule implements FailureRule {
       @Override
       public String getRuleId() {
           return "RULE-OOM-001";
       }

       @Override
       public String getRuleName() {
           return "Out of Memory Exception Rule";
       }

       @Override
       public FailureCategory getCategory() {
           return FailureCategory.CONFIGURATION_ERROR;
       }

       @Override
       public boolean matches(StageExecution stageExecution, List<ExecutionLog> logs) {
           return logs.stream().anyMatch(log -> 
               log.getMessage().contains("java.lang.OutOfMemoryError: Java heap space"));
       }

       @Override
       public FailureDiagnosis diagnose(StageExecution stageExecution, List<ExecutionLog> logs) {
           return new FailureDiagnosis(
               UUID.randomUUID().toString(),
               stageExecution.getExecutionId(),
               getRuleId(),
               getCategory().name(),
               "JVM Heap Memory Exhausted",
               "The process exceeded allocated JVM heap limits during execution.",
               "java.lang.OutOfMemoryError: Java heap space detected in logs",
               "Increase JVM max heap memory by passing -Xmx2g in stage parameters.",
               0.98,
               null
           );
       }
   }
   ```

2. Register the rule in `FailureRuleEngine`:
   ```java
   rules.add(new OutOfMemoryRule());
   ```

3. Add corresponding unit tests in `FailureRuleEngineTest` with fixture log snippets.

---

## 3. Adding a New JavaFX View
1. Extend `javafx.scene.layout.VBox` or `BorderPane`.
2. Accept required services in the constructor (injected via `ServiceFactory`).
3. Build the UI hierarchy cleanly using standard JavaFX controls and `devopshub.css` style classes.
4. Add the navigation token in `MainLayoutView.java` and handle view switching.
