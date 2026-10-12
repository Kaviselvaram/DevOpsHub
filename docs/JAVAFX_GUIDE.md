# DevOpsHub JavaFX Guide

## 1. JavaFX Application Lifecycle
DevOpsHub implements the standard JavaFX lifecycle managed by `com.devopshub.app.MainApp` and launched safely by `com.devopshub.app.Launcher`.

### Lifecycle Phases:
1. **`init()`**:
   - Loads environment configuration from `.env` or system variables.
   - Initializes HikariCP JDBC connection pool.
   - Runs database migrations (`V1__initial_schema.sql` and `V2__sample_seed_data.sql`).
   - Initializes core services (`ProjectService`, `PipelineService`, `ExecutionEngine`, `FailureRuleEngine`, `DashboardService`).
2. **`start(Stage primaryStage)`**:
   - Constructs the root layout view (`MainLayoutView`).
   - Attaches stylesheet (`/css/devopshub.css`).
   - Configures the desktop stage (title, dimensions `1280x820`, minimum size `1024x700`).
   - Displays the stage and navigates to the default `DASHBOARD` view.
3. **`stop()`**:
   - Signals graceful shutdown to `ExecutionThreadPool` (cancels ongoing executions safely).
   - Closes the HikariCP database connection pool.
   - Flushes logs and exits cleanly.

---

## 2. Programmatic Layout vs FXML Architecture
DevOpsHub employs a clean, modular **programmatic JavaFX architecture**:
- **Why Programmatic?**
  - Strong compile-time type safety across all components and view handlers.
  - No brittle reflection or classpath issues common with FXML in modular runtimes.
  - Clean constructor-based dependency injection for services.
  - Zero XML boilerplate for dynamic stage creation, interactive badges, and SVG vectors.
- **Organization:**
  - `com.devopshub.view.*`: Full screen views (`DashboardView`, `ProjectsView`, `PipelinesView`, `ExecutionHistoryView`, `ExecutionDetailView`, `AnalyticsView`, `SettingsView`).
  - `com.devopshub.view.component.*`: Reusable atomic UI components (`SvgIcon`, `StatusBadge`, `MetricCard`, `ToastNotification`, `ConfirmationDialog`).

---

## 3. UI Thread Safety & Concurrency
A critical rule of JavaFX is that the **JavaFX Application Thread must never be blocked** by long-running computations, subprocess executions, or database operations.

### Safe Threading Rules in DevOpsHub:
1. **Background Tasks:**
   - Pipeline execution runs on dedicated daemon threads managed by `ExecutionThreadPool` (using Java's `ExecutorService` and `CompletableFuture`).
   - Process output streaming reads subprocess standard out/err via daemon threads.
2. **UI Updates via `Platform.runLater()`:**
   - Any state change from the background engine (e.g. stage status updated to `RUNNING`, log line appended, execution finished) is dispatched to the UI thread via `Platform.runLater(() -> ...)`:
   ```java
   Platform.runLater(() -> {
       statusBadge.setStatus(stage.getStatus());
       logConsole.appendText(logLine);
   });
   ```
3. **Responsive Polling & Callbacks:**
   - `ExecutionDetailView` attaches execution listeners to receive real-time stage progress updates without freezing the user interface.

---

## 4. CSS Styling Tokens
Styles are centralized in `src/main/resources/css/devopshub.css`:
- Root CSS variables define palette, radii, borders, and font families.
- Buttons, text fields, combo boxes, table views, and scroll panes have custom skinning matching modern macOS and Windows desktop guidelines.
- Hover, pressed, and focus-visible pseudo-classes are fully declared.

---

## 5. Running the Application
### Maven JavaFX Plugin:
```bash
mvn javafx:run
```
### Packaged Fat JAR:
```bash
java -jar target/devopshub-desktop-1.0.0.jar
```
*(Requires Java 21+ and active MySQL database).*
