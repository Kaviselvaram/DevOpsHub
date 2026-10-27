# DevOpsHub — Strict Full-System Integrity Findings Report

**Audit Date:** 2026-10-27  
**Scope:** Source code, domain models, database repositories, schema migrations, runtime configuration, persistence behavior, UI controllers, and execution engines.  
**Auditor:** Senior Java Architect, Database Reliability Engineer, CI/CD Platform Engineer, Independent Code Auditor  

---

## 1. Executive Summary of Audit Findings

| Finding ID | Severity | Category | Component | Status |
| :--- | :---: | :--- | :--- | :---: |
| **FINDING-FK-01** | **P1 (High)** | Relational Integrity / Persistence | `PipelineRepository.java` | **VERIFIED & REPAIRED** |
| **FINDING-FK-02** | **P1 (High)** | Transaction Boundaries / Persistence | `PipelineService.java` | **VERIFIED & REPAIRED** |
| **FINDING-UI-01** | **P2 (Med)** | UI State / Prerequisite Dangling Refs | `PipelineBuilderView.java` | **VERIFIED & REPAIRED** |
| **FINDING-DUP-01** | **P2 (Med)** | DAG Topology / Pipeline Cloning | `PipelineRepository.java` | **VERIFIED & REPAIRED** |
| **FINDING-DB-01** | **P2 (Med)** | UI Transparency / Misleading Status | `MainLayoutView`, `HomeView`, `SettingsView` | **VERIFIED & REPAIRED** |
| **FINDING-TST-01** | **P2 (Med)** | Test Isolation / Live DB Preservation | `AppConfig.java`, `DatabaseManager.java` | **VERIFIED & REPAIRED** |
| **FINDING-MGR-01** | **P3 (Low)** | Schema Drift / Migration Maintenance | `database/migrations/` vs `src/main/resources/` | **VERIFIED & DOCUMENTED** |

---

## 2. Detailed Findings & Root Cause Analysis

### FINDING-FK-01: Single-Pass Stage Dependency Insertion Causing Foreign Key Violation

- **Finding ID:** `FINDING-FK-01`
- **Severity:** P1 — High (Core persistence failure; prevents pipeline saving)
- **Confidence:** 100% (Reproduced via runtime logs and confirmed in source)
- **Affected Files & Line Ranges:**
  - `src/main/java/com/devopshub/repository/PipelineRepository.java:126–133`, `275–301`
- **Reproducible Evidence:**
  Application runtime log observed during user execution:
  ```
  ERROR c.d.repository.PipelineRepository - Failed to save pipeline: Cannot add or update a child row: a foreign key constraint fails (`devopshub`.`stage_dependencies`, CONSTRAINT `stage_dependencies_ibfk_2` FOREIGN KEY (`depends_on_stage_id`) REFERENCES `pipeline_stages` (`id`) ON DELETE CASCADE)
  ```
- **Actual Root Cause:**
  In `PipelineRepository.save(Pipeline pipeline)`, the stages loop iterates over `pipeline.getStages()` and calls `saveStage(conn, stage)` sequentially. Inside `saveStage`, it immediately executes:
  ```java
  for (String depId : stage.getDependsOnStageIds()) {
      saveStageDependency(conn, stage.getId(), depId);
  }
  ```
  If Stage A (index 0) depends on Stage B (index 1), Stage B has not yet been inserted into `pipeline_stages`. When MySQL evaluates the foreign key constraint `stage_dependencies_ibfk_2` referencing `pipeline_stages(id)`, the constraint check fails immediately and rolls back the transaction.
- **Affected Behavior & Data:**
  Any pipeline where prerequisite stages appear after downstream stages in the stage list, or where stages were reordered, cannot be saved to the database.
- **Smallest Viable Correction:**
  Implement a two-pass persistence algorithm within the transaction:
  - *Pass 1:* Insert/upsert all stages in `pipeline_stages` so that all IDs exist.
  - *Pass 2:* Clear and insert dependencies into `stage_dependencies`, checking that `depId` exists in the valid stages set.
  - Defensively guard `saveStageDependency` by checking `stageExists(conn, depId)` to gracefully skip any dangling foreign references rather than crashing.
- **Regression Risk & Rollback:** Low. Additive algorithm within existing transaction boundaries. Rollback: revert to previous method.
- **Verification Tests:** `PipelineRepositoryDependencyIntegrityTest.testSavePipelineWithForwardReferencingDependencies()`

---

### FINDING-FK-02: Non-Transactional Stage Update in `PipelineService.updatePipeline`

- **Finding ID:** `FINDING-FK-02`
- **Severity:** P1 — High (Risk of partial state persistence on failure)
- **Confidence:** 100%
- **Affected Files & Line Ranges:**
  - `src/main/java/com/devopshub/service/PipelineService.java:86–103`
- **Reproducible Evidence:**
  ```java
  pipelineRepository.update(pipeline);
  for (Stage oldStage : currentStages) {
      if (!newIds.contains(oldStage.getId())) {
          pipelineRepository.deleteStage(oldStage.getId());
      }
  }
  for (Stage s : stages) {
      pipelineRepository.saveStage(s);
  }
  ```
- **Actual Root Cause:**
  `PipelineService.updatePipeline` executed the pipeline record update, individual stage deletions, and individual stage saves across multiple separate database connections without a unifying transaction. If an individual stage save failed, the pipeline and preceding stages were committed, while deleted stages were lost, leaving corrupted partial state. Furthermore, `saveStage(s)` suffered from the same single-pass forward-dependency vulnerability as `FINDING-FK-01`.
- **Smallest Viable Correction:**
  Introduce `pipelineRepository.updateWithStages(Pipeline pipeline, List<Stage> stages)` that executes the metadata update, deleted stage removal, and two-pass stage & dependency persistence inside a single atomic `Connection` transaction with `commit()` and `rollback()`.
- **Verification Tests:** `PipelineRepositoryDependencyIntegrityTest.testAtomicPipelineUpdateWithAddedAndRemovedStages()`

---

### FINDING-UI-01: Dangling Stage Dependencies Retained Upon Stage Removal in Pipeline Builder

- **Finding ID:** `FINDING-UI-01`
- **Severity:** P2 — Medium (UI state inconsistency leading to validation or FK errors)
- **Confidence:** 100%
- **Affected Files & Line Ranges:**
  - `src/main/java/com/devopshub/ui/view/PipelineBuilderView.java:650–663`
- **Reproducible Evidence:**
  In `removeSelectedStage()`:
  ```java
  int idx = stageList.indexOf(selected);
  if (idx >= 0) {
      stageList.remove(idx);
      ...
  }
  ```
  When Stage B (depended upon by Stage C) was removed from `stageList`, Stage C continued to hold `selected.getId()` in its `dependsOnStageIds` list.
- **Actual Root Cause:**
  The stage deletion routine did not prune references to the deleted stage ID from other stages remaining in `stageList`. When the user subsequently attempted to save or validate, the dangling UUID triggered validation failures or FK constraint errors.
- **Smallest Viable Correction:**
  Upon removing `selected`, iterate over all remaining stages in `stageList` and remove `selected.getId()` from their `dependsOnStageIds`, then refresh `updateDependenciesChecklist()` and visual flow.
- **Verification Tests:** Manual UI inspection and `PipelineRepositoryDependencyIntegrityTest.testDanglingDependencyGracefullyHandled()`

---

### FINDING-DUP-01: Pipeline Duplication Discards DAG Stage Dependencies

- **Finding ID:** `FINDING-DUP-01`
- **Severity:** P2 — Medium (Silent data loss during pipeline duplication)
- **Confidence:** 100%
- **Affected Files & Line Ranges:**
  - `src/main/java/com/devopshub/repository/PipelineRepository.java:178–214`
- **Reproducible Evidence:**
  ```java
  for (Stage s : source.getStages()) {
      Stage stageCopy = new Stage(UUID.randomUUID().toString(), newId, ...);
      newStages.add(stageCopy);
  }
  ```
  `stageCopy.getDependsOnStageIds()` was left empty (0 dependencies).
- **Actual Root Cause:**
  `duplicate` created new UUIDs for each cloned stage, but never built an `oldToNewStageId` translation map to remap prerequisite dependencies. As a result, cloned pipelines lost their multi-stage DAG execution structure and reverted to independent or sequential execution without dependencies.
- **Smallest Viable Correction:**
  Track `Map<String, String> oldToNewStageId`. In a second pass, remap each source stage's `dependsOnStageIds` to the corresponding newly generated stage IDs.
- **Verification Tests:** `PipelineRepositoryDependencyIntegrityTest.testDuplicationPreservesDAGTopology()`

---

### FINDING-DB-01: Inconsistent & Misleading Database Status Labels in UI

- **Finding ID:** `FINDING-DB-01`
- **Severity:** P2 — Medium (User confusion, false alarm regarding data loss)
- **Confidence:** 100%
- **Affected Files & Line Ranges:**
  - `src/main/java/com/devopshub/ui/view/MainLayoutView.java:103–112`, `220–230`
  - `src/main/java/com/devopshub/ui/view/HomeView.java:62–66`, `169–174`
  - `src/main/java/com/devopshub/ui/view/SettingsView.java:51–71`
- **Reproducible Evidence:**
  - `HomeView:63` displayed `Label dbTag = new Label(isH2 ? "H2 In-Memory Active" : "MySQL 8.0 Connected");`
  - `MainLayoutView:222` displayed `Label dbLabel = new Label(isH2 ? "H2 Fallback (Ephemeral)" : "MySQL 8.0 Live");` with tooltip `"Warning: Running in ephemeral in-memory H2 fallback mode. Data will NOT persist after application restart."`
  - In `MainLayoutView:110`, the header pill checked `isMem` and displayed `"H2 Persistent"`, directly contradicting line 222 which displayed `"H2 Fallback (Ephemeral)"`.
- **Actual Root Cause:**
  When Docker Desktop was stopped, `DatabaseManager` fell back to the durable disk-backed H2 file (`./data/devopshub.mv.db`), but several UI views hardcoded strings indicating that H2 is always in-memory and ephemeral. This led the user to believe their database was wiped or lost on every restart.
- **Smallest Viable Correction:**
  Standardize status indicators across all views using `DatabaseManager.getInstance().isH2Fallback()` and `DatabaseManager.getInstance().isH2InMemory()`. Accurately describe disk-backed H2 as `"H2 Disk Fallback (Persistent: ./data/devopshub)"` and MySQL as `"MySQL 8.0 Live (Primary Persistent)"`.
- **Verification Tests:** Verified via `DatabaseIntegrationTest` and UI component inspections.

---

### FINDING-TST-01: Inability to Force Isolated Test Database via CLI / Surefire

- **Finding ID:** `FINDING-TST-01`
- **Severity:** P2 — Medium (Risk of test pollution on live MySQL database)
- **Confidence:** 100%
- **Affected Files & Line Ranges:**
  - `src/main/java/com/devopshub/config/AppConfig.java:23–27`
  - `src/main/java/com/devopshub/config/DatabaseManager.java:37–65`
- **Reproducible Evidence:**
  Running `mvn test -DFORCE_H2_MODE=true` still connected to MySQL because `AppConfig` only read `.env` and `System.getenv()`, ignoring JVM system properties (`System.getProperties()`), and `DatabaseManager` unconditionally attempted MySQL first.
- **Actual Root Cause:**
  Lack of system property overrides prevented disposable unit tests from enforcing `FORCE_H2_MODE=true` on the command line without modifying the developer's `.env` file.
- **Smallest Viable Correction:**
  1. Add `loadSystemProperties()` to `AppConfig`.
  2. In `DatabaseManager`, check `if ("true".equalsIgnoreCase(config.get("FORCE_H2_MODE", "false")))` before attempting MySQL, routing directly to isolated H2 when requested.
- **Verification Tests:** Verified via `mvn test -Dtest=PipelineRepositoryDependencyIntegrityTest -DFORCE_H2_MODE=true -DH2_IN_MEMORY_ONLY=true` (connected to `jdbc:h2:mem:devopshub` without touching MySQL).

---

### FINDING-MGR-01: Dual Migration Directories Drift Hazard

- **Finding ID:** `FINDING-MGR-01`
- **Severity:** P3 — Low (Maintenance / operational cleanliness)
- **Confidence:** 100%
- **Affected Files:**
  - `database/migrations/` (repository root)
  - `src/main/resources/db/migrations/` (classpath root)
- **Analysis:**
  `DatabaseManager` loads migration scripts strictly from classpath (`/db/migrations/...`). The scripts in `database/migrations/` are documentation / external copies. `diff -r` confirms both directories are currently 100% identical. However, developers editing only `database/migrations/` without updating `src/main/resources/db/migrations/` risk schema drift.
- **Recommendation:** Maintain classpath directory as authoritative; documented in baseline.
