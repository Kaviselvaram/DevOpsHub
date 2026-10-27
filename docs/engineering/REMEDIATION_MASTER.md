# DevOpsHub — Remediation Master Plan & Architecture

**Date:** 2026-10-24  
**Project:** DevOpsHub — JavaFX Intelligent CI/CD Management Desktop Platform  
**Target Release:** v1.0.0 Production Release  

---

## 1. Executive Mission Overview

DevOpsHub is an autonomous, dependable JavaFX desktop CI/CD application that empowers engineers to manage multi-ecosystem software projects and execute reproducible pipelines locally with durable persistence, real process isolation, and actionable diagnostics.

The master remediation roadmap is executed sequentially across 10 defined milestones plus the mandatory live GUI visual audit and acceptance gate:

- **Milestone 1:** Database Safety & Durable Persistence
- **Milestone 2:** Git Repository Integration
- **Milestone 3:** Pipeline Execution Engine
- **Milestone 4:** Real Build and Test Pipelines
- **Milestone 5:** Maven Packaging and Independent Launch
- **Milestone 6:** Test Suite and Integration Infrastructure
- **Milestone 7:** JavaFX UI and User Workflow
- **Milestone 8:** Security and Credentials Handling
- **Milestone 9:** Reliability, Recovery, and Data Consistency
- **Milestone 10:** End-to-End Release Gate
- **Part 9:** Compulsory Live Application Launch, Visual Audit, Real Interaction & Acceptance

---

## 2. Architectural Pillars

```
+-----------------------------------------------------------------------------------+
|                         DevOpsHub JavaFX Desktop (Layer 1)                         |
|  - MainApp / Launcher / MainLayoutView                                             |
|  - DashboardView / ProjectsView / PipelinesView / PipelineBuilderView / History    |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                           Service Layer (Layer 2)                                 |
|  - PipelineService / ProjectService / DashboardService                            |
|  - GitService / ProjectImportService / DeploymentService / FailureRuleEngine      |
|  - WebhookServerService / PipelineSchedulerService                                |
+-----------------------------------------------------------------------------------+
                               |                     |
                               v                     v
+--------------------------------------+   +----------------------------------------+
|    Execution Engine (Layer 3)        |   |    Persistence Layer (Layer 4)         |
|  - ExecutionEngine / StageRunner     |   |  - DatabaseManager (HikariCP)          |
|  - ProcessExecutor / LogCollector    |   |  - Primary: MySQL 8.0 (Persistent)     |
|  - ParallelStageCoordinator          |   |  - Fallback/Dev: H2 (Explicit Warning) |
|  - Isolated Workspace Directories    |   |  - Version-Tracked SQL Migrations      |
+--------------------------------------+   +----------------------------------------+
```

---

## 3. High-Priority Requirement Inventory

| Req ID | Milestone | Title | Priority | Target Area |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-DB-01** | M1 | Explicit Persistence Mode & Fallback Disclosure | CRITICAL | `DatabaseManager`, `MainLayoutView`, `SettingsView` |
| **REQ-DB-02** | M1 | Atomic Transactions & Foreign Key Integrity | HIGH | `PipelineRepository`, `ExecutionRepository` |
| **REQ-DB-03** | M1 | Non-Blocking DB Access Off JavaFX Thread | HIGH | `ProjectsView`, `PipelinesView`, `ExecutionHistoryView` |
| **REQ-GIT-01** | M2 | Autonomous Git Clone & Revision Tracking | HIGH | `GitService`, `ExecutionEngine` |
| **REQ-GIT-02** | M2 | Secure Git Credential Handling & Scrubbing | CRITICAL | `GitService`, `ProcessExecutor` |
| **REQ-ENG-01** | M3 | DAG Dependency Graph & Failure Cascading | HIGH | `PipelineValidator`, `ExecutionEngine` |
| **REQ-ENG-02** | M3 | Deadlock-Free Streaming of stdout/stderr | HIGH | `ProcessExecutor`, `LogCollector` |
| **REQ-ENG-03** | M3 | Clean Cancellation & Process Tree Termination | HIGH | `ProcessExecutor`, `ExecutionEngine` |
| **REQ-BLD-01** | M4 | Multi-Ecosystem Build & Test Support | HIGH | `ProjectImportService`, `ExecutionEngine` |
| **REQ-BLD-02** | M4 | Real Artifact Discovery & SHA-256 Deployment | HIGH | `DeploymentService`, `ExecutionEngine` |
| **REQ-PKG-01** | M5 | Self-Contained Independent Executable Packaging | CRITICAL | `pom.xml`, Maven Assembly / JPackage |
| **REQ-TST-01** | M6 | Comprehensive Test Suite & Automation | HIGH | Test infrastructure, JUnit 5 |
| **REQ-UI-01** | M7 | Responsive Modern JavaFX UI Without Mock Data | HIGH | JavaFX UI views, real-time charts |
| **REQ-SEC-01** | M8 | Subprocess Hardening & Secret Masking | CRITICAL | `ProcessExecutor`, `WebhookServerService` |
| **REQ-REL-01** | M9 | Interrupted Execution Reconciler & Recovery | HIGH | `ExecutionRepository`, `MainApp` |
| **REQ-E2E-01** | M10 & P9 | Live Launch, Visual Audit & Full User Journey | CRITICAL | Packaged App, GUI verification |
