# DevOpsHub — Root Cause Analysis Report

## Root Cause 1: Layout Overflow & Scroll Event Starvation on URL Displays
- **Symptom:** When a project or execution displays a repository URL or build artifact path, vertical scrolling fails on macOS trackpads.
- **Architectural Analysis:**
  JavaFX controls calculate layout using `minWidth`, `prefWidth`, and `maxWidth`. When a `Label` renders text without `setWrapText(true)` and `setMinWidth(0)`, its `minWidth` is computed from the un-wrapped string width. In standard desktop environments, URLs and artifact paths regularly exceed 100–250 characters, requiring 800–1600px of minimum width.
  When placed in an `HBox` inside a `VBox` inside a `ScrollPane(fitToWidth=true)`, the `ScrollPane` attempts to constrain the content to its viewport width (e.g. 850px). However, JavaFX guarantees that a node is never sized smaller than its `minWidth`. Consequently, the node expands beyond the right edge of the viewport.
  On macOS, two-finger scrolling produces simultaneous deltaX and deltaY values. Because horizontal scrolling is clamped by `fitToWidth`, the JavaFX `ScrollPaneSkin` consumes the scroll gesture attempting to scroll horizontally, starving the vertical scroll mechanism.
- **Remedy:**
  1. Set `label.setWrapText(true);` and `label.setMinWidth(0);` across all dynamic URL and path labels.
  2. Set `HBox.setHgrow(container, Priority.ALWAYS);` and `container.setMinWidth(0);` on the immediate parent boxes.
  3. Configure `ScrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);` and `ScrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);`.

---

## Root Cause 2: Button Text Truncation in Responsive HBox Toolbars
- **Symptom:** Buttons display truncated labels (`+ Cre...`, `Im...`, `Rem...`).
- **Architectural Analysis:**
  In `ProjectsView.java`, `toolbar` is an `HBox` that contains a title, search field, date badge, two dropdown filters, a spacer, three action buttons, and a view mode switcher. When `HBox` computes layout in a constrained window, child nodes without an explicit `minWidth` are compressed below their preferred width (`prefWidth`).
- **Remedy:**
  Explicitly invoke `button.setMinWidth(Region.USE_PREF_SIZE)` on all toolbar action buttons so the layout engine preserves complete text labels under all window dimensions.

---

## Root Cause 3: Fixed-Dimension Modal Dialog with Uncontained Form Layout
- **Symptom:** In the "Create Project" modal dialog, bottom action buttons are clipped off.
- **Architectural Analysis:**
  `ProjectsView.showProjectModal` instantiated `new Scene(form, 480, 520)` with `modal.setResizable(false)`. The vertical accumulation of the title, error banner, 5 form fields, descriptions, dropdowns, and button box requires ~590px of vertical space. Because `form` was not enclosed in a `ScrollPane`, the excess height was physically clipped by the bottom border of the window.
- **Remedy:**
  Wrap the modal form in a `ScrollPane(fitToWidth=true)` and increase default dialog dimensions to `500, 600`.

---

## Root Cause 4: Missing JavaFX TableView Filler Styling
- **Symptom:** A bright white rectangle renders in the top-right table header of `PipelinesView`.
- **Architectural Analysis:**
  When a TableView's columns do not sum to the exact pixel width of the viewport, JavaFX creates an internal filler region (`.table-view .filler` and `.table-view .column-header-background`). The default Modena stylesheet styles this filler with a light/white background. Because `devopshub.css` did not define a background for `.filler`, the dark theme exposed an unstyled white block.
- **Remedy:**
  Add `.table-view .filler, .table-view .column-header-background { -fx-background-color: #1A1B29; }` to `devopshub.css`.

---

## Root Cause 5: Inadvertent Test Data Contamination of Shared Persistence
- **Symptom:** UI displays dummy `BUILD #999` with `Log line 1 \n Error log line 2`.
- **Architectural Analysis:**
  `DatabaseIntegrationTest` created sample records (`exec-log-test-...`, build #999) against the live MySQL database to test persistence, but failed to clean up the records in `@AfterEach`. When the user later launched the desktop GUI, these leftover records were retrieved and rendered.
- **Remedy:**
  Add explicit teardown cleanup in `DatabaseIntegrationTest` to delete test records by ID after test assertions complete.
