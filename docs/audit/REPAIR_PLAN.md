# DevOpsHub — Repair & Verification Plan

## Prioritized Implementation Plan

| Order | Target Component | Action Item | Expected Outcome | Priority |
| :---: | :--- | :--- | :--- | :---: |
| 1 | `MainLayoutView.java` & View Classes | Implement URL wrapping (`minWidth(0)`, `wrapText(true)`) and configure `ScrollPane` scrollbar policies | Completely eliminates URL layout blow-out and vertical scrolling freeze | **P1** |
| 2 | `ProjectsView.java` Toolbar | Set `setMinWidth(Region.USE_PREF_SIZE)` on `createBtn`, `importBtn`, `removeBtn` | Prevents text truncation (`+ Cre...`) on all display resolutions | **P2** |
| 3 | `ProjectsView.java` Modal | Enclose `showProjectModal` form in `ScrollPane(fitToWidth=true)` and set dimensions to `500, 600` | "Cancel" and "Create Project" action buttons are 100% visible and reachable | **P1** |
| 4 | `src/main/resources/css/devopshub.css` | Add styling for `.table-view .filler` and `.table-view .column-header-background` | Eliminates bright white header rectangle in table views | **P2** |
| 5 | `PipelineBuilderView.java` | Remove fake static team cards ("32 issues", "14 hours"), fix title from `"SOS Computer redesign"` to `"New Pipeline"`, and bind to project | Clean, authentic DAG builder with visible Save/Cancel buttons | **P2** |
| 6 | `DatabaseIntegrationTest.java` | Add teardown in `@AfterEach` to delete test-generated execution records | Eliminates test pollution (`BUILD #999`) in live application database | **P2** |
| 7 | Full Test Suite | Run `mvn test` and `mvn package` to verify zero regressions | Confirms 74+ tests pass and packaging succeeds | **P0** |
