# DevOpsHub Documentation Index

Welcome to the comprehensive technical documentation repository for **DevOpsHub** — Intelligent CI/CD Management Platform.

---

## Architecture & System Overview
- [System Architecture](ARCHITECTURE.md) — System boundaries, layered desktop architecture, and Mermaid system diagrams.
- [System Overview](SYSTEM_OVERVIEW.md) — High-level product summary and platform capabilities.
- [Core Modules](MODULES.md) — Deep dive into the 5 core modules (Project, Pipeline, Execution, Logs, Analytics).
- [Data Flow](DATA_FLOW.md) — End-to-end data pipelines from process execution to persistence and UI rendering.
- [Workflows](WORKFLOWS.md) — Complete user workflows with Mermaid sequence diagrams.

## Design, UI & JavaFX
- [UI/UX Design Guide](UI_UX_GUIDE.md) — Visual design tokens, Slate/Sky palette, and responsive component styling.
- [JavaFX Implementation Guide](JAVAFX_GUIDE.md) — JavaFX layout containers, thread safety, and SvgIcon vector rendering.
- [Java Code Structure](JAVA_CODE_STRUCTURE.md) — Package hierarchy, OOP design principles, and CSE1007 syllabus mapping.

## Database & Persistence
- [Database Design & Schema](DATABASE_DESIGN.md) — Normalized MySQL relational schema, ER diagrams, indexes, and constraints.
- [Database Setup & Migrations](DATABASE_SETUP.md) — MySQL container setup, automated migrations, and seed scripts.

## Engine & Concurrency
- [Pipeline Execution Engine](PIPELINE_ENGINE.md) — Stage execution lifecycle, DAG dependency validation, and state machine.
- [Concurrency & Threading Model](CONCURRENCY_MODEL.md) — `ExecutorService`, Fork/Join parallel execution, and synchronization.
- [Rule-Based Failure Analysis](FAILURE_ANALYSIS.md) — Explainable failure diagnosis engine and pattern matching.
- [Rule Engine Technical Guide](RULE_ENGINE.md) — Adding custom rules, evidence extraction, and recommendation generation.

## Engineering & Operations
- [Requirements Traceability Matrix](REQUIREMENTS.md) — Complete requirements traceability with unique IDs.
- [Feature Matrix & Status](FEATURE_MATRIX.md) — Implementation verification matrix.
- [Architectural Decision Records](DECISIONS.md) — Design trade-offs and rationale.
- [Implementation Plan](IMPLEMENTATION_PLAN.md) — Step-by-step phased execution history.
- [Security & Sandboxing](SECURITY.md) — Subprocess execution security, command validation, and credential protection.
- [Configuration Reference](CONFIGURATION.md) — Schema for `.env` and runtime properties.
- [Installation Guide](INSTALLATION.md) — Prerequisites, build commands, and verified verification procedures.
- [Local Development Guide](LOCAL_DEVELOPMENT.md) — VS Code and IDE setup, running tests, and live debugging.
- [Testing & Quality Assurance](TESTING.md) — JUnit 5 unit, integration, and scenario tests.
- [Troubleshooting & FAQs](TROUBLESHOOTING.md) — Resolving common environment and runtime errors.
- [Deployment & Packaging](DEPLOYMENT.md) — Standalone executable JAR building and distribution.
- [User Guide](USER_GUIDE.md) — End-user instructions for managing projects and running pipelines.
- [Developer Guide](DEVELOPER_GUIDE.md) — Guidelines for extending and maintaining DevOpsHub.
- [Acceptance Criteria Verification](ACCEPTANCE_CRITERIA.md) — Verification audit against all project acceptance criteria.
- [Changelog](CHANGELOG.md) — Version release notes and delivery history.
