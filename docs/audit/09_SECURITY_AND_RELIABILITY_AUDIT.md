# DevOpsHub — Security & Operational Reliability Audit

**Audit Date:** 2026-10-23  
**Auditor:** Application Security Auditor, Platform Reliability Engineer  

---

## 1. Process Execution & Command Security

### Shell Command Invocation
- **Mechanism:** `ProcessExecutor` invokes `/bin/sh -c` on Unix/macOS and `cmd.exe /c` on Windows.
- **Security Assessment:**
  - In a CI/CD platform by design, stage execution requires executing build tool CLI commands configured by the developer (e.g. `mvn clean compile`, `npm run build`, `npm test`).
  - To prevent argument injection and arbitrary filesystem manipulation during project creation and repository checkout, `GitService` executes Git arguments directly without a shell wrapper:
    `new ProcessBuilder("git", "clone", "--branch", branch, "--single-branch", url, dirName)`
  - Untrusted user input in project names or branches cannot break out into arbitrary shell execution.

### Working Directory Confinement
- All pipeline stages are strictly confined to `workspaces/projects/<projectId>/`.
- Before process start, `ProcessBuilder.directory(workingDirectory)` pins the root working tree.
- Temporary files generated during build are contained within the designated workspace.

---

## 2. Path Traversal & Archive Extraction (Zip-Slip Defense)

### Archive Security in `ProjectImportService`
- **Location:** `ProjectImportService.java:140-210`.
- **Zip-Slip Vulnerability Defense:**
  ```java
  File destFile = new File(destDir, entry.getName());
  String canonicalDestDirPath = destDir.getCanonicalPath();
  String canonicalDestFilePath = destFile.getCanonicalPath();
  if (!canonicalDestFilePath.startsWith(canonicalDestDirPath + File.separator)) {
      throw new DevOpsHubException("Zip-Slip security violation detected in entry: " + entry.getName());
  }
  ```
- **Zip-Bomb Protection:**
  - Maximum uncompressed size threshold: 250 MB (`MAX_UNCOMPRESSED_SIZE_BYTES`).
  - Maximum archive entry count threshold: 5,000 entries (`MAX_ZIP_ENTRIES`).
  - If extracted bytes exceed 250 MB or entries exceed 5,000, extraction immediately aborts with an exception.

---

## 3. Secret Masking & Credential Redaction

### Real-Time Log Sanitization
- **Class:** `com.devopshub.util.SecretMasker` (`SecretMasker.java:11-56`).
- **Patterns Covered:**
  1. **Private Keys:** Regex `-----BEGIN [A-Z ]+ PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+ PRIVATE KEY-----` replaced with `[REDACTED PRIVATE KEY]`.
  2. **URL Basic Authentication:** Regex `https?://([^:/\\s]+):([^@/\\s]+)@` replaced with `https://$1:***@`.
  3. **Tokens, Keys & Passwords:** Regex `(?i)(password|secret|token|apikey|api_key|access_key|auth_token|authorization|bearer)` key-value assignments masked with `***`.
- **Pipeline Boundary:** Applied to stdout/stderr in `ProcessExecutor` before appending to `LogCollector` and before writing to MySQL `execution_logs`.

---

## 4. Webhook Security & HMAC Verification

### Webhook Authentication
- **Location:** `WebhookServerService.java:70-130`.
- **HMAC-SHA256 Signature Checking:**
  - Compares the `X-Hub-Signature-256` header using Java `Mac.getInstance("HmacSHA256")`.
  - Constant-time comparison via `MessageDigest.isEqual()` to prevent timing attacks.
  - Rejects unsigned or improperly signed webhook requests with HTTP 401 Unauthorized.

---

## 5. Process Lifecycle & Resource Safety

- **Recursive Process Tree Destruction:** On timeout or user cancellation, `ProcessExecutor.terminateProcessTree()` traverses `process.descendants()` using the Java 9+ `ProcessHandle` API, sending `destroyForcibly()` to all child and grandchild processes before destroying the parent process.
- **Timeout Guarantees:** Every stage enforces a strict timeout (default 300s). Processes that hang or wait for interactive input are terminated with exit code `124`.
- **Thread Pool Shutdown:** `MainApp.stop()` triggers `ExecutionThreadPool.shutdown()`, terminating active worker pools and HikariCP connection pools cleanly.
