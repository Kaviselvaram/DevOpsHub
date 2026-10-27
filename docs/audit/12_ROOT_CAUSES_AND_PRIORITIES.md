# DevOpsHub — Root Causes & Prioritized Defect Register

**Audit Date:** 2026-10-23  
**Auditor:** Principal Software Architect, Release Readiness Assessor  

---

## 1. Severity Definitions

- **P0 — Critical Release Blocker:** Data loss, security vulnerability, unintended destruction, or inability to launch.
- **P1 — Core Functionality Release Blocker:** Pipeline execution simulated or broken, repository access failure, incorrect success status concealing failure, essential persistence broken, core workflow incomplete.
- **P2 — Major Defect:** Significant UI interaction/scrolling lockup, unreliable timeout/cancellation, major integration failure, ordinary workflow interruption.
- **P3 — Minor Defect:** Cosmetic inconsistency, minor layout flaw, low-impact usability enhancement.

---

## 2. Comprehensive Defect Register & Root-Cause Analyses

### Finding ID: `DEF-001`
- **Severity:** **P1**
- **Classification:** **CONFIRMED & FIXED**
- **Affected Feature:** `ProjectDetailView`, `ExecutionDetailView`, `MainLayoutView` (Vertical Scrolling)
- **Symptom:** Two-finger vertical scrolling on macOS trackpads locked up or froze completely when displaying Git repository URLs or build artifact paths.
- **Root Cause:**
  - JavaFX `Label` default `wrapText=false` and `minWidth=USE_COMPUTED_SIZE` forced card widths to 900–1600px.
  - Inside `HBox` inside `ScrollPane(fitToWidth=true)`, `ScrollPaneSkin` absorbed macOS composite diagonal trackpad events into the clamped horizontal axis without advancing vertical scroll values.
- **User Impact:** Users could not scroll down to inspect pipeline stages, view recent runs, or access bottom action buttons.
- **Release Impact:** Core monitoring workflows blocked on displays narrower than 1600px.
- **Reproduction Steps:** Open a project with a long GitHub URL; attempt to scroll down.
- **Recommended Repair:** Configure `label.setWrapText(true)`, `label.setMinWidth(0)`, `container.setMinWidth(0)`, and `HBox.setHgrow(container, Priority.ALWAYS)`. Set `ScrollPane` scrollbar policy to `AS_NEEDED`.
- **Regression Test:** Verified in manual launch and `ModalSnapshotTest`.

---

### Finding ID: `DEF-002`
- **Severity:** **P1**
- **Classification:** **CONFIRMED & FIXED**
- **Affected Feature:** `ProjectsView.showProjectModal()` ("Create Project" Modal Dialog)
- **Symptom:** Bottom action buttons ("Cancel" and "Create Project") were partially or completely clipped by the bottom window border.
- **Root Cause:** Modal stage was instantiated with fixed height of `520px`, `resizable=false`, and no `ScrollPane`. The total vertical height of inputs and labels exceeded 580px, causing parent clipping.
- **User Impact:** Users on standard resolutions could not see or click "Create Project".
- **Release Impact:** Blocked project creation workflow from UI.
- **Reproduction Steps:** Click `+ Create` in `ProjectsView`; observe bottom border.
- **Recommended Repair:** Enclose the modal form in a `ScrollPane(fitToWidth=true)`, increase initial scene dimensions to `520x620`, and enable `modal.setResizable(true)`.
- **Regression Test:** Verified via `ModalSnapshotTest.testCreateProjectModalDimensions()`.

---

### Finding ID: `DEF-003`
- **Severity:** **P2**
- **Classification:** **CONFIRMED & FIXED**
- **Affected Feature:** `PipelinesView` Table Header Styling
- **Symptom:** A bright white rectangular block appeared in the unassigned space to the right of the "Actions" column in the pipelines table.
- **Root Cause:** Modena default theme styled `.table-view .filler` and `.table-view .corner` as light gray; `devopshub.css` lacked styling rules for the filler area.
- **User Impact:** Jarring visual artifact contrasting sharply with the `#0F1017` dark theme.
- **Release Impact:** Professional polish failure.
- **Reproduction Steps:** Navigate to `Pipelines` view on a wide monitor.
- **Recommended Repair:** Add `.table-view .filler, .table-view .corner { -fx-background-color: #1A1B29; }` in `devopshub.css`.
- **Regression Test:** Visual inspection in desktop launch.

---

### Finding ID: `DEF-004`
- **Severity:** **P2**
- **Classification:** **CONFIRMED & FIXED**
- **Affected Feature:** `PipelineBuilderView` (Simulated Metrics & Static Redesign Title)
- **Symptom:** Header displayed hardcoded `"SOS Computer redesign"` and middle section rendered static mock cards: `"Build & Compile Team - 32 issues, 14 alerts"`, `"14 hours 5 persons 117 tasks"`.
- **Root Cause:** Presentation mockup text was left in production view code. The cards consumed 150px of vertical space, pushing the stage composer and Save/Cancel buttons below the fold.
- **User Impact:** Misleading fake issue counts presented to users; cluttered UI pushed action controls off-screen.
- **Release Impact:** Violated core CI/CD reality requirements (Rule 4 & 5).
- **Reproduction Steps:** Navigate to `Map (Builder)` screen.
- **Recommended Repair:** Replace static title with dynamic pipeline name, bind subtitle to selected project, and remove fake team metric cards. Wire `searchField` to filter stages in real time.
- **Regression Test:** `PipelineBuilderView` refactoring verified.

---

### Finding ID: `DEF-005`
- **Severity:** **P2**
- **Classification:** **CONFIRMED & FIXED**
- **Affected Feature:** `ProjectsView` Toolbar Action Buttons
- **Symptom:** Buttons in the top-right toolbar were compressed to `+ Cre...`, `Im...`, `Rem...`.
- **Root Cause:** Buttons lacked `setMinWidth(Region.USE_PREF_SIZE)`. Inside the `HBox` toolbar, sibling components compressed them when window width was constrained.
- **User Impact:** Truncated button labels made primary actions difficult to read.
- **Release Impact:** Usability defect.
- **Reproduction Steps:** Resize window or observe toolbar on standard resolution display.
- **Recommended Repair:** Set `setMinWidth(Region.USE_PREF_SIZE)` on `createBtn`, `importBtn`, and `removeBtn`.
- **Regression Test:** Visual inspection in desktop launch.

---

### Finding ID: `DEF-006`
- **Severity:** **P2**
- **Classification:** **CONFIRMED & FIXED**
- **Affected Feature:** `DatabaseIntegrationTest` / Live Executions Dashboard
- **Symptom:** Test execution `#999` with dummy log lines appeared in the live UI execution details screen.
- **Root Cause:** `DatabaseIntegrationTest.testLogPersistence()` created an execution with `status='RUNNING'`. Because history purge only removes completed statuses (`SUCCESS`, `FAILED`, `CANCELLED`), the test record remained in the shared MySQL database.
- **User Impact:** Confused users with fake `#999` run records in production screens.
- **Release Impact:** Contaminated production state with test fixtures.
- **Reproduction Steps:** Run `mvn test` then launch UI.
- **Recommended Repair:** Enclose test execution saves in `try-finally` blocks that invoke `executionRepository.delete(execId)`.
- **Regression Test:** Verified MySQL query `SELECT * FROM executions WHERE execution_number=999` returns empty.
