# DevOpsHub — Full UI & Interaction Audit

**Audit Date:** 2026-10-23  
**Auditor:** Principal UI/UX Architect, Senior JavaFX Engineer, QA Automation Lead  

---

## 1. Complete Screen & Component Inventory

DevOpsHub features 14 dedicated view classes in `com.devopshub.ui.view.*`:

1. **`MainLayoutView`:** Root window container (`1280x800`). Contains persistent dark left navigation sidebar, top system status bar (with MySQL live pill and notification bell), and main scrollable content area wrapped in a JavaFX `ScrollPane(fitToWidth=true)`.
2. **`HomeView`:** Metric overview cards (Active Projects, Success Rate, Average Duration), recent pipeline activity, and project quick-launch cards.
3. **`DashboardView`:** Real-time CI/CD metrics, weekly run volume histograms, success/failure donut visualizer, and live run status counters.
4. **`ProjectsView`:** Grid of project cards, filter dropdowns (by type/status), search field, toolbar buttons (`+ Create`, `Import`, `Remove`), and modal creation dialog.
5. **`ProjectDetailView`:** Selected project configuration, wrapped Git repository URL with copy button, branch badge, and filtered executions table.
6. **`PipelinesView`:** Data table of all configured delivery pipelines, execution modes (`SEQUENTIAL` / `PARALLEL_FORK`), stage counts, last status pills, and action buttons (`Run`, `View`, `Edit`, `Copy`, `Delete`).
7. **`PipelineDetailView`:** Stage breakdown, execution history, configuration overview, and "Run Pipeline" trigger button.
8. **`PipelineBuilderView`:** Interactive DAG topology map canvas with curved stage connectors, live stage search filter, reorder controls (`Move Up`, `Move Down`), and stage command parameter forms.
9. **`ExecutionHistoryView`:** Global execution audit log, filterable by project, pipeline, status, and date range.
10. **`ExecutionDetailView`:** Real-time build monitor, stage status cards, failure intelligence diagnosis card (with rule match and recovery recommendation), and live console log viewer with stage filtering.
11. **`LogViewerView`:** Full-screen execution log viewer with level filtering (`INFO`, `WARN`, `ERROR`), stream filtering (`STDOUT`, `STDERR`), search highlighting, and clipboard copy.
12. **`AnalyticsView`:** Historical trends, stage bottleneck analysis, failure categorization breakdown, and MTTR (mean time to recovery) metrics.
13. **`SettingsView`:** System environment configuration, database latency ping test, webhook server status, and audit history purge action.
14. **`AlertsSidebarView`:** Slide-out notification drawer presenting recent pipeline alerts, build failures, and recovery recommendations.

---

## 2. In-Depth Audit of the Long-URL Scrolling Defect

### Defect Analysis
- **Screen:** `ProjectDetailView`, `ExecutionDetailView`, `HomeView`.
- **Observed Symptom:** When displaying long URLs (e.g. `https://github.com/Kaviselvaram/lair.git`) or long artifact paths (`/Users/.../workspaces/projects/proj-001`), vertical scrolling locked up on macOS trackpads.
- **Root Cause Established:**
  1. Default JavaFX `Label` has `wrapText=false` and `minWidth=USE_COMPUTED_SIZE`. A 120-character string computes a minimum width exceeding 900px.
  2. Inside `HBox` containers, this minimum width forced the card wider than the viewport of `MainLayoutView`'s `ScrollPane(fitToWidth=true)`.
  3. On macOS trackpads, two-finger scrolling emits composite diagonal delta events (both dx and dy). Because horizontal scrolling was clamped by `fitToWidth`, the underlying `ScrollPaneSkin` absorbed the composite gesture without translating the vertical delta into vertical scrollbar movement.
- **Root-Cause Architectural Fix:**
  - Applied `label.setWrapText(true)` and `label.setMinWidth(0)` across all URL and path labels.
  - Set `container.setMinWidth(0)` and `HBox.setHgrow(container, Priority.ALWAYS)`.
  - Configured `scroll.setHbarPolicy(ScrollBarPolicy.AS_NEEDED)` and `scroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED)`.
  - Added an interactive "Copy Repository URL" button with clipboard integration.
- **Verification:** Verified in desktop launch. Project and execution detail screens now scroll smoothly from top to bottom with long Git URLs and deep workspace paths.

---

## 3. Form, Modal & Layout Defect Audit

| Defect | Affected View | Root Cause | Implemented Repair | Verification Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **Toolbar Button Truncation (`+ Cre...`)** | `ProjectsView.java:97-110` | Buttons lacked `setMinWidth(Region.USE_PREF_SIZE)`; compressed by `HBox` | Added `setMinWidth(Region.USE_PREF_SIZE)` | Full button text "+ Create", "Import", "Remove" visible on all resolutions |
| **Modal Dialog Button Clipping** | `ProjectsView.java:574-706` | Stage height fixed at `520px` without `ScrollPane`; bottom buttons cut off | Wrapped modal in `ScrollPane`, set size `520x620`, enabled resizable | Cancel and Create buttons 100% visible and clickable |
| **Blank White Rectangle in Table Header** | `PipelinesView.java` / `devopshub.css` | Modena default stylesheet styled unassigned `.filler` light gray | Added dark background `#1A1B29` for `.filler` and `.corner` in CSS | Table header has uniform dark styling across full table width |
| **Simulated Metrics in Pipeline Builder** | `PipelineBuilderView.java:91, 335` | Hardcoded title `"SOS Computer redesign"` and fake team cards | Set dynamic title, removed fake cards, wired stage search filter | Clean DAG composer; Save and Run buttons immediately accessible |

---

## 4. UI Threading & Background Responsiveness

- **Non-Blocking Rule:** No network I/O, Git cloning, process execution, or database queries run on the JavaFX Application Thread.
- **UI Dispatch:** Background tasks push updates to the UI exclusively via `Platform.runLater()`.
- **Log Streaming Throttling:** `LogCollector` batches console line updates so rapid compile output (e.g. hundreds of lines per second during Next.js or Maven builds) does not starve the JavaFX event loop.
