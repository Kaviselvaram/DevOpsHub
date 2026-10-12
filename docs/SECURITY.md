# DevOpsHub Security & Process Sandboxing

## 1. Threat Model & Security Boundaries
DevOpsHub is a developer platform that executes commands on the host operating system. To protect system integrity and data safety, several security boundaries are enforced:

### Key Threat Vectors & Mitigations:

| Threat Vector | Risk Level | Mitigation in DevOpsHub |
|---|---|---|
| Command Injection | Critical | `ProcessBuilder` uses structured array/list command tokens; avoids raw shell string concatenation where user parameters are accepted. |
| Runaway / Fork Bomb Subprocesses | High | Per-stage process timeout (default 300s); un-terminated processes are destroyed forcibly on timeout or cancellation. |
| Heap / Memory Exhaustion via Logs | High | `BoundedLogBuffer` limits memory retention to 5,000 lines per stage; logs are flushed to database and discarded from RAM. |
| SQL Injection | High | 100% of database queries use JDBC `PreparedStatement` with parameterized placeholders (`?`). Zero string concatenation in SQL queries. |
| Secret Leaks in Logs | Medium | Password and credential properties in `.env` are loaded via memory-only properties and never written to execution logs. |
| Accidental Project Deletion | Medium | Explicit `ConfirmationDialog` requires interactive user confirmation before cascading deletes are executed. |

---

## 2. Command Execution Guidelines
When configuring pipeline stages in `PipelineBuilderView`:
- Use approved CLI tools (`mvn`, `gradle`, `npm`, `git`, `docker`, `echo`).
- Avoid running unverified third-party shell scripts with root/administrative privileges.
- In production deployment environments, execution should occur inside isolated Docker containers or ephemeral VMs.
