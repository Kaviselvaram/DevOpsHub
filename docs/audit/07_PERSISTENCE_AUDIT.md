# DevOpsHub — Database, Persistence & History Audit

**Audit Date:** 2026-10-23  
**Auditor:** Database Reliability Engineer, Senior Java Engineer  

---

## 1. Relational Schema & Migration Architecture

DevOpsHub uses a normalized relational schema on MySQL 8.0 with InnoDB engine and `utf8mb4_unicode_ci` character encoding. Database migrations are managed via version-tracked SQL migrations in `db/migrations/`:
- `V1__initial_schema.sql`: Normalizes 8 core tables:
  1. `projects`: Project registry, repository URLs, default branches, project types.
  2. `pipelines`: Pipeline definitions, execution modes, activation flags.
  3. `pipeline_stages`: Configured stages, execution commands, timeouts, stage ordering.
  4. `stage_dependencies`: Directed acyclic graph (DAG) dependency mappings between stages.
  5. `executions`: Individual pipeline runs, trigger types, final statuses, durations.
  6. `stage_executions`: Per-stage run instances, exit codes, errors, execution durations.
  7. `execution_logs`: Structured logs (stdout/stderr streams, log levels, timestamps).
  8. `failure_diagnoses`: Rule-based failure intelligence findings and recommended actions.
- `V3__reference_seed_projects.sql`: Preserves default reference projects without overwriting user data.

---

## 2. Referential Integrity & Cascading Deletions

All child tables enforce foreign keys with explicit cascading semantics:
- `pipelines.project_id` -> `projects(id) ON DELETE CASCADE`
- `pipeline_stages.pipeline_id` -> `pipelines(id) ON DELETE CASCADE`
- `stage_dependencies.stage_id` -> `pipeline_stages(id) ON DELETE CASCADE`
- `executions.pipeline_id` -> `pipelines(id) ON DELETE CASCADE`
- `executions.project_id` -> `projects(id) ON DELETE CASCADE`
- `stage_executions.execution_id` -> `executions(id) ON DELETE CASCADE`
- `execution_logs.execution_id` -> `executions(id) ON DELETE CASCADE`
- `failure_diagnoses.execution_id` -> `executions(id) ON DELETE CASCADE`

**Integrity Verification:** Deleting a project or pipeline cleanly removes all associated executions, logs, and diagnoses in an atomic transaction without leaving orphaned foreign key references.

---

## 3. Connection Pooling & Resource Safety

- **Connection Pool:** Managed by `HikariCP 5.1.0` in `com.devopshub.config.DatabaseManager`.
- **Pool Size:** Configured to 10 maximum connections (`DB_POOL_SIZE=10`).
- **Connection Leak Protection:** All repository methods strictly utilize Java `try-with-resources` statements for `Connection`, `PreparedStatement`, and `ResultSet`, ensuring connections return to the pool immediately upon completion.
- **Latency Monitoring:** `DatabaseManager.pingLatencyMillis()` executes `SELECT 1` to measure live round-trip database latency (typically 1–3 ms on local Docker).
- **Graceful Shutdown:** `MainApp.stop()` triggers `DatabaseManager.getInstance().shutdown()`, ensuring all pooled connections and pending statements close gracefully during application exit.

---

## 4. History Durability & Restart Verification

- **Durable Persistence:** All pipeline definitions, runs, logs, and diagnoses reside in persistent MySQL tables, NOT temporary in-memory collections.
- **Restart Verification:** Executions and stages created before restart remain completely intact and queryable upon reopening DevOpsHub.
- **Test Isolation:** As verified in `DatabaseIntegrationTest.java`, integration tests now execute within `try-finally` blocks that delete test fixtures (`executionRepository.delete(execId)`), preventing fake or unit-test executions from polluting the live user dashboard.
