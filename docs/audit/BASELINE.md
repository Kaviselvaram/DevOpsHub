# DevOpsHub — Baseline Audit Report

## 1. Environment & Architecture Baseline
- **Application Type:** Native Desktop JavaFX CI/CD Management Platform.
- **Language / Runtime:** Java 21+ (Running on Java 25.0.1 OpenJDK 64-Bit Server VM, macOS aarch64).
- **Build System:** Apache Maven 3.6.3 / 3.9+.
- **Primary Database Engine:** MySQL 8.0 Server running in Docker on `localhost:3306`, Database: `devopshub`, User: `root`, Pass: `root`, HikariCP connection pool (Pool size: 10).
- **Fallback Engine:** H2 In-Memory (`jdbc:h2:mem:devopshub;DB_CLOSE_DELAY=-1`).
- **Main Entrypoint:** `com.devopshub.app.MainApp` launching JavaFX Application Stage.
- **Packaging Target:** `target/devopshub-desktop-1.0.0.jar` and `target/devopshub-desktop-1.0.0-jar-with-dependencies.jar`.

## 2. Baseline Build and Test Execution
- **Command Executed:** `mvn clean test`
- **Baseline Test Execution Results:**
  - `Tests run: 74, Failures: 0, Errors: 0, Skipped: 0`
  - `BUILD SUCCESS` (Total time: 55.670s)
- **Application Launch Method:**
  - Command: `mvn javafx:run`
  - Exit code: Active running process (PID 51027).

## 3. Real Target Repository Verification Baseline
- **Integration Test Target:** `https://github.com/Kaviselvaram/lair.git`
- **Active Branch:** `main`
- **Resolved Commit SHA:** `d2b7ca779a76f036d7a89f565a99c727db6c591a`
- **Real Tech Stack:** Next.js 16.3.2, React 19, TypeScript, Tailwind CSS v4, Turbopack.
- **Package Manager Detected:** `npm` via `package-lock.json` (`npm ci`).
- **Available Scripts in `package.json`:**
  - `"dev"`: `next dev`
  - `"build"`: `next build`
  - `"start"`: `next start`
  - `"lint"`: `next lint`
  - **Note on tests:** `"test"` script is NOT present in `package.json`.

## 4. Key Initial Audit Findings & Suspected Areas
1. **Showcase / Simulated Mockups Identified in UI:**
   - `PipelineBuilderView` had hardcoded text: `"SOS Computer redesign"`, `"Build & Compile Team - 32 issues, 14 alerts, Passing"`, `"14 hours 5 persons 117 tasks"`.
   - `ProjectsView` project cards had hardcoded mockup graphics and mock avatar initials `"JD"`, `"MK"`.
2. **UI Layout and Sizing Defects:**
   - Toolbar action buttons in `ProjectsView` were truncated (`+ Cre...`, `Im...`, `Rem...`) due to missing minimum width constraints.
   - The "Create Project" modal dialog was hardcoded to `Scene(480, 520)` while content height was ~580px, causing the `Cancel` and `Create Project` action buttons to be clipped off at the bottom.
   - The TableView in `PipelinesView` exhibited a glaring white rectangle on the right header because `.table-view .filler` was not styled in the dark CSS theme.
3. **URL & Long Path Scrolling Bug:**
   - Single-line `Label` controls displaying repository URLs (e.g. in `ProjectDetailView`, `HomeView`) or build artifact paths (in `ExecutionDetailView`) did not set `minWidth(0)` or `wrapText(true)`.
   - In JavaFX `HBox` containers, this caused the child `minWidth` to expand beyond the viewport width of `MainLayoutView`'s `ScrollPane(fitToWidth=true)`.
   - This horizontal overflow combined with macOS trackpad scroll events causes JavaFX `ScrollPaneSkin` to freeze vertical scrolling.
