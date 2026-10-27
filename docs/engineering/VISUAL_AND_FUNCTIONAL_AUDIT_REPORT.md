# DevOpsHub — Visual & Functional Acceptance Audit Report

**Audit Date:** 2026-10-24  
**Auditor:** Principal Autonomous Systems, JavaFX & Release Engineer  
**Execution Environment:** macOS 26.6.2 (Darwin arm64) | Java SE 25.0.1 LTS | MySQL 8.0 (Docker localhost:3306)  
**Distribution Artifact:** `target/devopshub-desktop-1.0.0-jar-with-dependencies.jar` (16.9 MB)  
**Launch Command:** `java -cp "target/test-classes:target/devopshub-desktop-1.0.0-jar-with-dependencies.jar" com.devopshub.ui.VisualAuditLauncher`  

---

## 1. Visual Audit Screen Register & Evidence

| # | Screen / View | Captured Evidence File | Live Verification Findings | Visual Status |
| :-: | :--- | :--- | :--- | :-: |
| **1** | **Home View** | `docs/audit/screenshots/01_home_view.png` | Hero banner with dark glassmorphic card, system environment diagnostics (MySQL 8.0, 1ms latency, 4 concurrent pipelines, Java 25.0.1 LTS, `./workspaces`), quick action cards, recent projects list with toggle switches. | **PASS** |
| **2** | **Dashboard View** | `docs/audit/screenshots/02_dashboard_view.png` | Live MySQL metrics: 240 projects, 455 pipelines, 1.0s average duration; build operations summary; recent execution audit table (`#1 Cancel`, `#1 Timeout`, `#1 Failure`, `#1 Success`) with color-coded status badges; recent projects side rail. | **PASS** |
| **3** | **Projects View** | `docs/audit/screenshots/03_projects_view.png` | Grid layout of project cards with status badges (`ACTIVE`), author avatar badges, search filtering, import modal trigger, delete action, and responsive grid/list switcher. | **PASS** |
| **4** | **Pipelines View** | `docs/audit/screenshots/04_pipelines_view.png` | Pipeline cards with trigger types, execution modes (`SEQUENTIAL`), active toggle switches, run buttons, and project bindings. | **PASS** |
| **5** | **Pipeline Builder View** | `docs/audit/screenshots/05_pipeline_builder_view.png` | Interactive Directed Acyclic Graph (DAG) canvas rendering connected stage pills (`Checkout Source` -> `Compile App` -> `Unit Tests` / `Security Scan` -> `Package Artifact` -> `Deploy`) with bezier curves, stage hierarchy list, and configuration editor. | **PASS** |
| **6** | **Execution History View** | `docs/audit/screenshots/06_execution_history_view.png` | Kanban board with status columns (`User story`, `In progress`, `Completed`), execution cards with run durations, commit hashes, and diagnostic inspect actions. | **PASS** |
| **7** | **Log Viewer View** | `docs/audit/screenshots/07_log_viewer_view.png` | Live terminal emulator with dark theme, millisecond timestamps, build execution dropdown selector, stage stream filter (`ALL`, `STDOUT`, `STDERR`), and autoscroll toggle. | **PASS** |
| **8** | **Analytics View** | `docs/audit/screenshots/08_analytics_view.png` | Live charts computed directly from MySQL database: execution outcome distribution PieChart (`Success: 1`, `Failed: 3`), failure category BarChart, failure hotspot rankings, and operational recommendations. | **PASS** |
| **9** | **Settings View** | `docs/audit/screenshots/09_settings_view.png` | Database engine connectivity indicator (`Primary MySQL 8.0 Server`), JDBC connection test button, execution concurrency controls (pool size, workspace directory), Webhook token management, and platform diagnostics. | **PASS** |

---

## 2. Visual & Interaction Defect Register

### DEF-UI-001: Header Date Badge Truncation
- **Screen:** `MainLayoutView` top header bar.
- **Severity:** Low / Visual.
- **Symptom:** The date badge displayed `24.1...` instead of `24.10.2026`.
- **Root Cause:** Unconstrained `header-search-field` with fixed `pref-width: 260px` squeezed the date badge label below its natural minimum width.
- **Fix Applied:** 
  1. Added `dateBadge.setMinWidth(Region.USE_PREF_SIZE);` in `MainLayoutView.java`.
  2. Adjusted `.header-search-field` in `devopshub.css` to `-fx-pref-width: 180px; -fx-min-width: 120px;`.
- **Verification Evidence:** `02_dashboard_view.png` displays `24.10.2026` completely without truncation.
- **Status:** **VERIFIED_FIXED**

---

### DEF-UI-002: Database Header Pill Truncation
- **Screen:** `MainLayoutView` top header bar.
- **Severity:** Low / Visual.
- **Symptom:** The database pill displayed `MySQL Con...` instead of `MySQL Connected`.
- **Root Cause:** When the right alerts sidebar was expanded, horizontal space compression truncated the header pill text.
- **Fix Applied:** Added `dbHeaderPill.setMinWidth(Region.USE_PREF_SIZE);` in `MainLayoutView.java`.
- **Verification Evidence:** `02_dashboard_view.png` displays `MySQL Connected` fully with the green status indicator.
- **Status:** **VERIFIED_FIXED**

---

### DEF-PKG-001: Standard Thin JAR Missing Application Dependencies
- **Screen / Workflow:** Application Packaging & Independent Launch.
- **Severity:** Critical / Blocker.
- **Symptom:** Executing `java -jar target/devopshub-desktop-1.0.0.jar` failed with `ClassNotFoundException: javafx.application.Application`.
- **Root Cause:** Standard Maven `jar-plugin` only packages project class files (346 KB) without bundling JavaFX or transitive libraries.
- **Fix Applied:** Configured `maven-assembly-plugin` with `jar-with-dependencies` producing a 16.9 MB standalone fat JAR, coupled with `Launcher.java` non-Application entry point.
- **Verification Evidence:** `java -jar target/devopshub-desktop-1.0.0-jar-with-dependencies.jar` launches cleanly on macOS ARM64 and boots MySQL HikariCP, Webhook server, and the JavaFX GUI.
- **Status:** **VERIFIED_FIXED**

---

### DEF-PERS-001: Transaction Rollback on Multi-Step Write Failure
- **Screen / Workflow:** Pipeline Persistence & Stage Dependency Association.
- **Severity:** High / Data Consistency.
- **Symptom:** Early versions lacked automated tests verifying that a failure during stage insertion rolls back the parent pipeline record.
- **Fix Applied:** Added `testTransactionRollbackOnInducedFailure()` in `DatabaseIntegrationTest.java` verifying atomic rollback and zero orphaned rows.
- **Verification Evidence:** `DatabaseIntegrationTest` passed (10/10).
- **Status:** **VERIFIED_FIXED**

---

### DEF-SEC-001: Secret Credential Scrubbing from Process Logs
- **Screen / Workflow:** Git Repository Cloning with Embedded Credentials.
- **Severity:** High / Security.
- **Symptom:** Risk of sensitive tokens in URLs (`https://oauth2:token@...`) appearing in console output or database logs.
- **Fix Applied:** Integrated `SecretMasker.mask()` across `GitService` and `ProcessExecutor`, replacing basic auth credentials with `***`.
- **Verification Evidence:** `testCredentialScrubbing()` in `GitServiceTest.java` verifies that secrets never appear in command strings or stderr.
- **Status:** **VERIFIED_FIXED**
