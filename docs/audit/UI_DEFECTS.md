# DevOpsHub — UI & Interaction Defects Audit

## Defect 1: Vertical Scrolling Freezes When Long URL or File Path is Displayed (P1)
- **Screen:** `ProjectDetailView`, `ExecutionDetailView`, `SettingsView`, `HomeView`.
- **Steps to Reproduce:**
  1. Open a project with a long Git repository URL (e.g. `https://github.com/Kaviselvaram/lair.git`) or an execution with a long build artifact path (e.g. `/var/folders/rf/j246g_6x58q5n9smdv0fs9zh0000gn/T/.../.next`).
  2. Attempt to scroll down using a trackpad or mouse wheel.
- **Expected Behavior:** The page smoothly scrolls vertically down to bottom cards and action controls.
- **Actual Behavior:** The page fails to scroll or scroll events lock up. The right edge of the card is clipped outside the viewport.
- **Root Cause:**
  - JavaFX `Label` default configuration has `wrapText=false` and `minWidth=USE_COMPUTED_SIZE` (which equals the full single-line string length).
  - Inside an `HBox`, the parent container's `minWidth` is forced wider than the viewport width of `MainLayoutView`'s `ScrollPane(fitToWidth=true)`.
  - In JavaFX `ScrollPaneSkin` on macOS, when content width exceeds the viewport and horizontal scrolling is disabled/clamped by `fitToWidth`, diagonal trackpad scroll events are absorbed by the horizontal handler without adjusting vertical scroll values.
- **Affected Components:**
  - `ProjectDetailView.java` (`repoLabel` in `buildHeaderCard`)
  - `ExecutionDetailView.java` (`buildMetaLabel` in `buildHeaderCard`)
  - `HomeView.java` (`repo` in project tiles)
  - `SettingsView.java` (`serverStatus` label)
  - `MainLayoutView.java` (`setView()` `ScrollPane` policies)
- **Fix Implemented:**
  - Set `label.setWrapText(true);` and `label.setMinWidth(0);` on all URL/path labels.
  - Set `infoBox.setMinWidth(0);` and `HBox.setHgrow(infoBox, Priority.ALWAYS);` so parent containers can flex down to available width.
  - Set `scroll.setHbarPolicy(ScrollBarPolicy.AS_NEEDED)` and `scroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED)` in `MainLayoutView`.

---

## Defect 2: Toolbar Action Buttons Truncated to "+ Cre...", "Im...", "Rem..." in Projects View (P2)
- **Screen:** `ProjectsView.java`
- **Steps to Reproduce:**
  1. Navigate to "Projects" screen.
  2. Observe the action buttons on the top right of the toolbar.
- **Expected Behavior:** Buttons clearly display "+ Create", "Import", "Remove".
- **Actual Behavior:** Buttons shrink horizontally, truncating text to "+ Cre...", "Im...", "Rem...".
- **Root Cause:**
  - The buttons were instantiated without setting `setMinWidth(Region.USE_PREF_SIZE)`.
  - The `HBox` toolbar contains `titleBox`, `searchField`, `datePill`, `typeFilter`, `statusFilter`, and a `spacer`. When horizontal space is tight, `HBox` compresses child buttons below their preferred width.
- **Affected Components:** `ProjectsView.java` lines 97-110.
- **Fix Implemented:** Add `createBtn.setMinWidth(Region.USE_PREF_SIZE);`, `importBtn.setMinWidth(Region.USE_PREF_SIZE);`, and `removeBtn.setMinWidth(Region.USE_PREF_SIZE);`.

---

## Defect 3: Create Project Modal Dialog Clips Bottom "Cancel" and "Create Project" Buttons (P1)
- **Screen:** `ProjectsView.java` ("Create Project" modal).
- **Steps to Reproduce:**
  1. Navigate to Projects screen and click "+ Create" button.
  2. Observe the modal dialog window.
- **Expected Behavior:** All form fields (Project Name, Description, Repository URL, Default Branch, Project Type) and both bottom buttons ("Cancel", "Create Project") are completely visible and reachable.
- **Actual Behavior:** The dialog height is too small; the bottom button bar is partially or completely clipped by the bottom window border.
- **Root Cause:**
  - `Scene scene = new Scene(form, 480, 520);` with `modal.setResizable(false);`.
  - The vertical size of the form components (title, error banner, 5 inputs with labels, and button row) with `spacing=14` and `padding=24` requires ~590px of vertical space.
  - `form` was not contained in a `ScrollPane`, meaning any height deficit permanently cut off the bottom controls.
- **Affected Components:** `ProjectsView.java` lines 574-706.
- **Fix Implemented:** Wrap the modal form in a `ScrollPane(fitToWidth=true)` and increase Scene dimensions to `500, 600`.

---

## Defect 4: Blank White Rectangle in Table Header of Pipelines View (P2)
- **Screen:** `PipelinesView.java`
- **Steps to Reproduce:**
  1. Navigate to "Pipelines" screen on a 1080p or larger display.
  2. Observe the table header row on the right side.
- **Expected Behavior:** Table header spans the full width with uniform dark styling (`#1A1B29`).
- **Actual Behavior:** The unassigned space to the right of the "Actions" column displays a bright white rectangular block.
- **Root Cause:**
  - The JavaFX TableView Modena stylesheet defaults `.table-view .filler` and `.table-view .column-header-background` to a light background color.
  - `devopshub.css` styled `.table-view .column-header` but omitted `.table-view .filler` and `.table-view .column-header-background`.
- **Affected Components:** `src/main/resources/css/devopshub.css`.
- **Fix Implemented:** Add CSS rule `.table-view .filler, .table-view .column-header-background { -fx-background-color: #1A1B29; }`.

---

## Defect 5: Pipeline Builder Displays Fake Mockup Text and Cluttered Dummy Cards (P2)
- **Screen:** `PipelineBuilderView.java`
- **Steps to Reproduce:**
  1. Navigate to "Map (Builder)" to compose a new pipeline.
  2. Inspect the screen header and middle section.
- **Expected Behavior:** Screen title indicates "New Pipeline", project name matches selected project, and controls allow building DAG stages without synthetic team metrics.
- **Actual Behavior:**
  - Header displays hardcoded `"SOS Computer redesign"`.
  - Middle section renders static mock cards: `"Build & Compile Team - 32 issues, 14 alerts, Passing"`, `"14 hours 5 persons 117 tasks"`, `"Programmers & QA Team"`, `"Deploy & Ops Team"`.
  - These fake cards consume ~150px of vertical space, pushing the stage composer and Save/Cancel buttons down below the viewport fold.
- **Root Cause:** Copy-pasted presentation mockup static text from initial visual mockups.
- **Affected Components:** `PipelineBuilderView.java` lines 91, 335-375.
- **Fix Implemented:**
  - Set screen title to `"New Pipeline"` when creating or `existingPipeline.getName()` when editing.
  - Bind project title dynamically to `projectCombo.getValue().getName()`.
  - Remove hardcoded fake team cards from builder to streamline DAG composition and ensure action buttons remain immediately visible.
