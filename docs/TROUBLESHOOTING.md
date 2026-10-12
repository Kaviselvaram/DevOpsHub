# DevOpsHub Troubleshooting Guide

## 1. Common Issues & Solutions

### 1. Database Connection Refused (`Communications link failure`)
- **Symptoms:** App fails to start or shows `DatabaseException: Could not connect to database`.
- **Cause:** MySQL is not running on port 3306 or firewall is blocking connections.
- **Remedy:**
  1. Verify MySQL status: `docker ps` or `mysqladmin -u root -p status`.
  2. If using Docker, restart container: `docker restart devopshub-mysql`.
  3. Ensure credentials in `.env` match (`DB_USER` and `DB_PASSWORD`).

### 2. JavaFX Runtime Exception (`Graphics Device initialization failed`)
- **Symptoms:** Error on headless Linux environments or server environments without X11/Wayland display.
- **Cause:** JavaFX requires a graphical display environment.
- **Remedy:**
  1. On desktop macOS/Windows/Linux: Run directly on your native display session.
  2. In CI/CD or headless environments: Run tests with Monocle headless display:
     `mvn test -Dtestfx.robot=glass -Dtestfx.headless=true`

### 3. Pipeline Stage Permission Denied (`exit code 126 / 127`)
- **Symptoms:** Stage execution fails with `Permission denied` or `Command not found`.
- **Cause:** The script being executed lacks executable bits or the binary is not on system `PATH`.
- **Remedy:**
  1. Run `chmod +x <script_path>`.
  2. Specify the absolute path to the binary in the stage command configuration.

### 4. Database Foreign Key Truncation Error
- **Symptoms:** `Data truncation: Data too long for column 'pipeline_id'`.
- **Cause:** Older schema had `VARCHAR(36)` instead of `VARCHAR(100)`.
- **Remedy:**
  - DevOpsHub uses `VARCHAR(100)` across all foreign keys in `V1__initial_schema.sql`. Run migration or execute:
    `ALTER TABLE executions MODIFY pipeline_id VARCHAR(100);`

---

## 2. Diagnostics & Log Locations
- Application log messages are streamed to standard output via Logback/SLF4J.
- Execution logs for builds are stored in the `execution_logs` database table and can be queried directly:
  ```sql
  SELECT timestamp, log_level, message 
  FROM execution_logs 
  WHERE execution_id = 'YOUR_EXECUTION_ID' 
  ORDER BY timestamp ASC;
  ```
