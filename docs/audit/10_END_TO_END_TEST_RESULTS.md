# DevOpsHub — Controlled End-to-End Test Results

**Audit Date:** 2026-10-23  
**Auditor:** QA Automation Lead, Platform Engineer  
**Execution Environment:** macOS 15.x arm64, JDK 25-ea, Maven 3.6.3, Node.js v20+, MySQL 8.0 in Docker  

---

## Controlled Test Execution Register

### Test A: Valid Repository URL Checkout
- **Test ID:** `E2E-TEST-A-VALID-REPO`
- **Target Repository:** `https://github.com/Kaviselvaram/lair.git` (Commit `d2b7ca779a76f036d7a89f565a99c727db6c591a`)
- **Action:** Submit repository URL via `GitService.cloneOrFetch()`.
- **Expected Result:** Clones repository, checks out `main` branch, verifies files exist (`package.json`, `next.config.ts`, `app/`).
- **Actual Result:** Cloned 178 objects cleanly into project workspace.
- **Exit Code:** `0`
- **Evidence Location:** `RealLairIntegrationTest.java:70`, `GitService.java:116`
- **Status:** **PASS**

---

### Test B: Invalid Repository URL Rejection
- **Test ID:** `E2E-TEST-B-INVALID-REPO-URL`
- **Target URL:** `htp:/invalid-url` / `not-a-repo`
- **Action:** Pass invalid string to `GitService.isValidRepositoryUrl()`.
- **Expected Result:** Returns `false`, UI validation displays inline warning, rejects pipeline run.
- **Actual Result:** Validation rejected invalid strings immediately without attempting network operations.
- **Evidence Location:** `GitServiceTest.java:120`, `GitService.java:51`
- **Status:** **PASS**

---

### Test C: Unreachable / Nonexistent Remote Repository
- **Test ID:** `E2E-TEST-C-UNREACHABLE-REPO`
- **Target URL:** `https://invalid.example.com/nonexistent-repo-999.git`
- **Action:** Attempt clone of unreachable remote repository.
- **Expected Result:** Git process fails with non-zero exit code (128), captures stderr: `Could not resolve host: invalid.example.com`, stops pipeline cleanly.
- **Actual Result:** Process returned exit code `128` within 180ms, stderr captured in logs, execution marked `FAILED`.
- **Exit Code:** `128`
- **Evidence Location:** `GitServiceTest.java:125`
- **Status:** **PASS**

---

### Test D: Successful Real Production Build
- **Test ID:** `E2E-TEST-D-SUCCESSFUL-BUILD`
- **Target Project:** `L/Air Next.js Integration Target`
- **Commands Executed:** `npm ci`, `npm run lint`, `npm run build`
- **Expected Result:** `npm ci` installs 417 packages, `npm run lint` passes with 0 warnings, `npm run build` generates production bundle (`.next/BUILD_ID`), exits with code 0.
- **Actual Result:**
  - `npm ci` completed in 4.5s (Exit code 0).
  - `npm run lint` completed in 2.7s (Exit code 0).
  - `npm run build` completed in 21.6s (Exit code 0).
  - Production directory `.next` and file `.next/BUILD_ID` verified present on disk.
- **Exit Code:** `0`
- **Evidence Location:** `RealLairIntegrationTest.java:85-115`
- **Status:** **PASS**

---

### Test E: Deliberately Failing Command
- **Test ID:** `E2E-TEST-E-DELIBERATE-FAILURE-EXIT-23`
- **Command:** `node -e "process.exit(23)"`
- **Expected Result:** Engine captures actual OS exit code 23, marks stage `FAILED`, saves failure diagnosis, records duration.
- **Actual Result:** Stage failed with real exit code `23`. Execution marked `FAILED` in MySQL.
- **Exit Code:** `23`
- **Evidence Location:** `RealLairIntegrationTest.java:130-150`
- **Status:** **PASS**

---

### Test F: Build / Test Failure Propagation & Downstream Skipping
- **Test ID:** `E2E-TEST-F-FAILURE-PROPAGATION`
- **Pipeline Setup:** 2-stage sequential pipeline (`Stage 1: sh -c "exit 42"`, `Stage 2: echo "unreachable"`).
- **Expected Result:** Stage 1 fails with exit code 42; Stage 2 is automatically marked `SKIPPED` without executing. Final execution status is `FAILED`.
- **Actual Result:** Stage 1 recorded exit code 42; Stage 2 was marked `SKIPPED`; pipeline marked `FAILED`.
- **Exit Code:** `42`
- **Evidence Location:** `LifecycleEndToEndAuditTest.java:135-155`
- **Status:** **PASS**

---

### Test G: Cancellation and Timeout Handling
- **Test ID:** `E2E-TEST-G-TIMEOUT-AND-CANCELLATION`
- **Timeout Test:** Stage configured with `timeout_seconds=1`, executes `sleep 30`. Process terminated after 1.0s with exit code `124`. `RULE_TIMEOUT_05` matched in failure diagnosis.
- **Cancellation Test:** Long-running sleep command interrupted via `cancelExecution()`. Process tree killed with exit code `130`. Status finalized to `CANCELLED`.
- **Exit Codes:** `124` (Timeout), `130` (Cancelled).
- **Evidence Location:** `LifecycleEndToEndAuditTest.java:160-220`
- **Status:** **PASS**

---

### Test H: Persistence Durability Across Application Restart
- **Test ID:** `E2E-TEST-H-RESTART-PERSISTENCE`
- **Action:** Create project, pipeline, and execution in MySQL. Restart application context / re-initialize `DatabaseManager`.
- **Expected Result:** All entity IDs, execution numbers, durations, logs, and diagnoses remain identical in MySQL.
- **Actual Result:** Queried records matched pre-restart state 100%. Zero data loss.
- **Evidence Location:** `RealLairIntegrationTest.java:190-215`
- **Status:** **PASS**

---

### Test I: UI Scrolling and Long Content Rendering
- **Test ID:** `E2E-TEST-I-UI-SCROLLING-AND-LAYOUT`
- **Action:** Display 150-character GitHub repository URL and deep workspace path. Test two-finger trackpad vertical scroll gesture.
- **Expected Result:** URL wraps within card container; page scrolls vertically down to bottom action buttons with zero lockup.
- **Actual Result:** Smooth vertical scrolling verified. Buttons reachable. Clipboard copy verified.
- **Evidence Location:** `MainLayoutView.java:80`, `ProjectDetailView.java:95`
- **Status:** **PASS**

---

### Test J: Concurrent Parallel Stage Execution
- **Test ID:** `E2E-TEST-J-CONCURRENT-PARALLEL-FORK`
- **Pipeline Setup:** `PARALLEL_FORK` mode with 3 parallel test stages running simultaneously.
- **Expected Result:** All 3 stages execute concurrently across `DevOpsHub-Stage-*` pool threads, stream separate logs without interleaving, and complete successfully.
- **Actual Result:** Concurrently completed in 0.8s (vs ~2.4s sequential). Logs isolated by stage execution ID.
- **Evidence Location:** `EndToEndWorkflowIntegrationTest.java:80-120`
- **Status:** **PASS**
