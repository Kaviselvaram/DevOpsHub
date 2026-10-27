# DevOpsHub — Repository & URL Workflow Audit

**Audit Date:** 2026-10-23  
**Auditor:** Principal Software Architect, Platform Engineer, Security Auditor  

---

## 1. URL Entry & Input Validation

### Input Handling in UI
- **Location:** `ProjectsView.java:574-706` (Project creation modal), `ProjectDetailView.java:90-140`.
- **Validation Logic:** Implemented in `GitService.isValidRepositoryUrl(String url)` (`GitService.java:51-62`).
- **Accepted Schemes:**
  - `https://...` (e.g. `https://github.com/Kaviselvaram/lair.git`)
  - `http://...`
  - `git@...` (SSH protocol)
  - `ssh://...`
  - `file://...`
  - Local absolute or relative directory paths (checked via `File.isDirectory()`).
- **Input Sanitization:** Leading and trailing whitespace is automatically stripped (`url.trim()`).
- **Error Presentation:** If an invalid URL format or nonexistent local directory is entered, the UI displays an inline validation message: *"Please provide a valid Git repository URL (https, git@, ssh) or local directory path."*
- **Layout Behavior:** URL text is wrapped via `label.setWrapText(true)` and `label.setMinWidth(0)` so long URLs (100+ chars) do not expand card containers or cause layout overflows.

---

## 2. Actual Repository Resolution & Access

### Git CLI Invocation
- **Location:** `GitService.cloneOrFetch()` (`GitService.java:72-129`).
- **Process Execution:** Executes genuine `git clone` or `git fetch` / `git checkout` via host Git CLI (`ProcessBuilder`).
- **Clone Destination:** Dedicated project workspace inside `workspaces/projects/<projectId>/`.
- **Branch Resolution:** Supports branch checkout with fallback:
  1. Clones `--single-branch --branch <branch>`.
  2. If the branch does not exist on remote, falls back to default branch clone.
- **Workspace Reuse:** If `.git/` already exists in the project workspace, `GitService` issues `git fetch --all --prune` and `git checkout <branch>` rather than blindly wiping the directory, preserving existing files and avoiding unnecessary re-cloning.
- **Credential Protection:** Credentials embedded in URLs (e.g. `https://user:token@github.com/...`) are masked in logs using `SecretMasker.mask(url)`.

---

## 3. Project Type & Build Tool Detection

### Automated Build System Inspection
- **Location:** `ProjectImportService.inspectDirectory()` (`ProjectImportService.java:46-135`).
- **Detection Rules:**
  1. **Java (Maven):** Detected via presence of `pom.xml`. Synthesizes stages: `mvn clean compile -B`, `mvn test -B`, `mvn package -DskipTests=true -B`.
  2. **Java (Gradle):** Detected via `build.gradle` or `build.gradle.kts`. Synthesizes stages: `gradle test`, `gradle build`.
  3. **NodeJS / Next.js:** Detected via `package.json`.
     - Lockfile detection:
       - `package-lock.json` -> `npm ci`
       - `pnpm-lock.yaml` -> `pnpm install --frozen-lockfile`
       - `yarn.lock` -> `yarn install --frozen-lockfile`
     - Framework inspection: If `next.config.js`, `next.config.ts`, or dependency `"next"` exists, configures Next.js production build (`npm run build`) and linter (`npm run lint`).
  4. **Python:** Detected via `requirements.txt`, `pyproject.toml`, or `setup.py`. Synthesizes stages: `pip install -r requirements.txt`, `pytest`.
  5. **Go:** Detected via `go.mod`. Synthesizes stages: `go test ./...`, `go build`.

---

## 4. URL Generation, Display, and Actionability

| URL Type | Displayed In | Source / Generation Logic | External Owner | Actionable Controls | Verification Status |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **Git Repository URL** | `ProjectDetailView`, `ProjectsView`, `HomeView` | User input stored in `projects.repository_url` | Remote Git Host (e.g. GitHub) | "Copy Repository URL" button with clipboard write | **REAL & VERIFIED** |
| **Local Workspace Path** | `ProjectDetailView`, `ExecutionDetailView` | Resolved local directory path (`workspaces/projects/<id>`) | Host filesystem | Path rendered with wrapping | **REAL & VERIFIED** |
| **Webhook Endpoint URL** | `SettingsView`, System logs | `http://<host>:8089/api/webhook/trigger` | Embedded Sun HTTP Server | Actionable endpoint accepting real POST requests | **REAL & VERIFIED** |
| **Build Artifact Location** | `ExecutionDetailView`, `DeploymentService` | Real filesystem path of compiled output (e.g. `target/*.jar`, `.next/BUILD_ID`) | Host filesystem | SHA-256 calculation and local deployment | **REAL & VERIFIED** |

### Misleading / Simulated URL Audit
- **Findings:** Zero fabricated external URLs exist in production code. The application does not fabricate fake deployment URLs (e.g. `https://devopshub.app/deploy/xyz`) or fake cloud links.
- **Distinction Enforced:** Repository URLs, local workspace directories, webhook endpoints, and artifact filepaths are clearly labeled with distinct icons and descriptions.
