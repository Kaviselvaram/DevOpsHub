# DevOpsHub Changelog

All notable changes to the DevOpsHub MVP project are documented in this file.

## [1.0.0] - 2026-10-12

### Initial MVP Delivery
#### Added
- **JavaFX Desktop Frontend:** Native JavaFX 21 desktop client with high-density Slate/Sky UI theme, navigation sidebar, dynamic header, and responsive layout containers.
- **Strict SVG-Only Icon System:** Vector `SVGPath` geometry renderer (`SvgIcon.java`) and 20+ vector SVG assets in `src/main/resources/icons/svg/`. Zero emojis or raster images.
- **Relational Persistence Layer:** Direct JDBC data access with HikariCP connection pooling and parameterized SQL queries across 5 repositories.
- **Database Migrations:** Schema creation script (`V1__initial_schema.sql`) and demonstration seed data (`V2__sample_seed_data.sql`) matching course requirements.
- **Execution Engine:** Real subprocess execution engine (`ProcessBuilder`) supporting sequential and parallel stage execution (`CompletableFuture`), configurable timeouts, bounded log buffering, and thread-safe cancellation.
- **Failure Intelligence Rule Engine:** Rule-based diagnostic engine (`FailureRuleEngine`) with 6 concrete diagnostic rules, including specific detection for test assertion mismatches (HTTP 401 expected, HTTP 500 received at `LoginService`).
- **Dashboard & Analytics:** Real-time metrics computed directly from database records, displaying total projects, active pipelines, success rate, recent runs, and failure category breakdowns.
- **Comprehensive Documentation Suite:** 30 project-specific markdown documents in `docs/` and root `README.md`.
- **Automated Verification:** 33 unit and integration tests covering services, repositories, validators, the execution engine, and diagnostic rules.
