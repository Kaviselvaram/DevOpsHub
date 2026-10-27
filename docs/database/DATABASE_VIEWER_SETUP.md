# DevOpsHub — Database Viewer Setup & Live Inspection Guide

## 1. Executive Summary

A standalone, production-grade database administration GUI—**MySQL Workbench (v8.0.47)**—has been installed and verified on macOS to inspect the live DevOpsHub database in real time. 

The setup adheres strictly to the non-disturbance principles:
- **Zero changes** to existing application code, database configuration, connection pools, or environment variables.
- **Zero disruptions** to the running DevOpsHub application process (PID `39814`).
- **No data reset, truncation, recreation, or migration changes**.
- Pre-configured connection profile initialized for immediate access.

---

## 2. Active Database Identification & Runtime Evidence

Read-only inspection of the active runtime and application processes confirms:

| Parameter | Observed Value | Evidence Source |
| :--- | :--- | :--- |
| **Persistence Engine** | **MySQL Community Server 8.0.46** | `SELECT VERSION()` inside container |
| **Execution Environment** | Docker Container (`devopshub-mysql`, ID `15757d37a748`) | `docker ps`, `docker inspect` |
| **Persistent Volume** | `e375552b...c0a` mounted to `/var/lib/mysql` | Docker Volume inspection |
| **Database Name** | `devopshub` | Application schema catalog |
| **Host & Port** | `127.0.0.1:3306` (`0.0.0.0:3306` bound on host) | `lsof -i :3306` |
| **Active Connections** | `java` process PID `39814` actively connected | `lsof -i :3306` socket state `ESTABLISHED` |
| **Effective JDBC URL** | `jdbc:mysql://localhost:3306/devopshub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | `AppConfig.java`, `DatabaseManager.java` |
| **H2 Fallback State** | **INACTIVE** (`./data/` directory is empty; 0 bytes) | Filesystem inspection of `./data` |

### Existing Data Inventory (Preserved Unaltered)
All 13 database tables are populated with live records:
- `projects`: **39** records
- `pipelines`: **46** records
- `pipeline_stages`: **84** records
- `stage_dependencies`: **36** records
- `executions`: **19** records
- `stage_executions`: **27** records
- `execution_logs`: **212** records
- `execution_events`: **478** records
- `failure_diagnoses`: **7** records
- `deployment_records`: **2** records
- `pipeline_schedules`: **0** records
- `security_findings`: **0** records
- `schema_version`: **4** records (V1, V2, V3, V4 applied)

---

## 3. Administration GUI Installed

- **Application**: **MySQL Workbench 8.0.47**
- **Binary Location**: `/Applications/MySQLWorkbench.app`
- **Installation Method**: Official Homebrew Cask (`brew install --cask mysqlworkbench`)
- **Compatibility**: Native macOS Apple Silicon (`mac-arm64`)
- **Pre-Configured Profile**: `DevOpsHub Local MySQL` configured in `~/Library/Application Support/MySQL/Workbench/connections.xml`

---

## 4. How to Launch & Connect

### Step 1: Open MySQL Workbench
Launch the application via either:
- **Terminal**:
  ```bash
  open -a MySQLWorkbench
  ```
- **macOS Finder / Spotlight**: Press `Cmd + Space`, type `MySQL Workbench`, and press `Enter`.

### Step 2: Open the Pre-Configured Connection
On the MySQL Workbench home screen, look under **MySQL Connections**:
- Click the connection tile labeled **DevOpsHub Local MySQL**.

### Step 3: Connection Settings (No Secrets Exposed)
The connection is configured with:
- **Connection Method**: Standard (TCP/IP)
- **Hostname**: `127.0.0.1`
- **Port**: `3306`
- **Username**: `root`
- **Default Schema**: `devopshub`
- **Password**: Enter the database password configured in your `.env` file (or default `root`). Check the **"Store in Keychain"** box so macOS remembers it safely without prompting again.

---

## 5. How to Inspect Live Schemas, Tables, and Data

### A. Browse Schemas & Tables
1. In the left sidebar under the **Navigator** panel, select the **Schemas** tab.
2. Expand `devopshub` -> **Tables**.
3. You will see all 13 application tables (`projects`, `pipelines`, `executions`, `execution_logs`, etc.).

### B. Inspect Columns, Data Types, and Indexes
1. Hover over any table in the schema tree (e.g. `executions`).
2. Click the small **"wrench" (Inspector / Alter)** icon to view the table design, columns, nullability, auto-increments, indexes, and foreign keys.
3. Alternatively, click the **"info" (i)** icon to view table statistics, storage size, and index breakdown.

### C. View and Edit Records
1. Hover over any table (e.g., `projects` or `executions`) and click the **table grid icon** (or right-click -> **"Select Rows - Limit 1000"**).
2. The interactive Result Grid displays all columns and rows.
3. You can sort by clicking any column header, search using the filter bar, and copy rows or cells directly.

### D. Execute Custom Read-Only Queries
1. Click **File -> Open SQL Script...** (or press `Cmd + Shift + O`).
2. Select the repository inspection script:
   `docs/database/inspect_database.sql`
3. Execute the full script by pressing `Cmd + Shift + Enter`, or execute individual queries by placing your cursor on a statement and pressing `Cmd + Enter`.

---

## 6. Live Updates & Refresh Workflow

### Live Visibility Explained
MySQL Workbench connects to the same InnoDB storage engine as DevOpsHub via autocommit read transactions. Whenever DevOpsHub commits an execution status update, writes a log line, or records an execution event, the new data is committed to the database.

### How to Refresh Data in MySQL Workbench:
1. **Result Grid Refresh**: When viewing a table, click the **blue refresh icon** (circular arrow) on the Result Grid toolbar, or press `Cmd + Enter` to re-execute the active query.
2. **Schema Tree Refresh**: In the **Navigator -> Schemas** pane, right-click anywhere in whitespace and click **"Refresh All"** (or click the circular arrows icon at the top right of the Navigator).
3. **Automated Polling Note**: MySQL Workbench does not include a background auto-polling timer for SQL result tabs. Data refresh is instantaneous via 1-click or keyboard shortcut (`Cmd + Enter`).
   *(Note: If continuous automated polling every 2–5 seconds is ever required without manual keystrokes, universal clients such as DBeaver Community support automated polling timers on table grids).*

---

## 7. How to Distinguish Active MySQL from H2 Fallback

DevOpsHub includes a dual-engine architecture in `DatabaseManager.java`:
1. **Primary Engine (Active)**: **MySQL** running in Docker container `devopshub-mysql` on port `3306`.
2. **Fallback Engine (Standby)**: **H2** file-based database located at `./data/devopshub.mv.db`.

### How to check which engine is active:
- **Inspect DevOpsHub UI**: The application's Settings or connection latency monitor shows MySQL status.
- **Inspect Directory `./data/`**:
  - If `./data/` is empty: **MySQL is active** (H2 was never initialized).
  - If `./data/devopshub.mv.db` exists: H2 has been used.
- **Inspect Docker Port**:
  ```bash
  lsof -i :3306
  ```
  If Java processes are connected to `localhost:mysql`, DevOpsHub is using MySQL.

### Connecting to H2 Fallback (If Ever Activated)
If MySQL is stopped and DevOpsHub runs in embedded H2 mode:
- **H2 JDBC URL**: `jdbc:h2:file:/Users/kaviselvaramkathirvel/javacicd/data/devopshub;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`
- **Username**: `sa`
- **Password**: *(empty)*
- **H2 Client**: DBeaver Community or the standard H2 Console (`java -jar ~/.m2/repository/com/h2database/h2/2.2.224/h2-2.2.224.jar`).

---

## 8. Verification Results

| Verification Check | Status | Verification Detail |
| :--- | :---: | :--- |
| **Existing DevOpsHub Application Running** | **PASS** | Process `39814` (`MainApp`) remained active throughout and continues running. |
| **Configured Database Preserved** | **PASS** | Host `127.0.0.1`, Port `3306`, DB `devopshub`. Config files unmodified. |
| **Data Integrity Preserved** | **PASS** | Exact row counts verified before and after setup (39 projects, 46 pipelines, 19 executions). |
| **GUI Installation** | **PASS** | MySQL Workbench 8.0.47 installed cleanly into `/Applications/MySQLWorkbench.app`. |
| **GUI Connection Preconfigured** | **PASS** | Connection profile `DevOpsHub Local MySQL` verified in `connections.xml` and loaded cleanly by Workbench. |
| **Schema & Constraint Inspection** | **PASS** | All 13 tables, primary keys, 14 foreign key relationships, and indexes mapped. |
| **Read-Only Inspection Script** | **PASS** | `docs/database/inspect_database.sql` executed with 0 syntax errors or warnings. |
| **Zero Destructive Actions** | **PASS** | No `docker compose down -v`, no DB resets, no migrations applied or re-run. |
| **No Exposed Credentials** | **PASS** | Passwords excluded from all documentation, logs, and committed repository files. |

---

## 9. Repository Modifications Inventory

| File Path | Action | Rationale |
| :--- | :--- | :--- |
| `docs/database/inspect_database.sql` | **Created** | Comprehensive read-only inspection script covering database identity, schemas, tables, constraints, accurate counts, and sample records. |
| `docs/database/DATABASE_VIEWER_SETUP.md` | **Created** | Complete setup documentation, connection parameters, live refresh workflows, and verification report. |

*No other files in the repository were created or modified.*
