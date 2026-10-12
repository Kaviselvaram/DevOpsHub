# DevOpsHub UI/UX Guide

## 1. Design System & Aesthetics
DevOpsHub features a clean, high-contrast, modern developer tool desktop aesthetic tailored for JavaFX. The design prioritizes readability, dense information hierarchy, clear status communication, and responsive desktop layout.

### Color Tokens
The color palette uses tailored HSL and hex tokens defined in `src/main/resources/css/devopshub.css`:

| Category | Token / Class | Hex Value | Role |
|---|---|---|---|
| Background | Base Canvas | `#F8FAFC` (Slate 50) | Main application backdrop |
| Surface | Card / Panel | `#FFFFFF` (White) | Content containers, modal dialogs, cards |
| Surface Alt | Subtle Gray | `#F1F5F9` (Slate 100) | Form inputs, table alternating rows, badges |
| Border | Divider / Outline | `#E2E8F0` (Slate 200) | Structure dividers and card borders |
| Text Primary | Main Heading/Body | `#0F172A` (Slate 900) | Primary typography, headers, metric values |
| Text Muted | Labels/Subtitles | `#64748B` (Slate 500) | Secondary metadata, timestamps, captions |
| Primary Accent | Brand / Action | `#0284C7` (Sky 600) | Primary buttons, active navigation, links |
| Success | Passed / Healthy | `#10B981` (Emerald 500) | Passed stages, success alerts, run status |
| Error / Danger | Failed / Destructive | `#E11D48` (Rose 600) | Failed builds, assertion errors, delete buttons |
| Warning | Running / Notice | `#F59E0B` (Amber 500) | In-progress tasks, timeouts, warnings |
| Info / Queued | Queued / Pending | `#64748B` (Slate 500) | Skipped stages, queued runs, neutral tags |

---

## 2. Typography
- **Font Stack:** `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif`.
- **Monospace Stack:** `"SF Mono", "JetBrains Mono", Menlo, Consolas, monospace` for pipeline commands, error logs, and execution traces.
- **Hierarchy:**
  - Page Title: `20px` bold, `#0F172A`
  - Section Header: `16px` semi-bold, `#1E293B`
  - Card Title / Label: `13px` medium, `#64748B`
  - Body Text: `13px` regular, `#334155`
  - Metric Big Number: `28px` bold, `#0F172A`
  - Code / Log Output: `12px` monospaced, line-height 1.45

---

## 3. Strict SVG-Only Icon System
In accordance with system policy, **zero raster images (PNG, JPG), icon fonts, or emojis** are used.

### Implementation:
1. **Component:** `com.devopshub.view.component.SvgIcon`
2. **Mechanism:** JavaFX `javafx.scene.shape.SVGPath` rendering raw vector path definitions.
3. **Physical Assets:** Corresponding standalone `.svg` vector files reside in `src/main/resources/icons/svg/`.
4. **Key Icon Types:**
   - `DASHBOARD`: Grid layout vector
   - `FOLDER` / `PROJECTS`: Project folder icon
   - `PLAY` / `RUN`: Execution action trigger
   - `CHECK_CIRCLE`: Success status badge
   - `X_CIRCLE`: Failure status badge
   - `CLOCK`: Duration and timestamp indicator
   - `TERMINAL` / `CODE`: Stage command and log viewer
   - `SETTINGS`: Gear configuration
   - `CHART`: Analytics and metrics
   - `SEARCH`: Search input icon
   - `PLUS`: Add project/pipeline action

---

## 4. Layout Architecture & Screen Responsiveness
The UI is constructed using native JavaFX layouts (`BorderPane`, `VBox`, `HBox`, `ScrollPane`, `GridPane`, `TableView`):

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Top Bar: Search, Global Health Status, Refresh Button, User Avatar      │
├───────────────┬─────────────────────────────────────────────────────────┤
│ Navigation    │ Main Content Canvas (Scrollable BorderPane/VBox)        │
│ Sidebar       │                                                         │
│ - Dashboard   │  [Metric Cards Grid: Total, Success Rate, Avg Dur]      │
│ - Projects    │                                                         │
│ - Pipelines   │  [Stage Visualizer / Data Table / Detail Panels]        │
│ - Executions  │                                                         │
│ - Analytics   │  [Log Console / Failure Diagnosis Recommendation Card]  │
│ - Settings    │                                                         │
└───────────────┴─────────────────────────────────────────────────────────┘
```

### Responsive Constraints:
- Minimum window dimension: `1024 × 700`
- Preferred initial dimension: `1280 × 820`
- The sidebar maintains a fixed width of `220px` with responsive navigation tabs.
- The content canvas reflows inside a styled `ScrollPane` with `fitToWidth(true)` so tables and cards stretch gracefully up to 4K resolutions without clipping.
- Tables (`TableView`) use `CONSTRAINED_RESIZE_POLICY` to evenly distribute columns and eliminate accidental horizontal scrollbars.

---

## 5. View Components & States
1. **MetricCard:** High-density summary cards showing current stat, delta badge, and SVG icon.
2. **StatusBadge:** Pill badge reflecting status (`SUCCESS`, `FAILED`, `RUNNING`, `QUEUED`, `SKIPPED`) with matching background tint and border.
3. **ToastNotification:** Floating bottom-right animated alert for feedback on project creation, pipeline execution, and validation.
4. **ConfirmationDialog:** Explicit modal for destructive operations (e.g. deleting a project or pipeline).
5. **LogConsole:** Dark-themed terminal output area (`#0F172A` background, `#38BDF8` stdout, `#F43F5E` stderr) with ANSI/keyword highlighting and real-time streaming capability.
