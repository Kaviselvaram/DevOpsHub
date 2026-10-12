# DevOpsHub User Guide

## 1. Introduction
Welcome to **DevOpsHub**, the intelligent CI/CD management platform built natively for your desktop. DevOpsHub allows you to manage software projects, configure sequential and parallel pipelines, run builds and tests with real-time feedback, inspect diagnostic logs, and receive actionable recovery recommendations when pipelines fail.

---

## 2. Navigating the Interface
The application interface consists of:
- **Left Sidebar:** Quick navigation between Dashboard, Projects, Pipelines, Executions, Analytics, and Settings.
- **Top Bar:** Quick search bar, system health indicator, refresh button, and profile badge.
- **Main Canvas:** Responsive work area for metrics, tables, stage flowcharts, and log terminals.

---

## 3. Working with Projects
1. **View Projects:** Click **Projects** in the navigation bar. You will see all configured projects with repository URLs, default branches, and status.
2. **Create a Project:**
   - Click the blue **+ New Project** button in the header.
   - Enter the Project Name (e.g. `Billing Service`), Description, Repository URL (e.g. `https://github.com/org/billing.git`), and Default Branch (`main`).
   - Click **Save Project**.
3. **Delete a Project:**
   - Click the red delete icon next to a project.
   - Confirm deletion in the modal dialog. Note: Deleting a project removes its associated pipelines and executions.

---

## 4. Configuring and Running Pipelines
1. **View Pipelines:** Navigate to **Pipelines** to see defined pipelines and their associated projects.
2. **Configure Stages in Pipeline Builder:**
   - In **Pipeline Builder**, you can add stages (Checkout, Build, Test, Security, Package, Deploy).
   - Configure execution mode: **Sequential** or **Parallel**.
   - Set stage command (e.g. `mvn clean compile`, `mvn test`) and timeout seconds.
   - Set stage dependencies to define custom execution graphs.
3. **Trigger an Execution:**
   - Click the green **Run** button next to any pipeline in the Pipelines view.
   - DevOpsHub launches the execution in the background and redirects you to the live Execution Details view.

---

## 5. Monitoring Executions & Failure Diagnosis
1. **Real-time Stage Progress:**
   - Watch stages transition from `QUEUED` -> `RUNNING` -> `SUCCESS` or `FAILED`.
   - If a stage fails, downstream dependent stages are automatically marked `SKIPPED`.
2. **Viewing Logs:**
   - Click on any stage or open the **Log Viewer** tab.
   - Standard output appears in blue/white text, while standard error is highlighted in red.
3. **Failure Analysis & Recommendations:**
   - When a pipeline fails, an intelligent **Failure Diagnosis Card** appears.
   - Read the **Likely Cause** and **Recommended Action** (e.g., investigating HTTP 401 vs 500 status mismatch in `LoginService`).
   - Follow the recommended instructions to fix your codebase and re-run the pipeline.
