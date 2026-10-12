# DevOpsHub Failure Analysis & Recovery Intelligence

## 1. Overview
The primary differentiator of DevOpsHub is its explainable, rule-based failure analysis engine (`com.devopshub.analysis.FailureRuleEngine`). Rather than using an unexplainable AI black-box or hallucination-prone external API, DevOpsHub parses execution evidence (logs, exit codes, stage context) using deterministic rules to diagnose the root cause and recommend actionable recovery steps.

---

## 2. The Analysis Workflow
When an execution finishes with status `FAILED`:
1. **Evidence Collection:**
   - The engine retrieves all logs captured for the failed stage from `execution_logs`.
   - The logs are sanitized and normalized into an in-memory buffer.
2. **Rule Matching:**
   - The engine iterates through registered rules ordered by specificity.
   - Each rule inspects the log buffer and stage metadata using targeted regex patterns.
3. **Diagnosis Generation:**
   - The first matching rule generates a `FailureDiagnosis` domain object containing:
     - `rule_id`: Unique identifier of the triggered rule (e.g., `RULE-ASSERT-001`).
     - `failure_category`: Categorization (e.g., `TEST_FAILURE`, `COMPILATION_ERROR`).
     - `title`: Concise diagnostic headline.
     - `likely_cause`: Explanation of why the stage failed.
     - `evidence_excerpts`: Extracted log lines demonstrating the error.
     - `recommended_action`: Concrete steps the engineer should take to resolve the issue.
     - `confidence`: Match strength (typically 0.85 - 0.99 for exact pattern matches).
4. **Persistence & UI Notification:**
   - The diagnosis is saved to the `failure_diagnoses` table in MySQL.
   - The JavaFX UI updates immediately to render the diagnosis card with a warning badge and copyable recommendation.

---

## 3. Real World Failure Scenario (Build #184)
Matching the project presentation specification:
- **Project:** E-Commerce Checkout Service
- **Failed Stage:** Integration Tests
- **Exit Code:** `1`
- **Captured Log Evidence:**
  ```text
  [ERROR] Tests run: 42, Failures: 1, Errors: 0, Skipped: 0
  [ERROR] com.ecommerce.auth.LoginServiceTest.testUnauthorizedAccess:78
  [ERROR] Expected HTTP status 401 Unauthorized but received HTTP 500 Internal Server Error
  [ERROR] Exception occurred at com.ecommerce.auth.LoginService.validateToken(LoginService.java:142)
  [ERROR] NullPointerException: authContext is null
  ```
- **Diagnostic Result Produced by Rule `RULE-ASSERT-001`:**
  - **Category:** `TEST_FAILURE`
  - **Title:** Test Assertion Failure (HTTP 401 expected, received HTTP 500)
  - **Likely Cause:** An unhandled exception in `LoginService` caused the endpoint to return an Internal Server Error (HTTP 500) instead of an Unauthorized response (HTTP 401).
  - **Evidence Excerpts:** `Expected HTTP status 401 Unauthorized but received HTTP 500 Internal Server Error at com.ecommerce.auth.LoginService.validateToken(LoginService.java:142)`
  - **Recommended Action:** Inspect `LoginService.java:142` to ensure missing authentication tokens throw an `AuthenticationException` mapped to HTTP 401 rather than triggering an unhandled `NullPointerException`.
