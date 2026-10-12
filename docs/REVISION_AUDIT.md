# DevOpsHub Forensic Audit & Revision Baseline

## 1. Executive Summary & Audit Context
This forensic audit was conducted on October 12, 2026, across the entire DevOpsHub repository (`javacicd`) for course **CSE1007 — Java Programming**.

The current application launches cleanly and passes baseline compilation and tests. However, the user experience is disproportionately centered around the dashboard, while other modules lack interactive depth, detailed drill-down views, comprehensive filters, visual DAG flowcharts, and complete CRUD workflows.

This audit catalogues all findings across all 10 modules, identifies incomplete and defective features, and defines the prioritized remediation plan.

---

## 2. Module-by-Module Forensic Audit

### Module 1: Dashboard (`DashboardView.java`)
- **Status:** *Partially Implemented & Functional, Needs Visual Polish*
- **Audit Findings:**
  - KPI cards display live data from MySQL, but lack time-range context (e.g., "All-time" vs "Last 7 days").
  - Recent executions table only shows basic columns; clicking rows doesn't show inline quick-stats.
  - Recent projects table is static list without direct links to project details.
  - Missing visual failure trend sparkline or mini-status breakdown.
- **Remediation Plan:** Add time-range filter, clickable rows leading to full details, and empty-state handling for fresh databases.

---

### Module 2: Project Management (`ProjectsView.java`)
- **Status:** *Partially Implemented — Major Gaps in Details & Filtering*
- **Audit Findings:**
  - **Missing Project Details View:** Clicking a project row does not open a dedicated Project Details page. Users cannot see associated pipelines, recent executions for that project, or repository branch info.
  - Filter by project type or status is missing (only text search exists).
  - Create/Edit dialog lacks real-time inline field validation styling (e.g., red border on invalid Git URL or empty name).
  - Empty state displays a generic empty table rather than a guided call-to-action card.
- **Remediation Plan:**
  - Build `ProjectDetailView` with associated pipeline cards and historical executions table.
  - Add Project Type and Status filter dropdowns in `ProjectsView`.
  - Add inline validation feedback and empty-state components.

---

### Module 3: Pipeline Management (`PipelinesView.java`)
- **Status:** *Partially Implemented — Missing Details, Search, & Duplicate Action*
- **Audit Findings:**
  - No "Create Pipeline" button in the table toolbar (relies solely on navigation bar).
  - No search bar or project filter dropdown in `PipelinesView`.
  - Missing "Duplicate Pipeline" action to clone existing pipelines safely.
  - Missing `PipelineDetailView` to inspect stages and execution history for a single pipeline.
- **Remediation Plan:**
  - Add search bar and project filter combo box.
  - Implement pipeline duplication in `PipelineService` and `PipelineRepository`.
  - Build dedicated `PipelineDetailView`.

---

### Module 4: Visual Pipeline Builder (`PipelineBuilderView.java`)
- **Status:** *Defective & Incomplete — Missing Dependency DAG UI & Visual Graph*
- **Audit Findings:**
  - **No Dependency Configuration:** Stages are ordered sequentially in a `ListView`, but users cannot configure `stage_dependencies` (which stages must finish before this stage runs).
  - **No Visual Flow Graph:** There is no visual flowchart or diagram of sequential vs parallel stage branches.
  - Validation errors only show via floating toasts or alerts instead of inline under the offending field.
  - No "Run Now" button after saving a pipeline.
- **Remediation Plan:**
  - Build a visual Stage Flow Graph component with interactive stage boxes and connection arrows.
  - Add a Stage Dependencies multi-select / checklist to configure DAG prerequisites.
  - Add inline validation indicators and post-save "Run Now" action.

---

### Module 5: Execution Engine (`ExecutionEngine.java` & Subprocess Execution)
- **Status:** *Working and Verified, Minor UI Sync Issue*
- **Audit Findings:**
  - Real subprocess execution works, timeouts destroy processes, and exit codes are recorded accurately.
  - Thread pools are bounded and safe.
  - When a live execution completes, the JavaFX view doesn't always automatically refresh its top-level status badge and render the diagnosis card without manual user refresh.
- **Remediation Plan:**
  - Add an `ExecutionCompletionListener` callback on `ExecutionEngine` to notify the UI when the top-level execution transitions to `SUCCESS` or `FAILED`.

---

### Module 6: Execution History & Details (`ExecutionHistoryView.java`, `ExecutionDetailView.java`)
- **Status:** *Partially Implemented*
- **Audit Findings:**
  - `ExecutionHistoryView` only queries `findRecent(50)` without project or pipeline filters, and without search by build number.
  - In `ExecutionDetailView`, clicking on an individual stage card does not filter the logs to display only that stage's output.
  - Missing breadcrumb or "Back to Executions" navigation button.
  - Missing stage duration distribution bar chart or timeline.
- **Remediation Plan:**
  - Add search field (build # / commit) and project/pipeline filters in `ExecutionHistoryView`.
  - Make stage cards in `ExecutionDetailView` selectable to filter terminal logs to the selected stage.
  - Add "Back to Executions" button and auto-refresh on execution finish.

---

### Module 7: Log Management (`LogViewerView.java`)
- **Status:** *Partially Implemented — Missing Stage Filter, Auto-Scroll, & Export*
- **Audit Findings:**
  - Cannot filter logs by individual pipeline stage (only by execution).
  - Lacks an Auto-Scroll toggle or pause/resume live-follow option.
  - Lacks an "Export Logs to File" button.
  - Text is raw unformatted string in a text area rather than timestamped structured log lines.
- **Remediation Plan:**
  - Add Stage selection dropdown.
  - Add Auto-Scroll toggle and Export to File (`.log`) action.
  - Add colorized stdout/stderr formatting.

---

### Module 8: Failure Analysis (`FailureRuleEngine.java`)
- **Status:** *Working and Verified*
- **Audit Findings:**
  - All 6 diagnostic rules match expected signatures and persist to `failure_diagnoses`.
  - UI renders the diagnosis card cleanly with likely cause and recommended action.
  - Needs a "Copy Recommendation" button for developer convenience.
- **Remediation Plan:** Add a "Copy Suggested Action" button in the diagnosis card.

---

### Module 9: Analytics (`AnalyticsView.java`)
- **Status:** *Partially Implemented — Missing Date Filters & Ranking*
- **Audit Findings:**
  - Displays PieChart (Status) and BarChart (Failure Categories), but has no time-range filter (7d, 30d, All Time).
  - Missing "Most Frequently Failing Pipelines" ranking table.
  - Missing project reliability comparison matrix.
- **Remediation Plan:**
  - Add time-range filter selector.
  - Add Failure Ranking table and project reliability breakdown.

---

### Module 10: Settings & Diagnostics (`SettingsView.java`)
- **Status:** *Partially Implemented — Read-Only Controls*
- **Audit Findings:**
  - Database settings and timeouts are read-only.
  - Test Connection button works, but doesn't report ping latency in milliseconds.
  - Missing configurable defaults (default stage timeout, log buffer size) that persist to application config.
- **Remediation Plan:**
  - Add interactive connection ping with millisecond round-trip measurement.
  - Allow saving configurable defaults.

---

## 3. Prioritized Defect & Enhancement Backlog

| Priority | Defect / Enhancement ID | Module | Description | Severity |
|---|---|---|---|---|
| **P0** | `DEF-STAGE-DEP-01` | Pipeline Builder | Implement Stage Dependency selection (DAG prerequisites) in builder | **Critical** |
| **P0** | `DEF-PROJ-DETAIL-01`| Projects | Create `ProjectDetailView` showing associated pipelines & execution history | **High** |
| **P0** | `DEF-PIPE-DETAIL-01`| Pipelines | Create `PipelineDetailView` with stage list, runs, and duplication action | **High** |
| **P1** | `DEF-DAG-FLOW-01`   | Pipeline Builder | Implement Visual Stage Flow graph/diagram showing sequential & parallel paths | **High** |
| **P1** | `DEF-EXEC-SYNC-01`  | Execution Detail | Auto-refresh stage statuses and diagnosis when background execution finishes | **High** |
| **P1** | `DEF-STAGE-LOG-01`  | Execution Detail | Make stage cards clickable to filter logs to the selected stage | **Medium** |
| **P1** | `DEF-LOG-EXPORT-01` | Log Viewer | Add Export to File (`.log`), Auto-scroll toggle, and stage filter | **Medium** |
| **P2** | `DEF-HIST-FILTER-01`| Execution History| Add Project and Pipeline filters, search by build number | **Medium** |
| **P2** | `DEF-ANALYTICS-01`  | Analytics | Add Most Frequently Failing Pipelines table and time-range filtering | **Medium** |
| **P2** | `DEF-SETTINGS-01`   | Settings | Add ping latency display and configurable stage defaults | **Low** |
| **P2** | `DEF-UI-TOKENS-01`  | Styling / CSS | Refine CSS tokens, card shadows, responsive padding, and empty states | **Medium** |
